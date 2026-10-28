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
class J10TvScreenshotTest {
    private val summary = TvSummary("Mi TV Box (fixture)", "MIBOX4", "192.168.1.42", "SERIAL", 28, "9")
    private fun shoot(name: String, step: Step, s: UiState) {
        RuntimeEnvironment.setQualifiers("w360dp-h720dp-xxhdpi")
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) { TidyTheme { Screen(step, s, vm = null, onShareLogs = {}) } }
        }
    }
    private fun base(j: J10Ui) = UiState(tv = summary, j10 = j)

    @Test fun j10Ready() = shoot("current_j10_ready", Step.J10TV, base(J10Ui.Ready("v1.2.0", 6_555_001, "One build for this 32-bit ARM TV", null)))
    @Test fun j10Weak() = shoot("current_j10_weakcheck", Step.J10TV, base(J10Ui.WeakCheck))
    @Test fun j10Installed() = shoot("current_j10_installed", Step.J10TV, base(J10Ui.Installed("com.skylake.skytv.jgorunner", "v1.2.0", true, "SHA-256 checked on the TV", true, false)))

    /** Same screen in a tall window: the whole body is laid out in the scroll column. */
    @Test fun j10ReadyTall() {
        RuntimeEnvironment.setQualifiers("w360dp-h1100dp-xxhdpi")
        captureRoboImage("src/test/screenshots/current_j10_ready_tall.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) { TidyTheme {
                Screen(Step.J10TV, base(J10Ui.Ready("v1.2.0", 6_555_001, "One build for this 32-bit ARM TV", null)), vm = null, onShareLogs = {})
            } }
        }
    }
}
