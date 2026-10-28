package dev.tidytv.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Our own launcher look must only use keys the upstream launcher documents (unknown keys are ignored silently, so a typo would do nothing). */
class MidnightThemeTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "upstream/cleanyourtv/themes/README.md").exists() }

    @Test fun keysAreDocumentedUpstream() {
        val cfg = Json.parseToJsonElement(File(root, "app/themes-own/midnight/config.json").readText()).jsonObject
        val readme = File(root, "upstream/cleanyourtv/themes/README.md").readText()
        val missing = cfg.keys.filter { !readme.contains("`$it`") }
        assertTrue("undocumented keys: $missing", missing.isEmpty())
    }

    @Test fun wallpaperAndPreviewShip() {
        assertTrue(File(root, "app/themes-own/midnight/wallpaper.jpg").length() in 5_000..400_000)
        assertTrue(File(root, "app/themes-own/midnight/screenshot.jpg").length() > 1_000)
    }

    @Test fun doesNotPinAnyApps() {
        val cfg = Json.parseToJsonElement(File(root, "app/themes-own/midnight/config.json").readText()).jsonObject
        assertTrue("dock_apps" !in cfg)
    }
}
