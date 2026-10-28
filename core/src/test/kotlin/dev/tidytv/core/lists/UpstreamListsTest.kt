package dev.tidytv.core.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs every bundled upstream list through the parser and the safety rules (port of test_examples.py). */
class UpstreamListsTest {
    companion object {
        val upstream = File(System.getProperty("tidytv.upstream") ?: "../upstream/cleanyourtv")
        val sources: Map<String, String> by lazy {
            File(upstream, "examples").listFiles()!!.filter { it.isDirectory }
                .associate { it.name to File(it, "debloat.txt").readText() }
        }
        val catalog by lazy { ListCatalog(sources) }
    }

    @Test fun everyFolderHasAList() {
        assertTrue("expected the upstream submodule checked out", sources.size >= 50)
    }

    @Test fun everyListParses() {
        assertEquals(catalog.rejected.joinToString("\n"), 0, catalog.rejected.size)
        assertEquals(sources.size, catalog.lists.size)
    }

    @Test fun everyListHasAPackageAndATitle() {
        for (l in catalog.lists) {
            assertTrue(l.id, l.entries.isNotEmpty())
            assertTrue(l.id, l.title.isNotBlank())
        }
    }

    @Test fun stockLauncherIsReadWhereStated() {
        val stated = catalog.lists.filter { it.header.any { h -> h.startsWith("Stock launcher: com.") } }
        assertTrue(stated.size >= 40)
        for (l in stated) assertTrue(l.id, l.stockLauncher != null && PackageName.isValid(l.stockLauncher!!))
    }

    @Test fun candidatesNeverIncludeProtectedOrHomeApps() {
        for (l in catalog.lists) {
            val all = (l.entries + l.optional).map { it.pkg }.toSet()
            for (c in Grouping.candidates(l, installed = all)) {
                assertFalse("${l.id}: ${c.pkg}", Protected.isProtected(c.pkg, listKeep = l.keep))
                assertFalse("${l.id}: ${c.pkg}", Launchers.isHomeApp(c.pkg))
                assertFalse("${l.id}: ${c.pkg}", c.pkg in Protected.EXACT)
            }
        }
    }

    @Test fun corePackagesThatCanStopABootAreProtected() {
        for (p in listOf("com.android.ext.services", "com.google.android.ext.shared", "com.android.modulemetadata",
            "com.google.android.modulemetadata", "com.android.phone", "com.google.android.overlay.modules.permissioncontroller",
            "com.android.providers.telephony", "com.android.providers.media.module", "com.android.providers.downloads.ui"))
            assertTrue(p, Protected.isProtected(p))
        for (p in listOf("com.android.providers.calendar", "com.android.providers.contacts",
            "com.android.providers.userdictionary", "com.android.providers.blockednumber"))
            assertFalse("a TV can live without $p", Protected.isProtected(p))
        assertTrue(Launchers.isHomeApp("com.cvte.tv.launcher.atv"))
    }

    @Test fun keptAppsFromUpstreamFixesAreNoLongerOffered() {
        // Each was in a list we shipped and broke something on that TV (upstream fixes after 2.4).
        val fixed = mapOf(
            "nvidia-shield-tv-2019" to "com.google.android.tvrecommendations",
            "nvidia-shield-tv-pro-2019" to "com.google.android.tvrecommendations",
            "onn-4k-streaming-box-2023" to "com.google.android.apps.tv.dreamx",
            "xiaomi-mitv-afmu0" to "com.google.android.tv.remote.service",
            "xiaomi-tv-a-pro-2026" to "com.google.android.apps.mediashell",
            // After 2.6: disabling it gives "No Signal" on every input, HDMI included.
            "sankey-smart2ktvse" to "com.zeasn.livetv",
        )
        for ((id, pkg) in fixed) {
            val l = catalog.byId(id)!!
            assertFalse("$id still offers $pkg", pkg in l.entries.map { it.pkg })
        }
    }

