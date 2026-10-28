package dev.tidytv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.tidytv.app.ui.Screen
import dev.tidytv.app.ui.TidyTheme
import dev.tidytv.core.flow.Step
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered with fixture state (no TV connected): these show layout and wording, not a real install. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CobaltScreenshotTest {
    private val summary = TvSummary("Mi TV Box (fixture)", "MIBOX4", "192.168.1.42", "SERIAL", 28, "9")
    private fun shoot(name: String, step: Step, s: UiState) {
        RuntimeEnvironment.setQualifiers("w360dp-h720dp-xxhdpi")
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) { TidyTheme { Screen(step, s, vm = null, onShareLogs = {}) } }
        }
    }
    private fun base(c: CobaltUi) = UiState(tv = summary, cobalt = c)

    @Test fun hub() = shoot("current_hub", Step.HUB, base(CobaltUi.Idle))
    @Test fun cobaltReady() = shoot("current_cobalt_ready", Step.COBALT, base(CobaltUi.Ready("v2.0.3", 153_048_724, "64-bit ARM build for this TV", "Stock YouTube stays untouched. The separate TizenTube Bridge project replaces YouTube by reusing its package name, which cannot coexist with the real app and would mean uninstalling stock YouTube. This app does not do that, so Cobalt installs next to it and you open it as its own app.", null)))
    @Test fun cobaltWeak() = shoot("current_cobalt_weakcheck", Step.COBALT, base(CobaltUi.WeakCheck))
    @Test fun cobaltInstalled() = shoot("current_cobalt_installed", Step.COBALT, base(CobaltUi.Installed("io.gh.reisxd.tizentube.cobalt", "v2.0.3", true, "SHA-256 checked on the TV", true, false)))

    /** Same screen in a tall window: the whole body, including the YouTube notice, is laid out in the scroll column. */
    @Test fun cobaltReadyTall() {
        RuntimeEnvironment.setQualifiers("w360dp-h1100dp-xxhdpi")
        captureRoboImage("src/test/screenshots/current_cobalt_ready_tall.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) { TidyTheme {
                Screen(Step.COBALT, base(CobaltUi.Ready("v2.0.3", 153_048_724, "64-bit ARM build for this TV", "Stock YouTube stays untouched. The separate TizenTube Bridge project replaces YouTube by reusing its package name, which cannot coexist with the real app and would mean uninstalling stock YouTube. This app does not do that, so Cobalt installs next to it and you open it as its own app.", null)), vm = null, onShareLogs = {})
            } }
        }
    }
}
