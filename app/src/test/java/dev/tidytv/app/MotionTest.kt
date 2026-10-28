package dev.tidytv.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.TidyTheme
import dev.tidytv.app.ui.screens.HubScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Steps the animation clock by hand and looks at real frames: entrance, press, tick draw-in, and the reduce-motion path. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h700dp-xxhdpi")
class MotionTest {
    @get:Rule val rule = createComposeRule()

    private fun frame() = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun px(a: Bitmap) = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
    private fun same(a: Bitmap, b: Bitmap) = a.width == b.width && a.height == b.height && px(a).contentEquals(px(b))
    private fun diff(a: Bitmap, b: Bitmap): Double { val x = px(a); val y = px(b); return x.indices.count { x[it] != y[it] }.toDouble() / x.size }
    private fun filmstrip(name: String, labels: List<String>, frames: List<Bitmap>) {
        val w = frames[0].width; val h = frames[0].height; val pad = 24
        val out = Bitmap.createBitmap(frames.size * (w + pad) + pad, h + 120, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(Color.rgb(30, 30, 36))
        val p = Paint().apply { color = Color.WHITE; textSize = 44f; isAntiAlias = true }
        frames.forEachIndexed { i, f -> c.drawBitmap(f, pad + i * (w + pad).toFloat(), 90f, null); c.drawText(labels[i], pad + i * (w + pad) + 8f, 60f, p) }
        File("src/test/screenshots").mkdirs()
        File("src/test/screenshots/$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun hub() = rule.setContent { TidyTheme { HubScreen(null, 0, false, {}, {}, {}, {}, {}, {}) } }

    @Test fun hubEntranceAndPress() {
        rule.mainClock.autoAdvance = false
        hub()
        val frames = ArrayList<Bitmap>(); var t = 0L
        for (ms in listOf(0L, 120L, 260L, 700L)) { rule.mainClock.advanceTimeBy(ms - t); t = ms; frames += frame() }
        filmstrip("motion_hub_entrance", listOf("t=0", "120 ms", "260 ms", "700 ms"), frames)
        assertFalse("entrance is moving at 120 ms", same(frames[0], frames[1]))
        assertFalse("still moving at 260 ms", same(frames[1], frames[2]))
        assertFalse("settling by 700 ms", same(frames[2], frames[3]))
        rule.mainClock.advanceTimeBy(600)
        assertTrue("settled: frames 600 ms apart are identical", same(frames[3], frame()))
    }

    @Test fun buttonPressScalesAndReleases() {
        rule.mainClock.autoAdvance = false
        rule.setContent { TidyTheme { Box(Modifier.fillMaxWidth().padding(24.dp)) { BigButton("Continue", {}, Modifier.testTag("b")) } } }
        rule.mainClock.advanceTimeBy(500)
        val rest = frame()
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(60); val early = frame()
        rule.mainClock.advanceTimeBy(300); val held = frame()
        rule.onNodeWithTag("b").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(600); val back = frame()
        filmstrip("motion_button_press", listOf("rest", "60 ms down", "held", "released"), listOf(rest, early, held, back))
        assertFalse("pressed state differs from rest", same(rest, held))
        assertFalse("pressing is animated, not a jump", same(early, held))
        assertTrue("released frame is closer to rest than the held frame is", diff(rest, back) < diff(rest, held))
        fun width(b: Bitmap): Int {
            val y = b.height / 2
            val xs = (0 until b.width).filter { x -> val c = b.getPixel(x, y); Color.blue(c) - Color.red(c) > 35 }
            return xs.last() - xs.first()
        }
        assertTrue("held button is narrower than rest", width(held) < width(rest))
        assertTrue("released button returns to its original width", kotlin.math.abs(width(rest) - width(back)) <= 2)
    }

    @Test fun tickDrawsIn() {
        rule.mainClock.autoAdvance = false
        rule.setContent { TidyTheme { Box(Modifier.padding(24.dp)) { Check(Tidy.Green, 96.dp) } } }
        val f = ArrayList<Bitmap>(); var t = 0L
        for (ms in listOf(0L, 250L, 400L, 900L)) { rule.mainClock.advanceTimeBy(ms - t); t = ms; f += frame() }
        filmstrip("motion_tick", listOf("0", "250 ms", "400 ms", "900 ms"), f)
        assertFalse(same(f[0], f[1])); assertFalse(same(f[1], f[2])); assertFalse(same(f[2], f[3]))
    }

    @Test fun reduceMotionShowsFinalFrameImmediately() {
        android.provider.Settings.Global.putFloat(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f,
        )
        rule.mainClock.autoAdvance = false
        hub()
        val first = frame()
        rule.mainClock.advanceTimeBy(1500)
        assertTrue("with animations off the first frame is already the settled frame", same(first, frame()))
    }
}
