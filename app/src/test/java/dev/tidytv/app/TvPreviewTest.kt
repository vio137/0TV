package dev.tidytv.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.tidytv.app.ui.Screen
import dev.tidytv.app.ui.TidyTheme
import dev.tidytv.core.flow.Step
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TvPreviewTest {
    private fun fixture() = Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888).apply {
        val c=Canvas(this); c.drawColor(Color.rgb(19,30,56))
        val p=Paint().apply { color=Color.WHITE; textSize=64f; isAntiAlias=true }
        c.drawText("TV screen fixture",90f,170f,p)
        p.color=Color.rgb(52,120,200);c.drawRoundRect(90f,300f,880f,850f,30f,30f,p)
        p.color=Color.rgb(50,160,140);c.drawRoundRect(990f,300f,1800f,850f,30f,30f,p)
    }
    @Test fun decodeDownsamplesAndRejectsInvalid() {
        val bytes=ByteArrayOutputStream().also { fixture().compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        val image=TvPreview.decode(bytes)!!
        assertTrue(image.width<=640);assertTrue(image.height<=640)
        assertNull(TvPreview.decode(byteArrayOf(1,2,3)))
        val black = Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        val blackBytes=ByteArrayOutputStream().also { black.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        assertNull(TvPreview.decode(blackBytes))
    }
    @Test fun cachePersistsAndSeparatesDevices() {
        val dir = java.nio.file.Files.createTempDirectory("preview-cache").toFile()
        val a = TvSummary("Same name", "MIBOX4", "192.168.0.2", "serial-A", 28, "9")
        val b = a.copy(serial = "serial-B")
        val cache = TvPreviewCache(dir)
        assertNull(cache.read(a))
        cache.save(a, fixture())
        assertNotNull(TvPreviewCache(dir).read(a.copy(title = "Renamed", host = "192.168.0.99")))
        assertNull(cache.read(b))
        val file = dir.listFiles()!!.single()
        assertFalse(file.name.contains("serial-A"))
        file.writeText("corrupt")
        assertNull(cache.read(a))
        cache.save(a, fixture())
        assertNotNull(cache.read(a))
    }
    @Test fun unknownSerialUsesDeviceEndpointNotDisplayName() {
        val dir = java.nio.file.Files.createTempDirectory("preview-cache").toFile()
        val a = TvSummary("Same name", "MIBOX4", "192.168.0.2", "unknown", 28, "9")
        val cache = TvPreviewCache(dir)
        cache.save(a, fixture())
        assertNull(cache.read(a.copy(host = "192.168.0.3")))
        assertNotNull(cache.read(a.copy(title = "Renamed")))
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cachedDeviceSkipsAdbAfterRestartAndAnotherDeviceCaptures() = kotlinx.coroutines.test.runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        try {
            val dir = java.nio.file.Files.createTempDirectory("preview-vm").toFile()
            val store = dev.tidytv.app.data.Store(dir)
            val a = TvSummary("A", "MIBOX4", "host-a", "serial-a", 28, "9")
            val b = a.copy(title = "B", serial = "serial-b", host = "host-b")
            val bytes = ByteArrayOutputStream().also { fixture().compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
            var captures = 0
            val shell = object : dev.tidytv.core.ops.TvShell {
                override suspend fun run(command: String): String {
                    if (command.contains("screencap")) captures++
                    return if (command.startsWith("stat ")) bytes.size.toString() else ""
                }
                override suspend fun pull(remotePath: String) = bytes
                override suspend fun push(bytes: ByteArray, remotePath: String) {}
            }
            fun vm(selected: TvSummary): WizardViewModel {
                val v = WizardViewModel(store, dev.tidytv.app.data.Trace(store.logsDir), { dev.tidytv.app.net.DemoBackend() }, null,
                    { dev.tidytv.core.lists.ListCatalog(emptyMap()) }, { null }, imageWork = dispatcher)
                v.javaClass.getDeclaredField("shell").apply { isAccessible = true }.set(v, shell)
                @Suppress("UNCHECKED_CAST")
                val state = v.javaClass.getDeclaredField("_ui").apply { isAccessible = true }.get(v) as kotlinx.coroutines.flow.MutableStateFlow<UiState>
                state.value = state.value.copy(tv = selected)
                return v
            }
            val first = vm(a); first.refreshTvPreview(); testScheduler.advanceUntilIdle()
            assertNotNull(first.ui.value.tvPreview.image); assertEquals(1,captures)
            first.refreshTvPreview(); testScheduler.advanceUntilIdle(); assertEquals(1,captures)
            val restarted = vm(a); restarted.refreshTvPreview(); testScheduler.advanceUntilIdle()
            assertNotNull(restarted.ui.value.tvPreview.image); assertEquals(1,captures)
            val other = vm(b); other.refreshTvPreview(); testScheduler.advanceUntilIdle()
            assertNotNull(other.ui.value.tvPreview.image); assertEquals(2,captures)
            // A tap on the picture forces a fresh capture and updates the cache, even with one already shown.
            other.refreshTvPreview(force = true); testScheduler.advanceUntilIdle()
            assertEquals(3,captures)
            val again = vm(b); again.refreshTvPreview(); testScheduler.advanceUntilIdle()
            assertEquals(3,captures)
        } finally { kotlinx.coroutines.Dispatchers.resetMain() }
    }
    private fun shoot(name:String,preview:TvPreview,width:Int=360) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h800dp-xxhdpi")
        val s=UiState(hubFacts=HubFacts(3379,1126),tv=TvSummary("maatv","MIBOX4","fixture","fixture",28,"9"),tvPreview=preview)
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) { TidyTheme { Screen(Step.HUB,s,null,{}) } }
        }
    }
    @Test fun capturedCard()=shoot("preview_captured_fixture",TvPreview(fixture(),1793203200000))
    @Test fun unavailableCard()=shoot("preview_unavailable",TvPreview())
    @Test fun loadingCard()=shoot("preview_loading",TvPreview(loading=true))
    @Test fun narrowCard()=shoot("preview_narrow_fixture",TvPreview(fixture(),1793203200000),320)
}
