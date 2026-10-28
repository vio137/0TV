package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParseAndLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun getpropWithCrlf() {
        val p = Parse.props("[ro.product.model]: [MIBOX4]\r\n[ro.build.version.sdk]: [28]\r\n[weird line]\n")
        assertEquals("MIBOX4", p["ro.product.model"])
        assertEquals(2, p.size)
    }

    @Test fun packagesRefuseErrorText() {
        assertEquals(setOf("com.a.b", "com.c.d"), Parse.packages("package:com.a.b\r\npackage:com.c.d\n\n"))
        assertEquals(setOf("com.a.b"), Parse.packages("package:/system/app/A/A.apk=com.a.b\n"))
        try { Parse.packages("package:com.a.b\nError: something\n"); fail() } catch (e: UnexpectedOutput) { }
    }

    @Test fun resolveHomeVariants() {
        assertEquals("com.x.home/.Main", Parse.component("priority=0 preferredOrder=0\ncom.x.home/.Main\n"))
        assertEquals("android/com.android.internal.app.ResolverActivity",
            Parse.component("priority=0\nandroid/com.android.internal.app.ResolverActivity\n"))
        assertNull(Parse.component("No activity found\n"))
        assertNull(Parse.component(""))
    }

    @Test fun flagsOutput() {
        val f = Parse.flags("@com.a.b\n    pkgFlags=[ SYSTEM PERSISTENT ]\n@com.c.d\n@com.e.f\n  pkgFlags=[ SYSTEM ]\n")
        assertTrue(f["com.a.b"]!!.contains("PERSISTENT"))
        assertEquals("", f["com.c.d"])
        assertTrue(f["com.e.f"]!!.contains("SYSTEM"))
    }

    @Test fun meminfo() {
        assertEquals(812345L, Parse.memAvailableKb("MemTotal: 1 kB\nMemAvailable:     812345 kB\n"))
        assertNull(Parse.memAvailableKb("nothing"))
    }

    @Test fun mirrorNeedsEndMarker() {
        assertEquals(setOf("com.a.b"), Parse.mirror("com.a.b\nbad line;rm\nEND\n"))
        assertNull("a cut-off read is not trusted", Parse.mirror("com.a.b\n"))
    }

    @Test fun ownerPackagesFromImeAndTvInputs() {
        val owners = Parse.ownerPackages("com.google.android.leanback.ime/.LeanbackImeService:com.x.y/.Ime", "null", "  inputId: com.tcl.tvinput/.Hdmi/HW5")
        assertEquals(setOf("com.google.android.leanback.ime", "com.x.y", "com.tcl.tvinput"), owners)
    }

    @Test fun commandsRejectInjection() {
        for (bad in listOf("com.a;reboot", "com.a b", "\$(id)")) {
            try { Commands.disable(bad); fail(bad) } catch (e: IllegalArgumentException) { }
        }
        try { Commands.setHome("com.a/.B;reboot"); fail() } catch (e: IllegalArgumentException) { }
        try { Settings.put("global", "x", "1; reboot"); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun disabledByUsFold() {
        val r = listOf(
            ActionRecord(1, Op.DISABLE, "a.b.c", Phase.INTENT),
            ActionRecord(2, Op.DISABLE, "a.b.c", Phase.DONE),
            ActionRecord(3, Op.DISABLE, "d.e.f", Phase.INTENT), // crash before the result: counts
            ActionRecord(4, Op.DISABLE, "g.h.i", Phase.INTENT),
            ActionRecord(5, Op.DISABLE, "g.h.i", Phase.FAILED),
            ActionRecord(6, Op.DISABLE, "j.k.l", Phase.DONE),
            ActionRecord(7, Op.ENABLE, "j.k.l", Phase.DONE),
        )
        assertEquals(setOf("a.b.c", "d.e.f"), r.disabledByUs())
    }

    @Test fun fileLogSurvivesTornLastLine() {
        val f = tmp.newFile("actions.jsonl")
        val log = FileActionLog(f)
        log.append(ActionRecord(1, Op.DISABLE, "a.b.c", Phase.INTENT))
        log.append(ActionRecord(2, Op.DISABLE, "a.b.c", Phase.DONE))
        f.appendText("{\"ts\":3,\"op\":\"DISA") // power cut mid-write
        val reread = FileActionLog(f).records()
        assertEquals(2, reread.size)
        assertEquals(setOf("a.b.c"), reread.disabledByUs())
        FileActionLog(f).append(ActionRecord(4, Op.ENABLE, "a.b.c", Phase.DONE))
        assertTrue(FileActionLog(f).records().disabledByUs().isEmpty())
    }

    @Test fun fileLogIsAppendOnly() {
        val f = tmp.newFile("a.jsonl")
        val log = FileActionLog(f)
        repeat(3) { log.append(ActionRecord(it.toLong(), Op.DISABLE, "a.b.c$it", Phase.DONE)) }
        val size = f.length()
        log.append(ActionRecord(9, Op.DISABLE, "x.y.z", Phase.DONE))
        assertTrue(f.length() > size)
        assertEquals(4, f.readLines().size)
    }

    @Test fun snapshotAndBackupRoundTrip() = runTest {
        val tv = FakeTv.googleTv()
        val snap = TvReader.snapshot(tv)
        assertEquals("TCLG05TEST01", snap.info.serial)
        assertEquals(30, snap.info.sdk)
        assertTrue("com.tcl.tvinput" in snap.liveProtected)
        assertTrue("com.google.android.leanback.ime" in snap.liveProtected)
        val b = TvReader.backup(tv, snap, now = 42)
        assertEquals(b, Backup.fromJson(b.toJson()))
        assertTrue(b.settings["global"]!!.contains("device_provisioned=1"))
    }

    @Test fun brokenGetpropStopsBeforeBackup() = runTest {
        val tv = FakeTv.googleTv().apply { getpropBroken = true }
        try { TvReader.snapshot(tv); fail() } catch (e: UnexpectedOutput) { }
    }
}
