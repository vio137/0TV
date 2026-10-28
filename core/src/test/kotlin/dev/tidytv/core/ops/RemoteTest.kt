package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTest {
    @Test fun keysGoThroughOneCommandEachWithoutASession() = runTest {
        val tv = FakeTv.googleTv()
        val r = Remote(tv)
        r.press(RemoteKey.UP); r.press(RemoteKey.OK); r.press(RemoteKey.VOLUME_UP)
        assertEquals(listOf("KEYCODE_DPAD_UP", "KEYCODE_DPAD_CENTER", "KEYCODE_VOLUME_UP"), tv.keys)
        assertTrue(tv.sessionCommands.isEmpty())
    }

    @Test fun keysUseTheKeptOpenSessionWhenThereIsOne() = runTest {
        val tv = FakeTv.googleTv().apply { sessionSupported = true }
        val r = Remote(tv)
        r.press(RemoteKey.HOME); r.press(RemoteKey.BACK)
        assertEquals(listOf("input keyevent KEYCODE_HOME", "input keyevent KEYCODE_BACK"), tv.sessionCommands)
        assertEquals(listOf("KEYCODE_HOME", "KEYCODE_BACK"), tv.keys)
    }

    @Test fun typingSendsEscapedText() = runTest {
        val tv = FakeTv.googleTv()
        val r = Remote(tv)
        assertTrue(r.type("It's 100% fun"))
        assertEquals(listOf("It's 100% fun"), tv.typed)
        assertFalse("nothing typable", r.type("😀"))
    }

    @Test fun appsComeFromTheTvHomeScreenAndOpen() = runTest {
        val tv = FakeTv.googleTv()
        val r = Remote(tv)
        val apps = r.apps()
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.android.tv.settings"), apps.map { it.pkg })
        r.launch(apps.first())
        assertEquals(listOf("com.netflix.ninja/.MainActivity"), tv.launched)
    }

    @Test fun noTvAppsMeansAnEmptyList() = runTest {
        val tv = FakeTv.googleTv().apply { tvApps = emptyList() }
        assertTrue(Remote(tv).apps().isEmpty())
    }

    @Test fun parsesBriefOutputFromAndroid9To14() {
        val android9 = "com.netflix.ninja/.MainActivity\ncom.tcl.tvweishi/com.tcl.tvweishi.MainActivity\n"
        val android14 = """
            2 activities found:
              Activity #0:
                priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false
                com.netflix.ninja/.MainActivity
              Activity #1:
                priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false
                com.netflix.ninja/.Other
        """.trimIndent()
        assertEquals(2, RemoteParse.apps(android9).size)
        assertEquals("one entry per app", listOf(TvApp("com.netflix.ninja", "com.netflix.ninja/.MainActivity")), RemoteParse.apps(android14))
        assertTrue(RemoteParse.apps("No activities found").isEmpty())
    }

    @Test fun aDroppedSessionReportsAndTheNextPressStartsAgain() = runTest {
        val tv = FakeTv.googleTv().apply { sessionSupported = true }
        val r = Remote(tv)
        r.press(RemoteKey.UP)
        tv.dropOnPrefix = "input keyevent KEYCODE_DPAD_DOWN"
        assertTrue(runCatching { r.press(RemoteKey.DOWN) }.exceptionOrNull() is TvConnectionLost)
        r.press(RemoteKey.LEFT)
        assertEquals(listOf("KEYCODE_DPAD_UP", "KEYCODE_DPAD_LEFT"), tv.keys)
    }
}
