package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinisherAndRedactorTest {
    @Test fun freedMemoryFromBeforeAndAfter() = runTest {
        val tv = FakeTv.googleTv()
        val before = TvReader.snapshot(tv).memAvailableKb
        tv.disabled += listOf("com.tcl.tv.plus", "com.tcl.guard")
        val report = Finisher.measure(tv, before)
        assertEquals(35, report.freedMb) // 2 × 18,000 kB
    }

    @Test fun freedIsNeverNegativeOrMadeUp() {
        assertEquals(0, FinishReport(900_000, 800_000).freedMb)
        assertNull(FinishReport(null, 800_000).freedMb)
    }

    @Test fun debuggingOffWhenPortIsClosed() = runTest {
        assertTrue(Finisher.debuggingOff { false })
        assertFalse(Finisher.debuggingOff { true })
    }

    @Test fun redactsSerialWifiMacEmailAndIp() {
        val log = """
            [ro.serialno]: [TCLG05TEST01]
            [ro.product.model]: [Smart TV Pro]
            [wifi.interface]: [wlan0]
            [persist.sys.device_name]: [Joe's TV]
            connect 192.168.1.42:5555
            mac 3c:5a:b4:01:02:03 user joe@example.com SSID: "HomeNet"
            serial again TCLG05TEST01
        """.trimIndent()
        val out = LogRedactor.redact(log, listOf("TCLG05TEST01"))
        assertFalse(out, out.contains("TCLG05TEST01"))
        assertFalse(out.contains("Joe's TV"))
        assertFalse(out.contains("3c:5a"))
        assertFalse(out.contains("joe@example.com"))
        assertFalse(out.contains("HomeNet"))
        assertFalse(out.contains("192.168.1"))
        assertTrue(out.contains("x.x.x.42:5555"))
        assertTrue(out.contains("[ro.product.model]: [Smart TV Pro]"))
    }
}
