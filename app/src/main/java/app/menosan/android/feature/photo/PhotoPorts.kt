package app.menosan.android.feature.photo

import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.data.remote.MenosanApi
import app.menosan.android.data.remote.dto.PhotoAnalysisDto
import app.menosan.android.data.repo.TaxonomyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

// Seams of the photo flow, so PhotoLogViewModel runs in JVM unit tests with fakes. Bound in PhotoModule below.

/** Where the photo comes from. Only [Camera] files are ours, and they're deleted right after reading (SFR8.5). */
sealed interface PhotoInput {
    /** A file in our cache that `TakePicture` wrote. */
    data class Camera(val file: File) : PhotoInput

    /** A `content://` URI from the photo picker (read-only, never copied). */
    data class Gallery(val uri: String) : PhotoInput
}

/** The photo couldn't be decoded (not an image, an unsupported format, or too little memory). */
class PhotoUnreadableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Even the smallest allowed JPEG is over the 2 MB budget (in practice this doesn't happen). */
class PhotoTooLargeException : Exception("The photo is too large.")

/** Turns a photo into an upright JPEG of at most 1280 px and 2 MB (plan §10). Bytes stay in memory only. */
fun interface PhotoProcessor {
    /** @throws PhotoUnreadableException */
    suspend fun prepare(input: PhotoInput): ByteArray
}

/** A camera target: the file on disk and the `content://` URI (as a string) that `TakePicture` writes to. */
data class CaptureTarget(val file: File, val uri: String)

/** Temporary photo files in the app cache. */
interface PhotoFiles {
    /** A new empty file for the camera, shared through our FileProvider. */
    fun newCaptureTarget(): CaptureTarget

    fun delete(file: File)

    /** Deletes every leftover photo file except [keep] (e.g. after the app was killed mid-analysis). */
    fun deleteAll(keep: File? = null)
}

/** `POST /v1/photo-analysis` (contract §7.1). */
fun interface PhotoAnalysisClient {
    suspend fun analyze(jpeg: ByteArray): ApiResult<PhotoAnalysisDto>
}

/** Online hint for the UI (photo logging is online only). Requests can still fail, so errors are handled too. */
interface NetworkStatus {
    fun isOnline(): Boolean
    val online: Flow<Boolean>
}

fun interface TaxonomySource {
    suspend fun taxonomy(): Taxonomy
}

class ApiPhotoAnalysisClient @Inject constructor(private val api: MenosanApi) : PhotoAnalysisClient {
    override suspend fun analyze(jpeg: ByteArray): ApiResult<PhotoAnalysisDto> =
        safeApiCall { api.analyzePhoto(MenosanApi.imagePart(jpeg)) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoModule {
    @Binds
    abstract fun processor(impl: AndroidPhotoProcessor): PhotoProcessor

    @Binds
    abstract fun files(impl: CachePhotoFiles): PhotoFiles

    @Binds
    abstract fun analysisClient(impl: ApiPhotoAnalysisClient): PhotoAnalysisClient

    companion object {
        @Provides
        fun networkStatus(observer: ConnectivityObserver): NetworkStatus = object : NetworkStatus {
            override fun isOnline(): Boolean = observer.isOnline()
            override val online: Flow<Boolean> = observer.online
        }

        @Provides
        fun taxonomySource(repository: TaxonomyRepository): TaxonomySource = TaxonomySource { repository.taxonomy() }
    }
}