    @Test fun playServicesAndStoreAreNeverCandidates() {
        for (l in catalog.lists) {
            val all = (l.entries + l.optional).map { it.pkg }.toSet() + setOf("com.google.android.gms", "com.android.vending")
            val c = Grouping.candidates(l, installed = all).map { it.pkg }
            assertFalse(l.id, "com.google.android.gms" in c || "com.android.vending" in c)
        }
    }

    @Test fun mostListedPackagesSurviveTheSafetyFilter() {
        // Guard against over-protection quietly emptying the clean-up.
        var listed = 0
        var offered = 0
        for (l in catalog.lists) {
            val installed = l.entries.map { it.pkg }.toSet()
            listed += installed.size
            offered += Grouping.candidates(l, installed).size
        }
        assertTrue("offered $offered of $listed", offered >= listed * 0.75)
    }

    @Test fun listMatchesItsOwnTitleCodename() {
        val g05 = catalog.byId("tcl-g05-google-tv")!!
        val tv = TvInfo("Smart TV Pro", "G05", "G05_4K_US", "TCL", "TCL", 30, "11", "x")
        val m = ModelMatcher.match(tv, catalog.lists)
        assertTrue(m.first().exact)
        assertTrue(m.any { it.list == g05 })
    }

    @Test fun aTvThatOnlySaysSmartTvIsNeverMatchedToAnotherBrand() {
        // A TCL Google TV 11 that reports its model as just "Smart TV" (a G06 before cleanyourtv added a G06 list;
        // G99 stands in for a TCL board nobody has listed). 0.6.0 matched it to a Fire TV list whose title says
        // "smart TV", and offered one app.
        val g06 = TvInfo("Smart TV", "G99", "G99_2K_GB", "TCL", "TCL", 30, "11", "x")
        val installed = catalog.byId("tcl-g07-google-tv")!!.entries.map { it.pkg }.toSet()
        val m = ModelMatcher.match(g06, catalog.lists, installed = installed)
        assertTrue(m.joinToString { it.list.id }, m.all { it.list.id.startsWith("tcl-") })
        assertFalse("no list is for this board, so nothing is exact", m.first().exact)
        assertEquals("the closest list by the apps on the TV comes first", "tcl-g07-google-tv", m.first().list.id)
        for (other in listOf(
            TvInfo("Android TV", "x1", "y1", "Acme", "Acme", 30, "11", "x"),
            TvInfo("Google TV", "x2", "y2", "Acme", "Acme", 30, "11", "x"),
        )) assertTrue(other.model, ModelMatcher.match(other, catalog.lists).none { it.exact })
    }

    @Test fun listsTidyTvCantApplyAreNeverOffered() {
        // The TiVo Stream 4K refuses pm disable-user for every app; its list suspends them instead.
        val tivo = TvInfo("TiVo Stream 4K", "SEI400TV", "SEI400TV", "TiVo", "SEI Robotics", 31, "12", "x")
        assertTrue(ModelMatcher.match(tivo, catalog.lists).none { it.list.id == "tivo-stream-4k" })
        assertTrue("the list itself still parses", catalog.byId("tivo-stream-4k") != null)
    }

    @Test fun aRealModelNumberStillMatchesExactly() {
        val tv = TvInfo("65C735", "unknown1", "unknown2", "TCL", "TCL", 30, "11", "x")
        assertEquals("tcl-65c735", ModelMatcher.match(tv, catalog.lists).first().list.id)
        assertTrue(ModelMatcher.match(tv, catalog.lists).first().exact)
    }

    @Test fun everyPackageHasALabel() {
        for (l in catalog.lists) for (e in l.entries) assertTrue(e.pkg, AppLabels.label(e.pkg, e.note).isNotBlank())
    }

    @Test fun report() {
        // Not an assertion: prints what the safety filter removes, for review when upstream changes.
        val out = StringBuilder()
        for (l in catalog.lists) {
            val removed = l.entries.map { it.pkg }.filter { Launchers.isHomeApp(it) || Protected.isProtected(it, listKeep = l.keep) }
            if (removed.isNotEmpty()) out.append("${l.id}: ${removed.joinToString { "$it (${Protected.reason(it, listKeep = l.keep) ?: "home app"})" }}\n")
        }
        File("build/list-report.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }
}
