package dev.tidytv.app

import dev.tidytv.app.data.LogTrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTrimTest {
    private fun log(actions: Int, trace: Int) = buildString {
        appendLine("TidyTV 0.9.0 on Android 14 (SDK 34)")
        appendLine("TV: Zzyx Qwerty 9000, Android 14 (SDK 34), list: none")
        appendLine("step: HOME_SCREEN, connect: Connected")
        appendLine("problem: Connected, but slow")
        appendLine("===== actions")
        repeat(actions) { appendLine("$it DISABLE com.app.p$it DONE") }
        appendLine("===== trace")
        repeat(trace) { appendLine("10-26 12:00:00.000  line $it of the trace with some padding text") }
    }

    @Test fun aShortLogIsLeftAlone() {
        val l = log(2, 3)
        assertEquals(l, LogTrim.fit(l, 5_000))
    }

    @Test fun theSummaryAndTheLastActionsSurviveAndTheTraceKeepsItsEnd() {
        val out = LogTrim.fit(log(300, 400), 5_000)
        assertTrue(out.length <= 5_000)
        assertTrue("summary first", out.startsWith("TidyTV 0.9.0"))
        assertTrue(out.contains("list: none") && out.contains("problem: Connected, but slow"))
        assertTrue("the last action is there, the first is not", out.contains("299 DISABLE") && !out.contains("\n0 DISABLE"))
        assertTrue("the trace ends at its last line", out.trimEnd().endsWith("line 399 of the trace with some padding text"))
    }
}
