package dev.tidytv.core.ops

import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Making the new home screen the real Home key target on a TV that behaves like the owner's TCL G06: the
 * enabled launcher with the highest priority always wins, `set-home-activity` and the HOME role change nothing.
 */
class HomeTakeoverTest {
    private val apk = ByteArray(37_287)
    private val ours = Launchers.TIDY_HOME_PACKAGE
    private val stock = Launchers.LAUNCHERX
    private val wraith = Launchers.SETUPWRAITH

    private fun handover(tv: FakeTv, log: ActionLog = MemoryActionLog()) =
        LauncherHandover(tv, log, clock = { 5L }, sleep = {}, settle = {})

    /** What the Home key does when pressed from another app, like a person on the couch. */
    private suspend fun homePressOpens(tv: FakeTv): String? {
        tv.run(Commands.OPEN_SETTINGS)
        tv.run(Commands.KEY_HOME)
        return Parse.focusedPackage(tv.run(Commands.FOCUSED_APP))
    }

    @Test fun theBugTheOwnerSawIsRealOnThisModel() = runTest {
        // Starting the launcher shows it on screen, but Android's Home key still goes to Google TV.
        val tv = FakeTv.tclG06().apply { installed += ours; homeClaimants["$ours/.MainActivity"] = 0 }
        tv.run("am start -W -n $ours/.MainActivity")
        assertEquals(ours, tv.focused)
        tv.run("cmd role add-role-holder --user 0 android.app.role.HOME $ours")
        tv.run("cmd package set-home-activity --user 0 $ours/.MainActivity")
        assertEquals("the role changes, the Home key doesn't", stock, Parse.component(tv.run(Commands.RESOLVE_HOME))!!.substringBefore('/'))
        assertEquals(stock, homePressOpens(tv))
    }

    @Test fun setupAppGoesFirstThenTheStockLauncherAndTheHomeKeyOpensOurs() = runTest {
        val tv = FakeTv.tclG06()
        val log = MemoryActionLog()
        val out = handover(tv, log).run(apk, listOf("com.netflix.ninja"), sdk = 30)
        assertEquals(HandoverOutcome.Done(stock), out)
        assertEquals(ours, homePressOpens(tv))
        assertTrue(wraith in tv.disabled && stock in tv.disabled)
        val disables = tv.commands.filter { it.startsWith("pm disable-user") }
        assertEquals("the setup app first, while Google TV still owns Home", wraith, disables.first().substringAfterLast(' '))
        assertEquals(setOf(wraith, stock), log.records().disabledByUs() - emptySet())
        assertEquals("each is on the TV's undo list once", "$wraith\n$stock\n", tv.files[Commands.MIRROR_FILE])
    }

    @Test fun restoreBringsBothBackAndGoogleTvOwnsTheHomeKeyAgain() = runTest {
        val tv = FakeTv.tclG06()
        val log = MemoryActionLog()
        handover(tv, log).run(apk, emptyList(), sdk = 30)
        val back = Restorer(tv, log) { 9L }.run()
        assertTrue(back.toString(), back is RestoreOutcome.Done)
        assertTrue(wraith !in tv.disabled && stock !in tv.disabled)
        assertEquals(stock, homePressOpens(tv))
        assertTrue("our launcher is gone again", ours !in tv.installed)
    }

