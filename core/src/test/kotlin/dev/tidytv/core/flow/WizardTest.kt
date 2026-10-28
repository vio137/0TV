package dev.tidytv.core.flow

import dev.tidytv.core.flow.WizardEvent as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WizardTest {
    private fun r(s: Wizard, vararg es: E): Wizard? = es.fold<E, Wizard?>(s) { acc, e -> acc?.let { WizardReducer.reduce(it, e) } }

    @Test fun googleTvHappyPath() {
        var s = r(Wizard(), E.Choose(TvKind.ANDROID_TV))!!
        assertEquals(Step.GET_READY, s.step)
        s = r(s, E.Ready, E.Connected(fireOs = false))!!
        assertEquals("connecting opens the My TV hub", Step.HUB, s.step)
        s = r(s, E.OpenTidy)!!
        assertEquals(Step.REVIEW, s.step)
        s = r(s, E.StartTidy)!!
        assertEquals(Wizard(Step.TIDYING, TvKind.ANDROID_TV, Mode.CLEAN, busy = true), s)
        s = r(s, E.TidyEnded(stopped = false, launcherPossible = true))!!
        assertEquals(Step.HOME_SCREEN, s.step)
        assertEquals(Step.DONE, r(s, E.HomeScreenDone)!!.step)
    }

    @Test fun samsungAndLgAreNotSupported() {
        val s = r(Wizard(), E.Choose(TvKind.SAMSUNG_LG))!!
        assertEquals(Step.UNSUPPORTED, s.step)
        assertEquals(Wizard(), r(s, E.Back))
    }

    @Test fun fireTvSkipsHomeScreen() {
        val s = r(Wizard(), E.Choose(TvKind.FIRE_TV), E.Ready, E.Connected(fireOs = true), E.OpenTidy, E.StartTidy, E.TidyEnded(false, launcherPossible = true))!!
        assertEquals(Step.DONE, s.step)
    }

    @Test fun tvItselfDecidesFireOs() {
        assertEquals(TvKind.FIRE_TV, r(Wizard(), E.Choose(TvKind.NOT_SURE), E.Ready, E.Connected(fireOs = true))!!.kind)
        assertEquals(TvKind.ANDROID_TV, r(Wizard(), E.Choose(TvKind.FIRE_TV), E.Ready, E.Connected(fireOs = false))!!.kind)
    }

    @Test fun stoppedTidyStaysWithMessage() {
        val s = r(Wizard(), E.Choose(TvKind.ANDROID_TV), E.Ready, E.Connected(false), E.OpenTidy, E.StartTidy, E.TidyEnded(stopped = true, launcherPossible = true))!!
        assertEquals(Step.TIDYING, s.step)
        assertEquals(false, s.busy)
        assertEquals(Step.DONE, r(s, E.ToDone)!!.step)
    }

    @Test fun restoreIsATileOnTheHub() {
        val hub = r(Wizard(), E.RestoreTapped, E.Ready, E.Connected(false))!!
        assertEquals(Step.HUB, hub.step)
        assertEquals(Wizard(Step.DONE, TvKind.ANDROID_TV, Mode.RESTORE), r(hub, E.OpenRestore))
    }

    @Test fun hubTilesOpenEachFeatureAndBackReturnsToTheHub() {
        val hub = r(Wizard(), E.Choose(TvKind.ANDROID_TV), E.Ready, E.Connected(false))!!
        for ((open, step) in listOf(E.OpenTidy to Step.REVIEW, E.OpenRemote to Step.REMOTE, E.OpenFiles to Step.SEND_FILES, E.OpenHomeScreen to Step.HOME_SCREEN, E.OpenRestore to Step.DONE)) {
            val inside = r(hub, open)!!
            assertEquals(step, inside.step)
            assertEquals("ToHub from $step", Step.HUB, r(inside, E.ToHub)!!.step)
        }
        assertEquals(Step.HUB, r(hub, E.OpenRemote, E.Back)!!.step)
        assertEquals(Step.HUB, r(hub, E.OpenFiles, E.Back)!!.step)
        assertEquals(Step.HUB, r(hub, E.OpenTidy, E.Back)!!.step)
        assertEquals("Back from the hub goes home", Wizard(), r(hub, E.Back))
    }

    @Test fun fireTvHasNoHomeScreenTile() {
        val hub = r(Wizard(), E.Choose(TvKind.FIRE_TV), E.Ready, E.Connected(fireOs = true))!!
        assertEquals(hub, r(hub, E.OpenHomeScreen))
        assertEquals(Step.REMOTE, r(hub, E.OpenRemote)!!.step)
    }

    @Test fun savedTvSkipsSetupAndBackGoesHome() {
        val c = r(Wizard(), E.ConnectSaved)!!
        assertEquals(Step.CONNECT, c.step)
        assertEquals(Wizard(), r(c, E.Back))
        assertEquals(Step.HUB, r(c, E.Connected(false))!!.step)
    }

    @Test fun tilesOnlyWorkFromTheHub() {
        val review = Wizard(Step.REVIEW, TvKind.ANDROID_TV)
        for (e in listOf(E.OpenTidy, E.OpenRemote, E.OpenFiles, E.OpenHomeScreen, E.OpenRestore)) assertEquals(review, r(review, e))
        assertEquals(Wizard(), r(Wizard(), E.ToHub))
    }

    @Test fun backNavigation() {
        val connect = r(Wizard(), E.Choose(TvKind.ANDROID_TV), E.Ready)!!
        assertEquals(Step.GET_READY, r(connect, E.Back)!!.step)
        val review = r(connect, E.Connected(false), E.OpenTidy)!!
        assertEquals(Step.HUB, r(review, E.Back)!!.step)
        assertEquals(Step.DONE, r(review, E.StartTidy, E.Busy(false), E.Back)!!.step)
        assertEquals(Step.HUB, r(Wizard(step = Step.DONE), E.Back)!!.step)
        assertNull(r(Wizard(), E.Back))
    }

    @Test fun nothingLeavesWhileBusy() {
        val busy = r(Wizard(), E.Choose(TvKind.ANDROID_TV), E.Ready, E.Connected(false), E.OpenTidy, E.StartTidy)!!
        assertEquals(busy, r(busy, E.ToHub))
        assertEquals(busy, r(busy, E.Back))
        assertEquals(busy, r(busy, E.StartOver))
        assertEquals(busy, r(busy, E.RestoreTapped))
        assertEquals(busy, r(busy, E.ToDone))
    }

    @Test fun eventsForOtherScreensAreIgnored() {
        assertEquals(Wizard(), r(Wizard(), E.StartTidy))
        assertEquals(Wizard(), r(Wizard(), E.Connected(false)))
        assertEquals(Wizard(), r(Wizard(), E.HomeScreenDone))
        val review = Wizard(Step.REVIEW, TvKind.ANDROID_TV)
        assertEquals(review, r(review, E.Choose(TvKind.FIRE_TV)))
    }
}
