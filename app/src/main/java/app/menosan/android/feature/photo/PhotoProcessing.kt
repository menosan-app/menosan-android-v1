package app.menosan.android.feature.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Decodes a camera or gallery photo with downsampling, turns it upright from its EXIF orientation, scales the long
 * side to ≤ 1280 px, and encodes a JPEG at q≈80 within the 2 MB budget ([PhotoImageMath]). Everything stays in memory:
 * nothing is written to disk and nothing is logged. The caller deletes camera files (SFR8.5).
 */
class AndroidPhotoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoProcessor {

    override suspend fun prepare(input: PhotoInput): ByteArray = withContext(Dispatchers.IO) {
        try {
            val stored = readBounds(input)
            val transform = PhotoImageMath.exifTransform(readOrientation(input))
            ensureActive()
            val decoded = open(input).use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = PhotoImageMath.sampleSize(stored) },
                )
            } ?: throw PhotoUnreadableException("The photo couldn't be decoded.")
            val upright = uprightAndScaled(decoded, transform)
            // uprightAndScaled returns the same instance when nothing changes; only recycle a replaced one.
            if (upright !== decoded) decoded.recycle()
            ensureActive()
            try {
                encode(upright)
            } finally {
                upright.recycle()
            }
        } catch (e: PhotoUnreadableException) {
            throw e
        } catch (e: PhotoTooLargeException) {
            throw e
        } catch (e: IOException) {
            throw PhotoUnreadableException("The photo couldn't be read.", e)
        } catch (e: SecurityException) {
            throw PhotoUnreadableException("No access to the photo.", e)
        } catch (e: IllegalArgumentException) {
            throw PhotoUnreadableException("The photo couldn't be read.", e)
        } catch (e: OutOfMemoryError) {
            throw PhotoUnreadableException("Not enough memory for the photo.", e)
        }
    }

    private fun open(input: PhotoInput): InputStream = when (input) {
        is PhotoInput.Camera -> FileInputStream(input.file)
        is PhotoInput.Gallery -> context.contentResolver.openInputStream(input.uri.toUri())
            ?: throw PhotoUnreadableException("The photo couldn't be opened.")
    }

    private fun readBounds(input: PhotoInput): PixelSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(input).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw PhotoUnreadableException("Not an image.")
        return PixelSize(options.outWidth, options.outHeight)
    }

    /** A missing or unreadable EXIF block just means "upright". */
    private fun readOrientation(input: PhotoInput): Int = try {
        open(input).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    } catch (_: RuntimeException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun uprightAndScaled(source: Bitmap, transform: ExifTransform): Bitmap {
        val target = PhotoImageMath.scaledSize(PixelSize(source.width, source.height))
        val scale = target.width.toFloat() / source.width
        if (transform.isIdentity && target.width == source.width) return source
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postRotate(transform.rotationDegrees.toFloat())
            if (transform.flipHorizontal) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun encode(upright: Bitmap): ByteArray {
        val size = PixelSize(upright.width, upright.height)
        val encoded = PhotoImageMath.encodeWithinBudget(size.longSide) { longSide, quality ->
            val bitmap = if (longSide >= size.longSide) {
                upright
            } else {
                val smaller = PhotoImageMath.scaledSize(size, longSide)
                upright.scale(smaller.width, smaller.height)
            }
            try {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            } finally {
                if (bitmap !== upright) bitmap.recycle()
            }
        }
        return encoded?.bytes ?: throw PhotoTooLargeException()
    }
}

/** Temporary camera files in `cache/photos/`, shared with the camera app through our FileProvider. */
class CachePhotoFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoFiles {
    private val dir: File get() = File(context.cacheDir, DIR)

    override fun newCaptureTarget(): CaptureTarget {
        val folder = dir.apply { mkdirs() }
        val file = File.createTempFile("capture-", ".jpg", folder)
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        return CaptureTarget(file, uri.toString())
    }

    override fun delete(file: File) {
        file.delete()
    }

    override fun deleteAll(keep: File?) {
        dir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    companion object {
        /** Must match `res/xml/photo_paths.xml`. */
        const val DIR = "photos"

        /** Must match the `<provider>` in the manifest (`${applicationId}.photos`). */
        fun authority(context: Context) = "${context.packageName}.photos"
    }
}
