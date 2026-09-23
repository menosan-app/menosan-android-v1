package app.menosan.android.feature.photo

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.network.ApiResult
import app.menosan.android.data.repo.EntryChangeException
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.feature.logging.EntryField
import app.menosan.android.feature.logging.EntryFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** The steps of photo logging. */
sealed interface PhotoStep {
    /** Take a photo or choose one (mockup `Upload Photo`). */
    data object Pick : PhotoStep

    /** Brand loading screen (mockup `Loading Screen (After Scanning)`). [wakingUp] after a long wait. */
    data class Analyzing(val wakingUp: Boolean = false) : PhotoStep

    data class Failed(val error: PhotoError) : PhotoStep

    data class Review(
        val taxonomy: Taxonomy,
        val review: PhotoReview,
        val showErrors: Boolean = false,
        val saving: Boolean = false,
        @param:StringRes val saveError: Int? = null,
    ) : PhotoStep {
        val canSave: Boolean get() = review.confirmed && !saving
    }
}

data class PhotoUiState(
    val online: Boolean = true,
    val step: PhotoStep = PhotoStep.Pick,
    /** True while the last photo is still in memory, so "Try again" can resend it without a new photo. */
    val canRetrySamePhoto: Boolean = false,
)

sealed interface PhotoEvent {
    data class Saved(@param:StringRes val message: Int) : PhotoEvent
}

/**
 * Photo logging (plan §10, AN-2; SFR7–9, NFR10): take or pick a photo → shrink it on the device → `POST
 * /v1/photo-analysis` → review the prefilled, AI-marked form → confirm → save through [EntryRepository] (the same
 * offline outbox as manual entries, with `source = PHOTO`).
 *
 * Privacy (SFR8.5): camera files are deleted as soon as they are read, and leftovers are swept when this screen
 * starts and closes. The prepared JPEG lives only in memory, and only until the analysis succeeds or the user moves on.
 */
