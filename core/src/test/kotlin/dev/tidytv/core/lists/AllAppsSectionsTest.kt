package dev.tidytv.core.lists

import dev.tidytv.core.ops.AppFacts
import java.io.File
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import dev.tidytv.core.testing.FakeTv

class AllAppsSectionsTest {
    private fun row(pkg: String, tier: Tier, label: String = pkg) = AppRow(pkg, label, tier, "", false, Group.PRELOADED)

    private val rows = listOf(
        row("com.a.yours.small", Tier.YOURS), row("com.b.yours.big", Tier.YOURS),
        row("com.locked", Tier.LOCKED), row("com.unlisted", Tier.UNLISTED),
        row("com.other.big", Tier.OTHER_TVS), row("com.call.small", Tier.YOUR_CALL),
        row("com.rec.small", Tier.RECOMMENDED), row("com.rec.bigram", Tier.RECOMMENDED), row("com.rec.bigdisk", Tier.RECOMMENDED),
    )
    private val facts = mapOf(
        "com.a.yours.small" to AppFacts(appMb = 10),
        "com.b.yours.big" to AppFacts(appMb = 100, dataMb = 50, cacheMb = 50),
        "com.rec.bigram" to AppFacts(ramMb = 200),
        "com.rec.bigdisk" to AppFacts(ramMb = 50, appMb = 500),
        "com.rec.small" to AppFacts(ramMb = 50, appMb = 5),
        "com.other.big" to AppFacts(ramMb = 900),
    )

    @Test fun yoursFirstThenPreinstalled() {
        val s = AllAppsView.sections(rows, facts)
        assertEquals(listOf(Origin.YOURS, Origin.PREINSTALLED), s.map { it.origin })
        assertEquals(listOf("com.b.yours.big", "com.a.yours.small"), s[0].removable.map { it.pkg })
        assertTrue(s[0].more.isEmpty())
    }

    @Test fun removableBeforeMoreAndOrderedBySaving() {
        val pre = AllAppsView.sections(rows, facts)[1]
        assertEquals(listOf("com.rec.bigram", "com.rec.bigdisk", "com.rec.small", "com.call.small", "com.other.big"), pre.removable.map { it.pkg })
        assertEquals(listOf("com.unlisted", "com.locked"), pre.more.map { it.pkg })
    }

    @Test fun lockedIsNeverRemovable() {
        val s = AllAppsView.sections(rows, facts)
        assertTrue(s.flatMap { it.removable }.none { it.tier == Tier.LOCKED })
        assertTrue(rows.filter { it.tier == Tier.LOCKED }.none { it.removable })
        assertTrue(row("x", Tier.UNLISTED).removable)
    }

    @Test fun emptySectionIsLeftOut() {
        assertEquals(listOf(Origin.PREINSTALLED), AllAppsView.sections(rows.filter { it.origin == Origin.PREINSTALLED }, facts).map { it.origin })
    }

    @Test fun savingsSumRamAndStorage() {
        val v = AllAppsView.savings(listOf("com.b.yours.big", "com.rec.bigdisk", "com.rec.bigdisk", "com.unknown"), facts)
        assertEquals(AllAppsView.Savings(ramMb = 50, storageMb = 700), v)
        assertEquals(AllAppsView.Savings(0, 0), AllAppsView.savings(emptyList(), facts))
    }

    @Test fun searchMatchesLabelAndPackageIgnoringCase() {
        val r = listOf(row("com.stremio.one", Tier.YOURS, "Stremio"), row("in.startv.hotstar", Tier.OTHER_TVS, "Hotstar"))
        assertEquals(listOf("com.stremio.one"), AllAppsView.search(r, "STREM").map { it.pkg })
        assertEquals(listOf("in.startv.hotstar"), AllAppsView.search(r, " startv ").map { it.pkg })
        assertEquals(2, AllAppsView.search(r, "").size)
        assertTrue(AllAppsView.search(r, "zzz").isEmpty())
    }

    @Test fun realTvSplitsByOrigin() {
        val ex = File("../upstream/cleanyourtv/examples")
        val catalog = ListCatalog(ex.listFiles()!!.filter { it.isDirectory }.associate { it.name to File(it, "debloat.txt").readText() })
        val tv = FakeTv.googleTv()
        val installed = tv.installed + "com.stremio.one"
        val built = AllApps.build(catalog.byId("tcl-g07-google-tv")!!, catalog.lists, installed, emptySet(), setOf("com.stremio.one"), setOf("com.tcl.tvinput"))
        val s = AllAppsView.sections(built, emptyMap())
        assertEquals(listOf("com.stremio.one"), s[0].all.map { it.pkg })
        assertEquals(installed.size - 1, s[1].size)
        assertTrue(s[1].removable.isNotEmpty())
    }
}
