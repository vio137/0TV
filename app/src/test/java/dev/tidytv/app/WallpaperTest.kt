package dev.tidytv.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import dev.tidytv.app.data.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Phone photos become small 16:9 TV wallpapers before anything is sent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WallpaperTest {
    private fun photo(w: Int, h: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Noise, so the JPEG is about as hard to squeeze as a real photo.
        val rnd = java.util.Random(1)
        val px = IntArray(w * h) { Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    @Test fun aBigLandscapePhotoBecomesFullHdAndSmall() {
        val src = photo(4000, 3000)
        val out = Wallpaper.fromPhoto(src.size.toLong()) { src.inputStream() }!!
        assertEquals(1920, out.width)
        assertEquals(1080, out.height)
        assertTrue("${out.jpeg.size} bytes", out.jpeg.size <= Wallpaper.MAX_BYTES)
        assertTrue(out.jpeg.size < src.size)
        val back = BitmapFactory.decodeByteArray(out.jpeg, 0, out.jpeg.size)
        assertEquals(1920, back.width)
    }

    @Test fun aPortraitPhotoIsCroppedToTheScreen() {
        val src = photo(1500, 2000)
        val out = Wallpaper.fromPhoto(src.size.toLong()) { src.inputStream() }!!
        assertEquals(1500, out.width)
        assertEquals("1500 × 9 / 16, rounded down", 843, out.height)
    }

    @Test fun aSmallPhotoIsNeverScaledUp() {
        val src = photo(800, 600)
        val out = Wallpaper.fromPhoto(src.size.toLong()) { src.inputStream() }!!
        assertEquals(800, out.width)
        assertEquals(450, out.height)
    }

    @Test fun somethingThatIsntAPhotoIsRefused() {
        assertNull(Wallpaper.fromPhoto(4) { byteArrayOf(1, 2, 3, 4).inputStream() })
    }
}