@HiltViewModel
class PhotoLogViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val processor: PhotoProcessor,
    private val files: PhotoFiles,
    private val analysisClient: PhotoAnalysisClient,
    private val repository: EntryRepository,
    private val taxonomySource: TaxonomySource,
    private val network: NetworkStatus,
) : ViewModel() {
    private val _state = MutableStateFlow(PhotoUiState(online = network.isOnline()))
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    private val events = Channel<PhotoEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PhotoEvent> = events.receiveAsFlow()

    private var job: Job? = null

    /** The prepared photo, kept in memory only for "Try again" after a retryable error. */
    private var lastJpeg: ByteArray? = null
        set(value) {
            field = value
            _state.update { it.copy(canRetrySamePhoto = value != null) }
        }

    /** The camera file we're waiting for. Saved so it survives the app being killed while the camera is open. */
    private var pendingCapture: File?
        get() = savedStateHandle.get<String>(KEY_CAPTURE)?.let(::File)
        set(value) {
            savedStateHandle[KEY_CAPTURE] = value?.path
        }

    init {
        // Leftovers from an earlier run (e.g. the app was killed mid-analysis). Keep a capture we're still waiting for.
        files.deleteAll(keep = pendingCapture)
        viewModelScope.launch { network.online.collect { online -> _state.update { it.copy(online = online) } } }
    }

    /** A new camera target, or null (and an error) when no file could be created. The UI passes the URI to `TakePicture`. */
    fun startCapture(): String? {
        pendingCapture?.let(files::delete)
        return try {
            val target = files.newCaptureTarget()
            pendingCapture = target.file
            target.uri
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            pendingCapture = null
            fail(PhotoError(PhotoErrorKind.NO_CAMERA))
            null
        }
    }

    /** The result of `TakePicture`. When the user backs out of the camera, the empty file is deleted. */
    fun onCaptureResult(success: Boolean) {
        val file = pendingCapture ?: return
        pendingCapture = null
        if (success) analyze(PhotoInput.Camera(file)) else files.delete(file)
    }

    /** There's no camera app to handle `TakePicture`. */
    fun onCameraUnavailable() {
        pendingCapture?.let(files::delete)
        pendingCapture = null
        fail(PhotoError(PhotoErrorKind.NO_CAMERA))
    }

    /** The result of the photo picker; null when the user closed it without choosing. */
    fun onGalleryResult(uri: String?) {
        if (uri != null) analyze(PhotoInput.Gallery(uri))
    }

    /** Sends the same photo again after a retryable error. */
    fun retry() {
        val jpeg = lastJpeg ?: return backToPick()
        runAnalysis { upload(jpeg) }
    }

    /** Stops an analysis in progress, or leaves an error or the review, and goes back to taking or choosing a photo. */
    fun backToPick() {
        job?.cancel()
        job = null
        lastJpeg = null
        _state.update { it.copy(step = PhotoStep.Pick) }
    }

    fun onFormChange(form: EntryFormState) = updateReview { it.copy(review = it.review.edit(form), saveError = null) }

    fun onConfirmField(field: EntryField) = updateReview { it.copy(review = it.review.confirmField(field)) }

    fun onConfirmedChange(confirmed: Boolean) =
        updateReview { it.copy(review = it.review.withConfirmed(confirmed), saveError = null) }

    fun save() {
        val step = _state.value.step as? PhotoStep.Review ?: return
        if (step.saving) return
        if (!step.review.confirmed) {
            updateReview { it.copy(saveError = R.string.photo_review_confirm_required) }
            return
        }
        val draft = step.review.toDraft(step.taxonomy)
        if (draft == null) {
            updateReview { it.copy(showErrors = true, saveError = R.string.photo_review_error_invalid) }
            return
        }
        updateReview { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                repository.create(draft)
                events.send(PhotoEvent.Saved(if (_state.value.online) R.string.photo_saved else R.string.photo_saved_offline))
            } catch (e: CancellationException) {
                throw e
            } catch (_: EntryChangeException.Invalid) {
                updateReview { it.copy(showErrors = true, saveError = R.string.photo_review_error_invalid) }
            } catch (_: Exception) {
                updateReview { it.copy(saveError = R.string.photo_review_error_save) }
            } finally {
                updateReview { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        lastJpeg = null
        files.deleteAll(keep = null)
    }

    private fun analyze(input: PhotoInput) = runAnalysis {
        lastJpeg = null
        val jpeg = try {
            processor.prepare(input)
        } catch (_: PhotoUnreadableException) {
            fail(PhotoError(PhotoErrorKind.UNREADABLE))
            return@runAnalysis
        } catch (_: PhotoTooLargeException) {
            fail(PhotoError(PhotoErrorKind.IMAGE_TOO_LARGE))
            return@runAnalysis
        } finally {
            // SFR8.5: our camera file is gone as soon as it's read, whatever happens next.
            if (input is PhotoInput.Camera) files.delete(input.file)
        }
        lastJpeg = jpeg
        upload(jpeg)
    }

    /** Shows the loading screen, and the "waking up" hint if the server takes long (Render Free cold start). */
    private fun runAnalysis(block: suspend () -> Unit) {
        job?.cancel()
        _state.update { it.copy(step = PhotoStep.Analyzing()) }
        job = viewModelScope.launch {
            val hint = launch {
                delay(WAKING_UP_HINT_MS)
                _state.update { s -> if (s.step is PhotoStep.Analyzing) s.copy(step = PhotoStep.Analyzing(wakingUp = true)) else s }
            }
            try {
                block()
            } finally {
                hint.cancel()
            }
        }
    }

    private suspend fun upload(jpeg: ByteArray) {
        when (val result = analysisClient.analyze(jpeg)) {
            is ApiResult.Success -> {
                val taxonomy = taxonomySource.taxonomy()
                val analysis = result.value
                lastJpeg = null // Analysis done: drop the photo (SFR8.5).
                _state.update {
                    it.copy(step = PhotoStep.Review(taxonomy, PhotoReview.from(analysis.suggestion, analysis.warning, taxonomy)))
                }
            }
            is ApiResult.Failure -> {
                val error = PhotoError.from(result.error)
                if (!error.kind.canRetrySame) lastJpeg = null
                fail(error)
            }
        }
    }

    private fun fail(error: PhotoError) = _state.update { it.copy(step = PhotoStep.Failed(error)) }

    private inline fun updateReview(crossinline change: (PhotoStep.Review) -> PhotoStep.Review) =
        _state.update { s -> (s.step as? PhotoStep.Review)?.let { s.copy(step = change(it)) } ?: s }

    companion object {
        private const val KEY_CAPTURE = "pendingCapture"

        /** When to add "the server may be waking up" to the loading screen. Awake, an analysis takes 2–6 s. */
        const val WAKING_UP_HINT_MS = 10_000L
    }
}
