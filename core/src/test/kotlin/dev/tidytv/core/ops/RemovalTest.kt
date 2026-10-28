package dev.tidytv.core.ops

import dev.tidytv.core.lists.AllApps
import dev.tidytv.core.lists.ListCatalog
import dev.tidytv.core.lists.Tier
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Uninstall for the TV's user, bring it back, owner apps, the facts behind All apps, and Free up space. */
class RemovalTest {
    private val ads = "com.tcl.guard"
    private val promos = "com.tcl.messagebox"
    private fun debloater(tv: FakeTv, log: ActionLog) = Debloater(tv, log, clock = { 1L }, sleep = {})

    @Test fun removedAppsAreGoneAndRestoreBringsThemBack() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        val out = debloater(tv, log).run(listOf(ads, promos, "com.tcl.waterfall.overseas"), remove = setOf(ads, promos))
        assertTrue(out is DebloatOutcome.Finished)
        assertEquals(listOf(PkgResult.Removed(ads), PkgResult.Removed(promos), PkgResult.Disabled("com.tcl.waterfall.overseas")),
            out.results.sortedBy { it !is PkgResult.Removed })
        assertEquals(3, out.disabledCount)
        assertTrue(ads !in tv.installed && promos !in tv.installed)
        assertEquals(setOf(ads, promos), log.records().removedByUs())
        assertEquals("on the TV's own undo list", "$ads\n$promos\n", tv.files[Commands.REMOVED_FILE])

