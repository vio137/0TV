package dev.tidytv.core.ops

import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.5.0: Check my TV, the reboot test's TV side, and Projectivy from the Play Store. */
class UpkeepTest {
    private val bloat = listOf("com.tcl.tv.plus", "com.tcl.ttvs", "com.tcl.guard")

    private suspend fun tidied(): Pair<FakeTv, ActionLog> {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        Debloater(tv, log, clock = { 1L }, sleep = {}).run(bloat)
        return tv to log
    }

    @Test fun freshlyTidiedTvIsAllGood() = runTest {
        val (tv, log) = tidied()
        val r = TvCheck(tv, log).run(bloat, installedAtBackup = tv.installed.toSet())
        assertTrue(r.allGood)
        assertEquals(3, r.stillOff)
    }

    @Test fun theOldLauncherIsNotCountedAsAnAppStillOff() = runTest {
        val (tv, log) = tidied()
        // The home-screen switch turned the stock launcher off, as LauncherHandover does.
        log.append(ActionRecord(2L, Op.DISABLE, Launchers.LAUNCHERX, Phase.DONE))
        tv.run(Commands.disable(Launchers.LAUNCHERX))
        val r = TvCheck(tv, log).run(bloat, installedAtBackup = tv.installed.toSet())
        assertEquals("same count as the hub and Done screens", 3, r.stillOff)
    }

    @Test fun firmwareUpdateTurningAppsBackOnIsFound() = runTest {
        val (tv, log) = tidied()
        val before = tv.installed.toSet()
        tv.reEnableOnReboot += setOf("com.tcl.ttvs", "com.tcl.guard")
        tv.installOnReboot += "com.tcl.newads"
        tv.installed += "com.tcl.userapp" // something the owner installed, not on the list
        assertTrue(runCatching { tv.run(Commands.REBOOT) }.exceptionOrNull() is TvConnectionLost)
        assertEquals(1, tv.reboots)

        val r = TvCheck(tv, log).run(bloat + "com.tcl.newads" + "com.tcl.keptbyowner", installedAtBackup = before + "com.tcl.keptbyowner")
        assertEquals(listOf("com.tcl.guard", "com.tcl.ttvs"), r.backOn)
        assertEquals("only list apps that arrived since the backup", listOf("com.tcl.newads"), r.newOnList)
        assertEquals(1, r.stillOff)
        assertFalse(r.allGood)
    }

    @Test fun reTidyingTheReportTurnsThemOffAgain() = runTest {
        val (tv, log) = tidied()
        tv.disabled -= "com.tcl.ttvs"
        val r = TvCheck(tv, log).run(bloat, tv.installed.toSet())
        Debloater(tv, log, clock = { 2L }, sleep = {}).run(r.toTidy)
        assertTrue(TvCheck(tv, log).run(bloat, tv.installed.toSet()).allGood)
    }

    @Test fun appsTheOwnerTurnedBackOnAreNotFlagged() = runTest {
        val (tv, log) = tidied()
        Restorer(tv, log).enableOnly(setOf("com.tcl.ttvs"))
        assertTrue(TvCheck(tv, log).run(bloat, tv.installed.toSet()).allGood)
    }

    @Test fun homeTakenBackByTheStockLauncherIsFound() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        LauncherHandover(tv, log, clock = { 5L }, sleep = {}).run(ByteArray(10), emptyList(), sdk = 30)
        assertFalse(TvCheck(tv, log).run(emptyList(), null).homeChanged)
        // A firmware update re-enables launcherx and makes it Home again.
        tv.disabled -= Launchers.LAUNCHERX
        tv.preferredHome = Launchers.LAUNCHERX + "/.MainActivity"
        assertTrue(TvCheck(tv, log).run(emptyList(), null).homeChanged)
    }

    // ---- Projectivy ----

    @Test fun projectivyNotInstalledChangesNothing() = runTest {
        val tv = FakeTv.googleTv()
        val homeBefore = tv.home()
        val out = LauncherHandover(tv, MemoryActionLog(), clock = { 5L }, sleep = {}, app = HomeApp.PROJECTIVY).run(ByteArray(0), emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.NotInstalled, out)
        assertEquals(homeBefore, tv.home())
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun projectivyFromThePlayStoreBecomesHomeWithoutAnInstall() = runTest {
        val tv = FakeTv.googleTv().apply { playStoreInstalls = true }
        tv.run(Commands.playStorePage(HomeApp.PROJECTIVY.pkg))
        assertEquals(listOf(HomeApp.PROJECTIVY.pkg), tv.playStoreOpened)
        val log = MemoryActionLog()
        val out = LauncherHandover(tv, log, clock = { 5L }, sleep = {}, app = HomeApp.PROJECTIVY).run(ByteArray(0), emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.Done(Launchers.LAUNCHERX), out)
        assertEquals("com.spocky.projengmenu/.ui.home.MainActivity", tv.home())
        assertTrue("nothing pushed or installed by TidyTV", tv.pushed.isEmpty() && tv.commands.none { it == Commands.INSTALL_TMP })
        assertFalse(log.records().launcherInstalledByUs(HomeApp.PROJECTIVY.pkg, "x"))
    }

    @Test fun restoreBringsTheOldHomeBackButKeepsProjectivyInstalled() = runTest {
        val tv = FakeTv.googleTv().apply { installProjectivy() }
        val log = MemoryActionLog()
        LauncherHandover(tv, log, clock = { 5L }, sleep = {}, app = HomeApp.PROJECTIVY).run(ByteArray(0), emptyList(), sdk = 30)
        val out = Restorer(tv, log).run()
        assertTrue((out as RestoreOutcome.Done).homeRestored)
        assertEquals(Launchers.LAUNCHERX + "/.MainActivity", tv.home())
        assertTrue("the owner's own Play Store app stays", HomeApp.PROJECTIVY.pkg in tv.installed)
        assertTrue(tv.commands.none { it.startsWith("pm uninstall") })
    }

    @Test fun projectivyIsNeverOnATidyList() {
        assertTrue(Launchers.isHomeApp(HomeApp.PROJECTIVY.pkg))
    }
}
