package dev.tidytv.core.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    private fun list(id: String, vararg pkgs: Pair<String, String>, keep: Set<String> = emptySet(), optional: List<String> = emptyList()) = DebloatList(
        id = id, title = id, header = emptyList(),
        entries = pkgs.map { (p, note) -> ListEntry(p, "Bloat", note) },
        optional = optional.map { ListEntry(it, "Optional", "", optional = true) },
        stockLauncher = null, keep = keep, isFireOs = false, needsHide = false,
    )

    private val current = list("this-tv", "com.tcl.channel" to "TCL Channel")
    private val other = list(
        "other-tv",
        "com.tcl.channel" to "TCL Channel",
        "com.android.vending" to "Play Store",
        "com.google.android.gms" to "Play Services",
        "com.google.android.apps.tv.launcherx" to "Google TV home",
        "com.google.android.tvlauncher" to "Old home",
        "com.netflix.ninja" to "Netflix",
        "com.tcl.guard" to "TGuard",
        "com.tcl.notinstalled" to "Not here",
        "com.tcl.needed" to "needed for HDMI",
        keep = setOf("com.tcl.kept"),
        optional = listOf("com.tcl.airplay"),
    )
    private val third = list("third-tv", "com.tcl.kept" to "Kept elsewhere", "com.tcl.guard" to "Duplicate")
    private val catalog = listOf(current, other, third)
    private val installed = setOf(
        "com.tcl.channel", "com.android.vending", "com.google.android.gms", "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher", "com.netflix.ninja", "com.tcl.guard", "com.tcl.needed", "com.tcl.kept",
        "com.tcl.airplay", "com.example.keyboard",
    )
    private val offered = Grouping.candidates(current, installed).map { it.pkg }.toSet()

    private fun more(live: Set<String> = emptySet()) =
        Grouping.moreCandidates(catalog, installed, live, current.keep, offered).map { it.pkg }

    @Test fun findsInstalledAppsFromOtherListsAllUnselected() {
        val more = Grouping.moreCandidates(catalog, installed, emptySet(), current.keep, offered)
        assertEquals(setOf("com.netflix.ninja", "com.tcl.guard", "com.tcl.airplay"), more.map { it.pkg }.toSet())
        assertTrue(more.none { it.selected })
        assertEquals("listed once even when two lists have it", 1, more.count { it.pkg == "com.tcl.guard" })
    }

    @Test fun neverReturnsProtectedApps() {
        val more = more(live = setOf("com.tcl.guard"))
        assertFalse("com.android.vending" in more)
        assertFalse("com.google.android.gms" in more)
        assertFalse("live-protected on this TV", "com.tcl.guard" in more)
        assertFalse("another list keeps it", "com.tcl.kept" in more)
        assertFalse("another list says it's needed", "com.tcl.needed" in more)
    }

    @Test fun neverReturnsHomeApps() {
        val more = more()
        assertFalse("com.google.android.apps.tv.launcherx" in more)
        assertFalse("com.google.android.tvlauncher" in more)
    }

    @Test fun neverReturnsAppsAlreadyOfferedOrNotInstalled() {
        val more = more()
        assertFalse("com.tcl.channel" in more)
        assertFalse("com.tcl.notinstalled" in more)
    }
}
