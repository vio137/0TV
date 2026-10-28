package dev.tidytv.core.ops

import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebloaterTest {
    private val bloat = listOf("com.tcl.tv.plus", "com.tcl.channelplus", "com.tcl.ttvs", "com.tcl.guard",
        "com.tcl.messagebox", "com.tcl.waterfall.overseas", "com.google.android.feedback")

    private fun debloater(tv: FakeTv, log: ActionLog = MemoryActionLog(), slept: MutableList<Long> = mutableListOf()) =
        Debloater(tv, log, clock = { 1L }, sleep = { slept += it })

    @Test fun happyPathDisablesVerifiesLogsAndMirrors() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        val slept = mutableListOf<Long>()
        val out = debloater(tv, log, slept).run(bloat)
        assertTrue(out is DebloatOutcome.Finished)
        assertEquals(bloat.size, out.disabledCount)
        assertTrue(tv.disabled.containsAll(bloat))
        assertEquals(bloat.toSet(), log.records().disabledByUs())
        assertEquals(bloat.toSet(), Parse.mirror(tv.files[Commands.MIRROR_FILE] + "END"))
        assertEquals(listOf(Debloater.SAVE_WAIT_MS), slept)
    }

    @Test fun onePmCommandPerPackageNeverChained() = runTest {
        val tv = FakeTv.googleTv()
        debloater(tv).run(bloat)
        val disables = tv.commands.filter { it.startsWith("pm disable-user") }
        assertEquals(bloat.size, disables.size)
        assertTrue(disables.none { it.contains(";") || it.contains("&&") })
    }

    @Test fun mirrorAndIntentAreWrittenBeforeEachDisable() = runTest {
        val tv = FakeTv.googleTv()
        val log = object : ActionLog {
            val inner = MemoryActionLog()
            override fun append(record: ActionRecord) {
                if (record.phase == Phase.INTENT) {
                    // The TV has not been told yet when the intent lands.
                    assertFalse(record.target in tv.disabled)
                    assertTrue(tv.files[Commands.MIRROR_FILE]!!.contains(record.target))
                }
                inner.append(record)
            }
            override fun records() = inner.records()
        }
        debloater(tv, log).run(bloat)
    }

    @Test fun alreadyDisabledIsNotTouchedOrClaimed() = runTest {
        val tv = FakeTv.googleTv().apply { disabled += "com.tcl.ttvs" }
        val log = MemoryActionLog()
        val out = debloater(tv, log).run(bloat)
        assertTrue(out.results.contains(PkgResult.AlreadyOff("com.tcl.ttvs")))
        assertFalse("restore must not turn on what the owner had off", "com.tcl.ttvs" in log.records().disabledByUs())
    }

    @Test fun notInstalledIsSkipped() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv).run(listOf("com.not.here", "com.tcl.ttvs"))
        assertTrue(out.results.contains(PkgResult.NotInstalled("com.not.here")))
        assertTrue(out.results.contains(PkgResult.Disabled("com.tcl.ttvs")))
        assertFalse(tv.commands.any { it.contains("com.not.here") && it.startsWith("pm disable") })
    }

    @Test fun protectedPackagesAreRefusedEvenIfAsked() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv).run(listOf("com.google.android.gms", "com.android.vending", "com.tcl.tvinput", "com.android.systemui"))
        assertTrue(out.results.all { it is PkgResult.Skipped })
        assertTrue(tv.commands.none { it.startsWith("pm disable") })
    }

    @Test fun liveOwnersAndListKeepAreRefused() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv).run(listOf("com.tcl.tv.plus", "com.tcl.guard"), liveProtected = setOf("com.tcl.tv.plus"), listKeep = setOf("com.tcl.guard"))
        assertTrue(out.results.all { it is PkgResult.Skipped })
    }

    @Test fun homeAppsAreLeftToTheHandover() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv).run(listOf(Launchers.LAUNCHERX, Launchers.SETUPWRAITH))
        assertTrue(out.results.all { it is PkgResult.Skipped })
        assertEquals(Launchers.LAUNCHERX + "/.MainActivity", tv.home())
    }

    @Test fun persistentPackagesAreSkipped() = runTest {
        val tv = FakeTv.googleTv().apply { persistent += "com.tcl.guard" }
        val out = debloater(tv).run(listOf("com.tcl.guard", "com.tcl.ttvs"))
        assertTrue(out.results.any { it is PkgResult.Skipped && it.pkg == "com.tcl.guard" })
        assertTrue("com.tcl.ttvs" in tv.disabled)
    }

    @Test fun refusedDisableStopsTheRun() = runTest {
        val tv = FakeTv.googleTv().apply { refuseDisable += "com.tcl.ttvs" }
        val out = debloater(tv).run(bloat)
        assertTrue(out is DebloatOutcome.Stopped)
        val idx = bloat.indexOf("com.tcl.ttvs")
        assertTrue(tv.disabled.containsAll(bloat.take(idx)))
        assertFalse(bloat.drop(idx + 1).any { it in tv.disabled })
    }

    @Test fun changeThatDoesNotStickStopsTheRun() = runTest {
        val tv = FakeTv.googleTv().apply { disableDoesNotStick += "com.tcl.channelplus" }
        val log = MemoryActionLog()
        val out = debloater(tv, log).run(bloat)
        assertTrue(out is DebloatOutcome.Stopped)
        assertTrue(out.results.contains(PkgResult.Failed("com.tcl.channelplus", "still on after the command")))
        // The first batch of five is checked together; nothing after it is touched.
        assertFalse(bloat.drop(5).any { it in tv.disabled })
    }

    @Test fun readOnlyMirrorStopsBeforeAnyChange() = runTest {
        val tv = FakeTv.googleTv().apply { mirrorWritable = false }
        val out = debloater(tv).run(bloat)
        assertTrue(out is DebloatOutcome.Stopped)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun garbageFromPmStopsSafely() = runTest {
        val tv = FakeTv.googleTv().apply { listPackagesGarbage = true }
        val out = debloater(tv).run(bloat)
        assertTrue(out is DebloatOutcome.Stopped)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun droppedConnectionThenResumeFinishesWithoutDuplicates() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "pm disable-user --user 0 com.tcl.guard" }
        val log = MemoryActionLog()
        val first = debloater(tv, log).run(bloat)
        assertTrue(first is DebloatOutcome.Disconnected)

        val second = debloater(tv, log).run(bloat)
        assertTrue(second is DebloatOutcome.Finished)
        assertTrue(tv.disabled.containsAll(bloat))
        // Everything is restorable, including the ones finished before the drop.
        assertEquals(bloat.toSet(), log.records().disabledByUs())
        val disableCount = tv.commands.count { it.startsWith("pm disable-user") }
        assertEquals("no package disabled twice", bloat.size, disableCount)
    }

    @Test fun resumeCreditsAppsTurnedOffBeforeTheDropButNotOnesAlreadyOff() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "pm disable-user --user 0 ${bloat[2]}" }
        val userOff = bloat.last()
        tv.disabled += userOff
        val log = MemoryActionLog()
        debloater(tv, log).run(bloat)

        val second = debloater(tv, log).run(bloat)
        val byPkg = second.results.associateBy { it.pkg }
        for (p in bloat.dropLast(1)) assertEquals(p, PkgResult.Disabled(p), byPkg[p])
        assertEquals(PkgResult.AlreadyOff(userOff), byPkg[userOff])
        assertTrue(log.records().pendingDisables().isEmpty())
        assertTrue(userOff !in log.records().disabledByUs())
    }

    @Test fun dropBeforeAnyCommandChangesNothing() = runTest {
        val tv = FakeTv.googleTv().apply { dropAtCommand = 1 }
        val out = debloater(tv).run(bloat)
        assertTrue(out is DebloatOutcome.Disconnected)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun emptySelectionDoesNothing() = runTest {
        val tv = FakeTv.googleTv()
        val slept = mutableListOf<Long>()
        val out = debloater(tv, slept = slept).run(emptyList())
        assertTrue(out is DebloatOutcome.Finished)
        assertTrue(tv.commands.isEmpty())
        assertTrue(slept.isEmpty())
    }

    @Test fun invalidNameNeverReachesTheShell() = runTest {
        val tv = FakeTv.googleTv()
        val out = debloater(tv).run(listOf("com.a;reboot"))
        assertTrue(out.results.single() is PkgResult.Skipped)
        assertTrue(tv.commands.none { it.contains("reboot") })
    }
}
