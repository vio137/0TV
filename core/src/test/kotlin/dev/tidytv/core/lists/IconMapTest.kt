package dev.tidytv.core.lists

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every package in every list shows either a bundled brand mark or a readable initials chip. */
class IconMapTest {
    private val bundled: Set<String> by lazy {
        val f = File(System.getProperty("tidytv.icons") ?: "../app/src/main/assets/icons.json")
        Regex("\"([a-z0-9]+)\":\\{").findAll(f.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test fun everyListedPackageHasAnIconOrInitials() {
        for (l in UpstreamListsTest.catalog.lists) for (e in l.entries + l.optional) {
            val slug = AppLabels.iconSlug(e.pkg)
            if (slug != null && slug in bundled) continue
            val label = AppLabels.label(e.pkg, e.note)
            assertNotEquals("${l.id}: ${e.pkg} has no icon and no initials", "?", AppLabels.initials(label))
        }
    }

    @Test fun bundleHasTheBigBrands() {
        for (s in listOf("netflix", "youtube", "google", "spotify", "android")) assertTrue(s, s in bundled)
    }

    @Test fun bundleHoldsOnlySlugsTheAppAsksFor() {
        assertTrue((bundled - AppLabels.ALL_SLUGS).toString(), (bundled - AppLabels.ALL_SLUGS).isEmpty())
    }

    @Test fun knownAppsUseTheirBrand() {
        assertTrue(AppLabels.iconSlug("com.netflix.ninja") == "netflix")
        assertTrue(AppLabels.iconSlug("com.tcl.tv.plus") == "tcl")
        assertTrue(AppLabels.iconSlug("org.example.unknown") == null)
    }
}
