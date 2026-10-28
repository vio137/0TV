package dev.tidytv.core.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNameTest {
    @Test fun normalNameUnchanged() = assertEquals("My Movie (2020).mp4", SafeName.clean("My Movie (2020).mp4"))

    @Test fun pathsBecomeUnderscores() {
        assertEquals("_etc_passwd", SafeName.clean("/etc/passwd"))
        assertEquals("a_b_c.txt", SafeName.clean("a\\b/c.txt"))
    }

    @Test fun dangerousCharsReplaced() {
        val out = SafeName.clean("a:b*c?d\"e<f>g|h'i\$j`k\nl\u0000m")
        assertEquals("a_b_c_d_e_f_g_h_i_j_k_l_m", out)
    }

    @Test fun outputNeverHasQuoteOrSlash() {
        for (s in listOf("'; rm -rf /'", "../../x", "a/b\\c", "`id`", "\$(id)")) {
            val o = SafeName.clean(s)
            assertFalse(s, o.contains('\'') || o.contains('/') || o.contains('\\'))
        }
    }

    @Test fun repeatedUnderscoresCollapse() = assertEquals("a_b.txt", SafeName.clean("a///b.txt"))

    @Test fun hiddenNamesLoseLeadingDot() = assertEquals("bashrc", SafeName.clean(".bashrc"))

    @Test fun dotDotBecomesFile() {
        assertEquals("file", SafeName.clean(".."))
        assertEquals("file", SafeName.clean("."))
        assertEquals("_", SafeName.clean("../.."))
    }

    @Test fun dotDotInPathIsHarmless() = assertEquals("_x", SafeName.clean("../x"))

    @Test fun trimsSpacesAndDots() = assertEquals("a b", SafeName.clean("  ..a b..  "))

    @Test fun emptyBecomesFile() {
        assertEquals("file", SafeName.clean(""))
        assertEquals("file", SafeName.clean("   "))
    }

    @Test fun veryLongNameKeepsExtension() {
        val out = SafeName.clean("a".repeat(300) + ".mp4")
        assertEquals(100, out.length)
        assertTrue(out.endsWith(".mp4"))
        assertTrue(out.startsWith("aaa"))
    }

    @Test fun veryLongNameWithoutExtension() = assertEquals(100, SafeName.clean("b".repeat(300)).length)

    @Test fun longExtensionIsNotKept() {
        val out = SafeName.clean("a".repeat(150) + "." + "x".repeat(20))
        assertEquals(100, out.length)
    }

    @Test fun uniqueReturnsSameWhenFree() = assertEquals("a.mp4", SafeName.unique("a.mp4", setOf("b.mp4")))

    @Test fun uniqueAddsSuffixBeforeExtension() {
        assertEquals("a (2).mp4", SafeName.unique("a.mp4", setOf("a.mp4")))
        assertEquals("a (3).mp4", SafeName.unique("a.mp4", setOf("a.mp4", "a (2).mp4")))
    }

    @Test fun uniqueWithoutExtension() {
        assertEquals("notes (2)", SafeName.unique("notes", setOf("notes")))
        assertEquals("notes (3)", SafeName.unique("notes", setOf("notes", "notes (2)")))
    }
}
