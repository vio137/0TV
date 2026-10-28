package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLookTest {
    private val dir = "/sdcard/Android/data/com.gathin.tvhome/files"
    private val glass = """{"dock_apps": ["com.sample.app"], "wallpaper": true, "dock_glass": true}"""

    private fun config(tv: FakeTv) = Json.parseToJsonElement(String(tv.pushed["$dir/config.json"]!!)).jsonObject

    @Test fun aLookKeepsTheOwnersDockAndRestartsHome() = runTest {
        val tv = FakeTv.googleTv()
        val look = LauncherLook(tv) { 42 }
        look.writeDock(listOf("com.netflix.ninja", "com.google.android.youtube.tv"))
        assertTrue(look.apply(LookFiles("glass", glass, wallpaper = byteArrayOf(1, 2, 3))))

        val c = config(tv)
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv"), c["dock_apps"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", c["dock_glass"]!!.jsonPrimitive.content)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(tv.pushed["$dir/wallpaper.jpg"]))
        assertEquals("42", tv.files["$dir/theme_revision"])
        assertEquals(1, tv.lookRestarts)
        assertTrue("every file went through the launcher's provider", "config.json" in tv.lookWrites && "wallpaper.jpg" in tv.lookWrites)
        assertTrue("nothing left in the staging area", tv.pushed.keys.none { it.startsWith("/data/local/tmp/tidytv-look-") })
    }

    @Test fun aCollectionReplacesTheSingleWallpaper() = runTest {
        val tv = FakeTv.googleTv()
        val look = LauncherLook(tv) { 1 }
        look.apply(LookFiles("aurora", """{"wallpaper": true}""", wallpaper = byteArrayOf(9)))
        look.apply(LookFiles("scenes", """{"wallpaper": true, "wallpaper_rotation_minutes": 60}""",
            wallpapers = listOf("01-dusk.jpg" to byteArrayOf(1), "02-coast.jpg" to byteArrayOf(2)), captions = "Dusk\nCoast\n".toByteArray()))
        assertNull(tv.pushed["$dir/wallpaper.jpg"])
        assertTrue(tv.pushed.containsKey("$dir/wallpapers/01-dusk.jpg") && tv.pushed.containsKey("$dir/wallpapers/02-coast.jpg"))
        assertTrue(tv.pushed.containsKey("$dir/wallpapers/captions.txt"))

        look.apply(LookFiles("default", "{}"))
        assertTrue("switching looks clears the old collection", tv.pushed.keys.none { it.startsWith("$dir/wallpapers/") || it == "$dir/wallpaper.jpg" })
    }

    @Test fun noDockOnTheTvMeansNoSampleDockEither() {
        val out = Json.parseToJsonElement(LauncherLook.merged(glass, emptyList())).jsonObject
        assertFalse("the theme's sample apps never become the owner's dock", out.containsKey("dock_apps"))
        assertEquals("""{"dock_apps":["com.a.b"],"tile_menu":true,"app_library":true,"launcher_settings_button":true}""", LauncherLook.merged("not json", listOf("com.a.b")).trim())
        // A theme that sets one of them itself keeps its value.
        val off = Json.parseToJsonElement(LauncherLook.merged("""{"tile_menu": false}""", emptyList())).jsonObject
        assertEquals("false", off["tile_menu"]!!.jsonPrimitive.content)
        assertEquals("true", off["app_library"]!!.jsonPrimitive.content)
    }

    @Test fun fileNamesCantEscapeTheFolder() {
        for (bad in listOf("../x.jpg", "a b.jpg", "x;rm.jpg", "wallpapers/../x", ".hidden"))
            assertTrue(bad, runCatching { Commands.lookPut("com.gathin.tvhome", bad) }.isFailure)
        assertTrue(runCatching { LookFiles("x", "{}", wallpapers = listOf("\$(reboot).jpg" to byteArrayOf())) }.isFailure)
        assertEquals("/data/local/tmp/tidytv-look-wallpapers-a.jpg", Commands.lookStage("wallpapers/a.jpg"))
    }

    @Test fun oldAndroidsUnsupportedWriteFallsBackToCopy() {
        val put = Commands.lookPut("com.gathin.tvhome", "config.json")
        assertTrue(put.contains("*'Unsupported operation'*) false"))
        assertTrue(put.contains("|| cp /data/local/tmp/tidytv-look-config.json $dir/config.json"))
    }
}

class PremiumHomeTest {
    @org.junit.Test fun `premium home has its own package and a full activity name`() {
        val p = dev.tidytv.core.lists.HomeApp.PREMIUM
        org.junit.Assert.assertEquals("app.tvtidy.home", p.pkg)
        org.junit.Assert.assertEquals("app.tvtidy.home/com.gathin.tvhome.MainActivity", p.component)
        org.junit.Assert.assertNotEquals(dev.tidytv.core.lists.HomeApp.STYLED.pkg, p.pkg)
        org.junit.Assert.assertTrue(dev.tidytv.core.lists.Launchers.isHomeApp(p.pkg))
        org.junit.Assert.assertTrue(dev.tidytv.core.lists.Protected.isProtected(p.pkg))
    }
}