    @Test fun theSetupAppOwningTheWifiBlocksBeforeAnythingChanges() = runTest {
        val tv = FakeTv.tclG06().apply { wifiOwner = "com.google.android.tungsten.setupwraith:10130" }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.SetupAppBlocked(WraithCheck.OwnsWifi), out)
        assertTrue("nothing turned off", tv.disabled.isEmpty())
        assertEquals(stock, homePressOpens(tv))
    }

    @Test fun onAnEthernetTvTheOwnerCanOverrideTheWifiCheck() = runTest {
        val tv = FakeTv.tclG06().apply { wifiOwner = "com.google.android.tungsten.setupwraith:10130" }
        val out = handover(tv).run(apk, emptyList(), sdk = 30, onEthernet = true)
        assertTrue(out is HandoverOutcome.Done)
        assertEquals(ours, homePressOpens(tv))
    }

    @Test fun anUnfinishedFirstSetupKeepsTheSetupApp() = runTest {
        val tv = FakeTv.tclG06().apply { settings["secure/user_setup_complete"] = "0" }
        assertEquals(HandoverOutcome.SetupAppBlocked(WraithCheck.SetupIncomplete), handover(tv).run(apk, emptyList(), sdk = 30))
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun ifTheStockLauncherWontTurnOffEverythingIsPutBack() = runTest {
        val tv = FakeTv.tclG06().apply { disableDoesNotStick += stock }
        val log = MemoryActionLog()
        val out = handover(tv, log).run(apk, emptyList(), sdk = 30)
        assertTrue(out.toString(), out is HandoverOutcome.RolledBack)
        assertTrue("the setup app is back on too", wraith !in tv.disabled)
        assertEquals(stock, homePressOpens(tv))
        assertTrue(log.records().disabledByUs().isEmpty())
    }

    @Test fun aHomeKeyThatOpensSomethingElseIsNeverCalledDone() = runTest {
        // Android's own answer says ours, but the Home key lands elsewhere (a firmware that overrides it).
        val tv = FakeTv.tclG06()
        val shell = object : TvShell by tv {
            override suspend fun run(command: String): String {
                if (command == Commands.FOCUSED_APP) return "  mFocusedApp=ActivityRecord{1 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t3}\n"
                return tv.run(command)
            }
        }
        val out = LauncherHandover(shell, MemoryActionLog(), clock = { 5L }, sleep = {}, settle = {}).run(apk, emptyList(), sdk = 30)
        assertNotEquals(HandoverOutcome.Done(stock), out)
        assertTrue(out.toString(), out is HandoverOutcome.RolledBack || out is HandoverOutcome.RollbackFailed)
        assertTrue(wraith !in tv.disabled && stock !in tv.disabled)
    }

    @Test fun ifTheTvWontSayWhatIsOnScreenTheOwnAnswerCounts() = runTest {
        val tv = FakeTv.tclG06()
        val shell = object : TvShell by tv {
            override suspend fun run(command: String) = if (command == Commands.FOCUSED_APP) "" else tv.run(command)
        }
        val out = LauncherHandover(shell, MemoryActionLog(), clock = { 5L }, sleep = {}, settle = {}).run(apk, emptyList(), sdk = 30)
        assertTrue(out is HandoverOutcome.Done)
    }

    @Test fun runningItTwiceChangesNothingTheSecondTime() = runTest {
        val tv = FakeTv.tclG06()
        val log = MemoryActionLog()
        assertTrue(handover(tv, log).run(apk, emptyList(), sdk = 30) is HandoverOutcome.Done)
        val undo = tv.files[Commands.MIRROR_FILE]
        assertTrue(handover(tv, log).run(apk, emptyList(), sdk = 30) is HandoverOutcome.Done)
        assertEquals("no duplicate entries on the TV's undo list", undo, tv.files[Commands.MIRROR_FILE])
        assertEquals(ours, homePressOpens(tv))
    }

    @Test fun aDroppedConnectionMidwayCanBeResumedAndRestored() = runTest {
        val tv = FakeTv.tclG06().apply { dropOnPrefix = "pm disable-user --user 0 $stock" }
        val log = MemoryActionLog()
        assertEquals(HandoverOutcome.Disconnected, handover(tv, log).run(apk, emptyList(), sdk = 30))
        // Only the setup app is off so far, and Home still works.
        assertTrue(wraith in tv.disabled && stock !in tv.disabled)
        assertEquals(stock, homePressOpens(tv))
        assertTrue(handover(tv, log).run(apk, emptyList(), sdk = 30) is HandoverOutcome.Done)
        assertEquals(ours, homePressOpens(tv))
        assertTrue(Restorer(tv, log) { 9L }.run() is RestoreOutcome.Done)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun anotherHomeScreenAppTiesWithOursSoNothingIsChangedAndTheOwnerIsToldWhy() = runTest {
        // Projectivy from Play is a HOME claimant at priority 0 too: with Google TV off, Android would show a chooser.
        val tv = FakeTv.tclG06().apply { installProjectivy() }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertTrue(out.toString(), out is HandoverOutcome.NeedsChoice)
        assertTrue((out as HandoverOutcome.NeedsChoice).claimants.containsAll(listOf(ours, "com.spocky.projengmenu")))
        assertTrue("Google TV is back and owns Home", wraith !in tv.disabled && stock !in tv.disabled)
        assertEquals(stock, homePressOpens(tv))
    }

    @Test fun parsesWhatIsOnScreen() {
        assertEquals("com.ansub.tvhome", Parse.focusedPackage("  mFocusedApp=ActivityRecord{d8d8195 u0 com.ansub.tvhome/.MainActivity t3576}"))
        assertEquals("com.android.tv.settings", Parse.focusedPackage("mFocusedApp=ActivityRecord{61d28af u0 com.android.tv.settings/.MainSettings t3577}"))
        assertNull(Parse.focusedPackage(""))
        assertNull(Parse.focusedPackage("mFocusedApp=null"))
    }
}
