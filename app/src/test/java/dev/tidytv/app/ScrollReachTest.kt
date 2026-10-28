package dev.tidytv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TidyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The last row of a long screen can be scrolled into view and ends above the pinned action bar, at normal and large font. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class ScrollReachTest {
    @get:Rule val rule = createComposeRule()

    private fun check(fontScale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TidyTheme {
                    ScreenFrame(bottom = { Box(Modifier.height(56.dp).testTag("bar")) { Text("Action") } }) {
                        repeat(40) { i -> Text("Row $i", Modifier.testTag("row$i")) }
                        Text("Last row", Modifier.testTag("last"))
                    }
                }
            }
        }
        rule.onNodeWithTag("last").performScrollTo().assertIsDisplayed()
        val last = rule.onNodeWithTag("last").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        assertTrue("last row (${last.bottom}) must end above the action bar (${bar.top})", last.bottom <= bar.top + 0.5f)
    }

    @Test fun lastRowReachableAtNormalFont() = check(1f)
    @Test fun lastRowReachableAtLargeFont() = check(2f)
}
