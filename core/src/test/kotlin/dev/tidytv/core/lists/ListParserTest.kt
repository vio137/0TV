package dev.tidytv.core.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListParserTest {
    private val sample = """
        # TCL Smart TV Pro, US (device G05, product G05_4K_US)
        # Android 11 (Google TV)
        #
        # Stock launcher: com.google.android.apps.tv.launcherx (pass it to restore.sh).
        # Live TV app: com.tcl.tv (keep it, it also owns HDMI/antenna playback). Keep com.tcl.tvinput,
        # com.tcl.settings, com.tcl.keyhelp and com.tcl.providers.config.

        # TCL content, ads and promos
        com.tcl.tv.plus                        # TCL TV+ content app
        com.tcl.channelplus                    # TCL Live FAST channels

        # Voice, casting and assistants (owner's choice; comment out what you use)
        com.google.android.katniss             # Google Assistant / voice search
        com.google.android.apps.mediashell     # Chromecast built-in
        # com.tcl.airplay2                     # AirPlay, together with the next line
        # com.mediatek.AirplayAPK
        #   for p in com.google.android.gms; do pm revoke ${'$'}p x; done
    """.trimIndent()

    private fun ok(text: String, id: String = "t") = ListParser.parse(id, text) as ParseResult.Ok
    private fun rejected(text: String) = ListParser.parse("t", text) as ParseResult.Rejected

    @Test fun parsesEntriesSectionsAndNotes() {
        val list = ok(sample).list
        assertEquals(
            listOf("com.tcl.tv.plus", "com.tcl.channelplus", "com.google.android.katniss", "com.google.android.apps.mediashell"),
            list.packages,
        )
        assertEquals("TCL content, ads and promos", list.entries[0].section)
        assertEquals("TCL TV+ content app", list.entries[0].note)
        assertTrue(list.entries[2].section.startsWith("Voice, casting"))
        assertTrue(list.title.startsWith("TCL Smart TV Pro"))
    }

    @Test fun readsStockLauncherFromHeader() {
        assertEquals("com.google.android.apps.tv.launcherx", ok(sample).list.stockLauncher)
    }

    @Test fun keepSentencesAcrossLinesAreProtected() {
        val keep = ok(sample).list.keep
        assertTrue(keep.containsAll(listOf("com.tcl.tv", "com.tcl.tvinput", "com.tcl.settings", "com.tcl.keyhelp", "com.tcl.providers.config")))
        assertFalse("the stock launcher sentence has no keep word", "com.google.android.apps.tv.launcherx" in keep)
    }

    @Test fun commentedOutPackagesAreOptionalExtras() {
        val opt = ok(sample).list.optional.map { it.pkg }
        assertEquals(listOf("com.tcl.airplay2", "com.mediatek.AirplayAPK"), opt)
        assertTrue(ok(sample).list.optional.all { it.optional })
    }

    @Test fun shellRecipeInCommentIsNotAnOptionalEntry() {
        assertFalse(ok(sample).list.optional.any { it.pkg == "com.google.android.gms" })
    }

    @Test fun stripsBomAndCrlf() {
        val text = "﻿# TV\r\ncom.a.b\r\ncom.c.d   # note\r\n"
        val list = ok(text).list
        assertEquals(listOf("com.a.b", "com.c.d"), list.packages)
        assertEquals("note", list.entries[1].note)
    }

    @Test fun rejectsWholeListOnShellInjection() {
        for (bad in listOf("com.a.b;reboot", "com.a.b && reboot", "$(reboot)", "com.a.b`id`", "com.a.b|sh", "com.ä.b", "com..b", "com", "1com.a")) {
            val r = rejected("# TV\ncom.good.one\n$bad\n")
            assertTrue("should reject: $bad", r.reasons.any { it.contains("not a package name") })
        }
    }

    @Test fun rejectsTwoTokensOnOneLine() {
        assertTrue(rejected("# TV\ncom.a.b com.c.d\n").reasons.isNotEmpty())
    }

    @Test fun rejectsDuplicates() {
        assertTrue(rejected("# TV\ncom.a.b\ncom.a.b\n").reasons.any { it.startsWith("listed twice") })
    }

    @Test fun rejectsCommentOnlyList() {
        assertTrue(rejected("# TV\n# nothing here\n").reasons.contains("no packages"))
    }

    @Test fun rejectsMissingTitleComment() {
        assertTrue(rejected("com.a.b\n").reasons.any { it.startsWith("first line") })
    }

    @Test fun optionalThatIsAlsoActiveIsNotOptional() {
        val list = ok("# TV\ncom.a.b\n# com.a.b  # again\n# com.c.d\n").list
        assertEquals(listOf("com.c.d"), list.optional.map { it.pkg })
    }

    @Test fun detectsFireOsAndPmHide() {
        assertTrue(ok("# Amazon Fire TV Stick (Fire OS 7)\ncom.amazon.x\n").list.isFireOs)
        assertTrue(ok("# Redmi\n# use pm hide instead\ncom.mi.x\n").list.needsHide)
    }

    @Test fun packageNameValidator() {
        assertTrue(PackageName.isValid("com.google.android.youtube.tv"))
        assertTrue(PackageName.isValid("com.tcl.tcl_bt_rcu_service"))
        assertFalse(PackageName.isValid("com.a b"))
        assertFalse(PackageName.isValid("com.a;b"))
        assertFalse(PackageName.isValid("noDots"))
        assertFalse(PackageName.isValid(""))
    }
}
