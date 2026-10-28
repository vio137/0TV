package dev.tidytv.core.ops

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureTest {
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1)
    private class Shell(val bytes: ByteArray?, val size: String, val fail: Boolean = false) : TvShell {
        val commands = mutableListOf<String>()
        override suspend fun run(command: String): String {
            commands += command
            if (fail && command.startsWith("timeout")) error("capture failed")
            return if (command.startsWith("stat")) size else ""
        }
        override suspend fun pull(remotePath: String) = bytes
        override suspend fun push(bytes: ByteArray, remotePath: String) = Unit
    }
    @Test fun validPngAndCleanup() = runTest {
        val s = Shell(png, "9")
        assertArrayEquals(png, ScreenCapture.read(s))
        assertTrue(s.commands.first().startsWith("timeout 8 screencap -p /data/local/tmp/otv-screen-"))
        assertTrue(s.commands.last().startsWith("rm -f /data/local/tmp/otv-screen-"))
    }
    @Test fun missingOversizedTruncatedAndInvalidAreNotImages() = runTest {
        for (s in listOf(Shell(null,"9"), Shell(png,"999999999"), Shell(png,"10"), Shell(ByteArray(9),"9"), Shell(png,"error"))) {
            assertNull(ScreenCapture.read(s))
            assertTrue(s.commands.last().startsWith("rm -f "))
        }
    }
    @Test fun captureFailureStillRemovesFile() = runTest {
        val s = Shell(null,"",true)
        try { ScreenCapture.read(s); fail() } catch (_: IllegalStateException) {}
        assertTrue(s.commands.last().startsWith("rm -f "))
    }
}
