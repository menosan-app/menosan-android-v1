package app.menosan.android.feature.photo

import kotlin.math.max
import kotlin.math.roundToInt

/** Width and height in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    val longSide: Int get() = max(width, height)
}

/**
 * How to turn the stored pixels upright, from the EXIF orientation tag (1–8): rotate clockwise by [rotationDegrees],
 * then mirror horizontally when [flipHorizontal] is set.
 */
data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    /** A 90° or 270° turn swaps width and height. */
    val swapsSides: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270
    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal
}

/** A JPEG ready to upload, with the long side and quality it was encoded at. */
class EncodedJpeg(val bytes: ByteArray, val longSide: Int, val quality: Int)

/**
 * The pure part of photo preparation (plan §10 Photo logging): downsampling, scaling, EXIF orientation, and the
 * size budget for `POST /v1/photo-analysis`. No Android types, so it's unit-tested on the JVM.
 */
object PhotoImageMath {
    /** The long side of the uploaded photo (plan §10). */
    const val MAX_LONG_SIDE = 1280

    /** JPEG quality of the first attempt (plan §10: q ≈ 80). */
    const val START_QUALITY = 80
    const val MIN_QUALITY = 50
    const val QUALITY_STEP = 10

    /** The server takes at most 2 MiB (contract §7.1). Stay a little under it. */
    const val MAX_BYTES = 2 * 1024 * 1024 - 64 * 1024

    /** When even [MIN_QUALITY] is too big, the long side shrinks by this factor and quality starts over. */
    const val SHRINK_FACTOR = 0.75

    /** Below this long side the photo is no use for analysis, so give up. */
    const val MIN_LONG_SIDE = 320

    /**
     * The largest power-of-two `inSampleSize` that still decodes at least [maxLongSide] pixels on the long side, so
     * the decoder saves memory without dropping below the upload size.
     */
    fun sampleSize(source: PixelSize, maxLongSide: Int = MAX_LONG_SIDE): Int {
        require(source.width > 0 && source.height > 0) { "Empty image" }
        var sample = 1
        while (source.longSide / (sample * 2) >= maxLongSide) sample *= 2
        return sample
    }

    /** [source] scaled down (never up) so its long side is at most [maxLongSide], keeping the aspect ratio. */
    fun scaledSize(source: PixelSize, maxLongSide: Int = MAX_LONG_SIDE): PixelSize {
        if (source.longSide <= maxLongSide) return source
        val scale = maxLongSide.toDouble() / source.longSide
        return PixelSize(
            width = (source.width * scale).roundToInt().coerceAtLeast(1),
            height = (source.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    /** EXIF orientation values 1–8 (`ExifInterface.ORIENTATION_*`). Unknown or missing values mean "upright". */
    fun exifTransform(orientation: Int): ExifTransform = when (orientation) {
        2 -> ExifTransform(0, flipHorizontal = true) // FLIP_HORIZONTAL
        3 -> ExifTransform(180, flipHorizontal = false) // ROTATE_180
        4 -> ExifTransform(180, flipHorizontal = true) // FLIP_VERTICAL
        5 -> ExifTransform(90, flipHorizontal = true) // TRANSPOSE
        6 -> ExifTransform(90, flipHorizontal = false) // ROTATE_90
        7 -> ExifTransform(270, flipHorizontal = true) // TRANSVERSE
        8 -> ExifTransform(270, flipHorizontal = false) // ROTATE_270
        else -> ExifTransform(0, flipHorizontal = false) // NORMAL, UNDEFINED
    }

    /** The upright size after [transform]. */
    fun uprightSize(stored: PixelSize, transform: ExifTransform): PixelSize =
        if (transform.swapsSides) PixelSize(stored.height, stored.width) else stored

    /**
     * Encodes with falling quality ([START_QUALITY] down to [MIN_QUALITY]), then a smaller long side, until the JPEG
     * fits in [maxBytes]. [encode] gets the target long side and quality. The first round always runs at
     * [startLongSide], even for a small photo. Returns null when nothing fits before the long side would drop below
     * [MIN_LONG_SIDE] (in practice never: a 1280 px JPEG at q80 is a few hundred KB).
     */
    fun encodeWithinBudget(
        startLongSide: Int,
        maxBytes: Int = MAX_BYTES,
        encode: (longSide: Int, quality: Int) -> ByteArray,
    ): EncodedJpeg? {
        var longSide = startLongSide
        while (true) {
            var quality = START_QUALITY
            while (quality >= MIN_QUALITY) {
                val bytes = encode(longSide, quality)
                if (bytes.size <= maxBytes) return EncodedJpeg(bytes, longSide, quality)
                quality -= QUALITY_STEP
            }
            val next = (longSide * SHRINK_FACTOR).roundToInt()
            if (next < MIN_LONG_SIDE) return null
            longSide = next
        }
    }
}
