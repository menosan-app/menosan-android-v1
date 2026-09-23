package app.menosan.android.feature.photo

import androidx.lifecycle.SavedStateHandle
import app.menosan.android.R
import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.data.remote.dto.PhotoAnalysisDto
import app.menosan.android.data.remote.dto.PhotoSuggestionDto
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.feature.logging.EntryField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val taxonomy: Taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
    private val success = ApiResult.Success(
        PhotoAnalysisDto(
            PhotoSuggestionDto("Water PET bottle", WasteCategory.RECYCLABLE, "REC_PET_BOTTLES", 2, 0.9),
            warning = "This is an AI suggestion and it can be wrong.",
        ),
        200,
    )

    private val processor = FakeProcessor()
    private val files = FakeFiles()
    private val analysis = FakeAnalysis()
    private val repository = FakeEntryRepository()
    private val network = FakeNetwork()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        PhotoLogViewModel(saved, processor, files, analysis, repository, { taxonomy }, network)

    private fun http(status: Int, code: ApiErrorCode) =
        ApiResult.Failure(ApiError.Http(status, code, null, JsonObject(emptyMap()), null))

    private val PhotoLogViewModel.step get() = state.value.step

    @Test
    fun `a gallery photo becomes a prefilled review with every field marked`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()

        vm.onGalleryResult("content://media/picker/1")
        assertEquals(PhotoStep.Analyzing(), vm.step)
        advanceUntilIdle()

        assertEquals(listOf<PhotoInput>(PhotoInput.Gallery("content://media/picker/1")), processor.inputs)
        assertArrayEquals(jpeg, analysis.sent.single())
        val review = (vm.step as PhotoStep.Review).review
        assertEquals("Water PET bottle", review.form.name)
        assertEquals("REC_PET_BOTTLES", review.form.subcategory)
        assertEquals("2", review.form.quantity)
        assertEquals(EntryField.entries.toSet(), review.aiSuggested)
        assertEquals("This is an AI suggestion and it can be wrong.", review.warning)
        // The photo is dropped once the analysis succeeded.
        assertFalse(vm.state.value.canRetrySamePhoto)
    }

    @Test
    fun `closing the picker without a photo changes nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onGalleryResult(null)
        advanceUntilIdle()
        assertEquals(PhotoStep.Pick, vm.step)
        assertTrue(processor.inputs.isEmpty())
    }

    @Test
    fun `a camera photo is deleted right after it is read`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()

        val uri = vm.startCapture()
        assertEquals("content://app.menosan.android.photos/photos/capture-1.jpg", uri)
        vm.onCaptureResult(true)
        advanceUntilIdle()

        val file = files.created.single()
        assertEquals(listOf<PhotoInput>(PhotoInput.Camera(file)), processor.inputs)
        assertTrue(file in files.deleted)
        assertTrue(vm.step is PhotoStep.Review)
    }

    @Test
    fun `backing out of the camera deletes the empty file`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startCapture()
        vm.onCaptureResult(false)
        advanceUntilIdle()

        assertTrue(files.created.single() in files.deleted)
        assertEquals(PhotoStep.Pick, vm.step)
        assertTrue(processor.inputs.isEmpty())
    }

    @Test
    fun `a camera file is deleted even when it can't be read`() = runTest(dispatcher) {
        processor.failure = PhotoUnreadableException("broken")
        val vm = viewModel()
        vm.startCapture()
        vm.onCaptureResult(true)
        advanceUntilIdle()

        assertTrue(files.created.single() in files.deleted)
        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.UNREADABLE)), vm.step)
        assertTrue(analysis.sent.isEmpty())
    }

    @Test
    fun `no camera app shows a friendly error and cleans up`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startCapture()
        vm.onCameraUnavailable()
        assertTrue(files.created.single() in files.deleted)
        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.NO_CAMERA)), vm.step)
    }

    @Test
    fun `the pending camera file survives the app being killed`() = runTest(dispatcher) {
        analysis.results += success
        val saved = SavedStateHandle()
        viewModel(saved).startCapture()
        val file = files.created.single()

        // The process dies while the camera is open; a new ViewModel gets the same saved state.
        val restored = viewModel(saved)
        assertEquals(file, files.keptOnSweep.last())
        restored.onCaptureResult(true)
        advanceUntilIdle()

        assertEquals(listOf<PhotoInput>(PhotoInput.Camera(file)), processor.inputs)
        assertTrue(restored.step is PhotoStep.Review)
    }

    @Test
    fun `leftover photo files are swept when the screen starts`() {
        viewModel()
        assertEquals(1, files.sweeps)
        assertNull(files.keptOnSweep.single())
    }

    @Test
    fun `not waste can't be retried with the same photo`() = runTest(dispatcher) {
        analysis.results += http(422, ApiErrorCode.NOT_WASTE)
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()

        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.NOT_WASTE)), vm.step)
        assertFalse(vm.state.value.canRetrySamePhoto)
    }

    @Test
    fun `a failed analysis can be retried with the same photo`() = runTest(dispatcher) {
        analysis.results += http(422, ApiErrorCode.ANALYSIS_FAILED)
        analysis.results += success
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()
        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.ANALYSIS_FAILED)), vm.step)
        assertTrue(vm.state.value.canRetrySamePhoto)

        vm.retry()
        advanceUntilIdle()

        assertEquals(1, processor.inputs.size) // Not decoded again.
        assertEquals(2, analysis.sent.size)
        assertArrayEquals(analysis.sent[0], analysis.sent[1])
        assertTrue(vm.step is PhotoStep.Review)
        assertFalse(vm.state.value.canRetrySamePhoto)
    }

    @Test
    fun `network errors keep the photo for a retry`() = runTest(dispatcher) {
        analysis.results += ApiResult.Failure(ApiError.Network(IOException("no route")))
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()
        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.NETWORK)), vm.step)
        assertTrue(vm.state.value.canRetrySamePhoto)

        vm.backToPick()
        assertEquals(PhotoStep.Pick, vm.step)
        assertFalse(vm.state.value.canRetrySamePhoto)
    }

    @Test
    fun `the rate limit error keeps resetsAt`() = runTest(dispatcher) {
        analysis.results += ApiResult.Failure(
            ApiError.Http(
                429, ApiErrorCode.RATE_LIMITED, null,
                JsonObject(mapOf("resetsAt" to kotlinx.serialization.json.JsonPrimitive("2026-09-30T16:00:00Z"))), null,
            ),
        )
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()
        assertEquals(PhotoStep.Failed(PhotoError(PhotoErrorKind.RATE_LIMITED, Instant.parse("2026-09-30T16:00:00Z"))), vm.step)
    }

    @Test
    fun `a slow server shows the waking up hint`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<PhotoAnalysisDto>>()
        analysis.gate = gate
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        runCurrent()
        assertEquals(PhotoStep.Analyzing(wakingUp = false), vm.step)

        advanceTimeBy(PhotoLogViewModel.WAKING_UP_HINT_MS + 1)
        assertEquals(PhotoStep.Analyzing(wakingUp = true), vm.step)

        gate.complete(success)
        advanceUntilIdle()
        assertTrue(vm.step is PhotoStep.Review)
    }

    @Test
    fun `cancel stops the analysis`() = runTest(dispatcher) {
        analysis.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        runCurrent()
        vm.backToPick()
        advanceUntilIdle()
        assertEquals(PhotoStep.Pick, vm.step)
        assertFalse(vm.state.value.canRetrySamePhoto)
    }

    @Test
    fun `saving needs the confirmation box`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        val step = vm.step as PhotoStep.Review
        assertFalse(step.canSave)
        assertEquals(R.string.photo_review_confirm_required, step.saveError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a confirmed review is saved as a photo entry with the user's edits`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()

        val form = (vm.step as PhotoStep.Review).review.form
        vm.onFormChange(form.withQuantity("3"))
        vm.onConfirmField(EntryField.NAME)
        assertEquals(setOf(EntryField.CATEGORY, EntryField.SUBCATEGORY), (vm.step as PhotoStep.Review).review.aiSuggested)

        vm.onConfirmedChange(true)
        assertTrue((vm.step as PhotoStep.Review).canSave)
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf(EntryDraft("Water PET bottle", "REC_PET_BOTTLES", 3, EntrySource.PHOTO)), repository.created)
        assertEquals(PhotoEvent.Saved(R.string.photo_saved), vm.eventFlow.first())
    }

    @Test
    fun `saving offline says it will sync later`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()
        network.state.value = false
        advanceUntilIdle()

        vm.onConfirmedChange(true)
        vm.save()
        advanceUntilIdle()
        assertEquals(PhotoEvent.Saved(R.string.photo_saved_offline), vm.eventFlow.first())
    }

    @Test
    fun `an invalid form shows the field errors instead of saving`() = runTest(dispatcher) {
        analysis.results += success
        val vm = viewModel()
        vm.onGalleryResult("content://x")
        advanceUntilIdle()
        vm.onFormChange((vm.step as PhotoStep.Review).review.form.withName(""))
        vm.onConfirmedChange(true)
        vm.save()
        advanceUntilIdle()

        val step = vm.step as PhotoStep.Review
        assertTrue(step.showErrors)
        assertEquals(R.string.photo_review_error_invalid, step.saveError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `the offline state follows the network`() = runTest(dispatcher) {
        network.state.value = false
        val vm = viewModel()
        assertFalse(vm.state.value.online)
        network.state.value = true
        advanceUntilIdle()
        assertTrue(vm.state.value.online)
    }

    // Fakes

    private inner class FakeProcessor : PhotoProcessor {
        val inputs = mutableListOf<PhotoInput>()
        var failure: Exception? = null

        override suspend fun prepare(input: PhotoInput): ByteArray {
            inputs += input
            failure?.let { throw it }
            return jpeg.copyOf()
        }
    }

    private class FakeFiles : PhotoFiles {
        val created = mutableListOf<File>()
        val deleted = mutableListOf<File>()
        val keptOnSweep = mutableListOf<File?>()
        var sweeps = 0

        override fun newCaptureTarget(): CaptureTarget {
            val file = File("cache/photos/capture-${created.size + 1}.jpg")
            created += file
            return CaptureTarget(file, "content://app.menosan.android.photos/photos/${file.name}")
        }

        override fun delete(file: File) {
            deleted += file
        }

        override fun deleteAll(keep: File?) {
            sweeps++
            keptOnSweep += keep
        }
    }

    private class FakeAnalysis : PhotoAnalysisClient {
        val results = ArrayDeque<ApiResult<PhotoAnalysisDto>>()
        val sent = mutableListOf<ByteArray>()
        var gate: CompletableDeferred<ApiResult<PhotoAnalysisDto>>? = null

        override suspend fun analyze(jpeg: ByteArray): ApiResult<PhotoAnalysisDto> {
            sent += jpeg
            gate?.let { return it.await() }
            return results.removeFirst()
        }
    }

    private class FakeNetwork : NetworkStatus {
        val state = MutableStateFlow(true)
        override fun isOnline(): Boolean = state.value
        override val online: Flow<Boolean> = state
    }

    private class FakeEntryRepository : EntryRepository {
        val created = mutableListOf<EntryDraft>()

        override fun observeWeek(weekStart: LocalDate): Flow<List<Entry>> = emptyFlow()
        override fun observeCurrentWeek(): Flow<List<Entry>> = emptyFlow()
        override suspend fun get(id: String): Entry? = null

        override suspend fun create(draft: EntryDraft): Entry {
            created += draft
            return Entry(
                id = "id-${created.size}", name = draft.name, category = WasteCategory.RECYCLABLE,
                subcategory = draft.subcategory, quantity = draft.quantity, source = draft.source,
                createdAt = Instant.parse("2026-09-28T02:00:00Z"), weekStart = LocalDate.parse("2026-09-27"),
                syncStatus = EntrySyncStatus.PENDING, editable = true,
            )
        }

        override suspend fun update(id: String, draft: EntryDraft): Entry = throw UnsupportedOperationException()
        override suspend fun delete(id: String) = throw UnsupportedOperationException()
        override fun observePendingCount(): Flow<Int> = flowOf(created.size)
        override suspend fun hasPendingChanges(): Boolean = created.isNotEmpty()
        override fun requestSync() = Unit
        override suspend fun refreshCurrentWeek() = Unit
    }
}
