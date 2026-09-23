package app.menosan.android.feature.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoImageMathTest {

    @Test
    fun `sample size keeps at least 1280 px on the long side`() {
        assertEquals(1, PhotoImageMath.sampleSize(PixelSize(1280, 960)))
        assertEquals(1, PhotoImageMath.sampleSize(PixelSize(2559, 1920)))
        assertEquals(2, PhotoImageMath.sampleSize(PixelSize(2560, 1920)))
        // A 12 MP phone photo: 4000 / 2 = 2000 ≥ 1280, 4000 / 4 = 1000 < 1280.
        assertEquals(2, PhotoImageMath.sampleSize(PixelSize(4000, 3000)))
        // Portrait works the same way.
        assertEquals(4, PhotoImageMath.sampleSize(PixelSize(3000, 6000)))
        // Small photos are never sampled.
        assertEquals(1, PhotoImageMath.sampleSize(PixelSize(640, 480)))
    }

    @Test
    fun `scaled size caps the long side at 1280 and keeps the aspect ratio`() {
        assertEquals(PixelSize(1280, 960), PhotoImageMath.scaledSize(PixelSize(2000, 1500)))
        assertEquals(PixelSize(720, 1280), PhotoImageMath.scaledSize(PixelSize(1800, 3200)))
        assertEquals(PixelSize(1280, 1280), PhotoImageMath.scaledSize(PixelSize(4000, 4000)))
        // Never scaled up.
        assertEquals(PixelSize(800, 600), PhotoImageMath.scaledSize(PixelSize(800, 600)))
        // A very thin image keeps at least 1 px.
        assertEquals(PixelSize(1280, 1), PhotoImageMath.scaledSize(PixelSize(10_000, 2)))
    }

    @Test
    fun `EXIF orientation maps to rotation and mirroring`() {
        assertEquals(ExifTransform(0, false), PhotoImageMath.exifTransform(1))
        assertEquals(ExifTransform(0, true), PhotoImageMath.exifTransform(2))
        assertEquals(ExifTransform(180, false), PhotoImageMath.exifTransform(3))
        assertEquals(ExifTransform(180, true), PhotoImageMath.exifTransform(4))
        assertEquals(ExifTransform(90, true), PhotoImageMath.exifTransform(5))
        assertEquals(ExifTransform(90, false), PhotoImageMath.exifTransform(6))
        assertEquals(ExifTransform(270, true), PhotoImageMath.exifTransform(7))
        assertEquals(ExifTransform(270, false), PhotoImageMath.exifTransform(8))
        // Undefined (0) or garbage means upright.
        assertTrue(PhotoImageMath.exifTransform(0).isIdentity)
        assertTrue(PhotoImageMath.exifTransform(42).isIdentity)
    }

    @Test
    fun `quarter turns swap width and height`() {
        val stored = PixelSize(4000, 3000)
        assertEquals(PixelSize(3000, 4000), PhotoImageMath.uprightSize(stored, PhotoImageMath.exifTransform(6)))
        assertEquals(PixelSize(3000, 4000), PhotoImageMath.uprightSize(stored, PhotoImageMath.exifTransform(8)))
        assertEquals(stored, PhotoImageMath.uprightSize(stored, PhotoImageMath.exifTransform(3)))
        assertFalse(PhotoImageMath.exifTransform(2).swapsSides)
    }

    @Test
    fun `the first attempt is q80 at the full size`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val result = PhotoImageMath.encodeWithinBudget(1280) { side, q ->
            calls += side to q
            ByteArray(300_000)
        }!!
        assertEquals(listOf(1280 to 80), calls)
        assertEquals(1280, result.longSide)
        assertEquals(80, result.quality)
    }

    @Test
    fun `quality drops before the size does`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        // Too big until quality 60.
        val result = PhotoImageMath.encodeWithinBudget(1280, maxBytes = 1000) { side, q ->
            calls += side to q
            ByteArray(if (q <= 60) 900 else 5000)
        }!!
        assertEquals(listOf(1280 to 80, 1280 to 70, 1280 to 60), calls)
        assertEquals(60, result.quality)
        assertTrue(result.bytes.size <= 1000)
    }

    @Test
    fun `size shrinks when even the lowest quality is too big`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val result = PhotoImageMath.encodeWithinBudget(1280, maxBytes = 1000) { side, q ->
            calls += side to q
            ByteArray(if (side <= 960 && q == 80) 800 else 5000)
        }!!
        assertEquals(listOf(1280 to 80, 1280 to 70, 1280 to 60, 1280 to 50, 960 to 80), calls)
        assertEquals(960, result.longSide)
    }

    @Test
    fun `gives up below the minimum long side`() {
        val sides = mutableSetOf<Int>()
        val result = PhotoImageMath.encodeWithinBudget(1280, maxBytes = 10) { side, _ ->
            sides += side
            ByteArray(5000)
        }
        assertNull(result)
        assertTrue(sides.all { it >= PhotoImageMath.MIN_LONG_SIDE })
        assertEquals(listOf(1280, 960, 720, 540, 405), sides.sortedDescending())
    }

    @Test
    fun `a small photo is still encoded once at its own size`() {
        val result = PhotoImageMath.encodeWithinBudget(200) { _, _ -> ByteArray(10) }!!
        assertEquals(200, result.longSide)
    }

    @Test
    fun `the budget stays under the server limit of 2 MiB`() {
        assertTrue(PhotoImageMath.MAX_BYTES < 2 * 1024 * 1024)
        assertTrue(PhotoImageMath.MAX_BYTES > 1_900_000)
    }
}