        val back = Restorer(tv, log) { 2L }.run()
        assertTrue(back.toString(), back is RestoreOutcome.Done)
        assertTrue(ads in tv.installed && promos in tv.installed && "com.tcl.waterfall.overseas" !in tv.disabled)
        assertTrue(log.records().removedByUs().isEmpty())
        assertTrue(Commands.REMOVED_FILE !in tv.files)
    }

    @Test fun theTvsOwnListIsEnoughToRestoreFromANewPhone() = runTest {
        val tv = FakeTv.googleTv()
        debloater(tv, MemoryActionLog()).run(listOf(ads), remove = setOf(ads))
        val fresh = MemoryActionLog() // phone reset: no log at all
        assertTrue(Restorer(tv, fresh) { 2L }.run() is RestoreOutcome.Done)
        assertTrue(ads in tv.installed)
    }

    @Test fun anAppTheOwnerInstalledIsUninstalledForGoodAndReportedNotRetried() = runTest {
        val tv = FakeTv.googleTv().apply { installed += "com.stremio.one"; userApps += "com.stremio.one" }
        val log = MemoryActionLog()
        val out = debloater(tv, log).run(listOf("com.stremio.one"), remove = setOf("com.stremio.one"))
        assertEquals(listOf(PkgResult.Removed("com.stremio.one", yours = true)), out.results)
        assertFalse("not on the TV's undo list", tv.files[Commands.REMOVED_FILE].orEmpty().contains("stremio"))
        val back = Restorer(tv, log) { 2L }.run() as RestoreOutcome.Done
        assertEquals(listOf("com.stremio.one"), back.gone)
        assertTrue(tv.commands.none { it.startsWith("cmd package install-existing com.stremio.one") })
    }

    @Test fun aRefusedUninstallStopsTheRun() = runTest {
        val tv = FakeTv.googleTv().apply { refuseRemove += ads }
        val log = MemoryActionLog()
        val out = debloater(tv, log).run(listOf(ads, promos), remove = setOf(ads, promos))
        assertTrue(out is DebloatOutcome.Stopped)
        assertTrue("nothing after the refusal", promos in tv.installed)
    }

    @Test fun protectedAndHomeAppsAreNeverRemoved() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv, MemoryActionLog()).run(listOf("com.google.android.gms", "com.android.vending", "com.google.android.apps.tv.launcherx"),
            remove = setOf("com.google.android.gms", "com.android.vending", "com.google.android.apps.tv.launcherx"))
        assertTrue(out.results.all { it is PkgResult.Skipped })
        assertTrue(tv.commands.none { it.startsWith("pm uninstall") })
    }

    @Test fun oneRemovedAppComesBackByItself() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        debloater(tv, log).run(listOf(ads, promos), remove = setOf(ads, promos))
        val r = Restorer(tv, log) { 2L }.enableOnly(setOf(ads))
        assertTrue(r is RestoreOutcome.Done)
        assertTrue(ads in tv.installed && promos !in tv.installed)
        assertEquals("$promos\n", tv.files[Commands.REMOVED_FILE])
    }

    @Test fun checkMyTvSeesARemovedAppThatCameBack() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        debloater(tv, log).run(listOf(ads), remove = setOf(ads))
        assertTrue(TvCheck(tv, log).run(listOf(ads), installedAtBackup = tv.installed + ads).allGood)
        tv.installed += ads // a firmware update put it back
        assertEquals(listOf(ads), TvCheck(tv, log).run(listOf(ads), installedAtBackup = tv.installed).backOn)
    }

    @Test fun factsAndFreeingCaches() = runTest {
        val tv = FakeTv.googleTv()
        val facts = TvFacts.read(tv)
        assertEquals(151, facts.of("com.google.android.apps.tv.launcherx").ramMb)
        assertTrue(facts.of("com.tcl.guard").startsAtBoot)
        assertEquals(68, facts.of("com.tcl.waterfall.overseas").cacheMb)
        assertEquals(269, facts.storage.cacheMb)
        val r = TvFacts.freeCaches(tv, wait = {})
        assertEquals(1, tv.cachesTrimmed)
        assertEquals(269, r.freedMb)
        assertTrue(r.finished)
    }

    @Test fun parsersReadRealOutput() {
        val meminfo = """
            Total PSS by process:
                197,124K: com.google.android.youtube.tv (pid 4507 / activities)
                151,520K: com.google.android.apps.tv.launcherx (pid 1622)
                 12,000K: com.google.android.gms:persistent (pid 900)
                 68,000K: com.google.android.gms (pid 901)
            Total PSS by OOM adjustment:
                 99,999K: Native
        """.trimIndent()
        val pss = Parse.pssMbByPackage(meminfo)
        assertEquals(192, pss["com.google.android.youtube.tv"])
        assertEquals("a package's processes add up", 78, pss["com.google.android.gms"])
        assertFalse(pss.containsKey("Native"))
        val disk = Parse.diskstats("Data-Free: 6028692K / 10243384K total = 58% free\nPackage Names: [\"com.a.b\",\"com.c.d\"]\nApp Sizes: [1048576,0]\nApp Data Sizes: [2097152,0]\nCache Sizes: [3145728,5]\n")
        assertEquals(6028692L, disk.freeKb)
        assertEquals(Parse.AppBytes(1048576, 2097152, 3145728), disk.apps["com.a.b"])
    }

    @Test fun allAppsPutsEveryAppInOneTierAndOrigin() {
        val ex = File("../upstream/cleanyourtv/examples")
        val catalog = ListCatalog(ex.listFiles()!!.filter { it.isDirectory }.associate { it.name to File(it, "debloat.txt").readText() })
        val list = catalog.byId("tcl-g07-google-tv")!!
        val tv = FakeTv.googleTv()
        val installed = tv.installed + listOf("com.stremio.one", "com.example.mystery")
        val rows = AllApps.build(list, catalog.lists, installed, emptySet(), yours = setOf("com.stremio.one"), live = setOf("com.tcl.tvinput"))
        val by = rows.associateBy { it.pkg }
        assertEquals(installed.size, rows.size)
        assertEquals(Tier.LOCKED, by["com.google.android.gms"]!!.tier)
        assertEquals(Tier.LOCKED, by["com.tcl.tvinput"]!!.tier)
        assertEquals(Tier.LOCKED, by["com.google.android.apps.tv.launcherx"]!!.tier)
        assertEquals(Tier.YOURS, by["com.stremio.one"]!!.tier)
        assertEquals(Tier.UNLISTED, by["com.example.mystery"]!!.tier)
        assertEquals(Tier.RECOMMENDED, by["com.tcl.guard"]!!.tier)
        // The G07 list keeps the voice assistant on; other TVs turn it off, so it's the owner's call.
        val voice = by["com.google.android.katniss"]!!
        assertEquals(Tier.YOUR_CALL, voice.tier)
        assertTrue(voice.keptByList && voice.note.contains("mic"))
        assertTrue("only Recommended starts ticked", rows.filter { it.selected }.all { it.tier == Tier.RECOMMENDED })
        assertEquals("Google Play services", by["com.google.android.gms"]!!.label)
        assertEquals(dev.tidytv.core.lists.Origin.YOURS, by["com.stremio.one"]!!.origin)
        assertEquals(dev.tidytv.core.lists.Origin.PREINSTALLED, by["com.tcl.guard"]!!.origin)
        assertTrue(rows.all { it.removable == (it.tier != Tier.LOCKED) })
    }
}
