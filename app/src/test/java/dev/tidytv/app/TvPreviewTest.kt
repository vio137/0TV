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
