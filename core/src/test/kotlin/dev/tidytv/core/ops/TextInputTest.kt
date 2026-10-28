package dev.tidytv.core.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputTest {
    @Test fun plainWord() = assertEquals("input text 'hello'", TextInput.command("hello"))

    @Test fun spacesBecomePercentS() =
        assertEquals("input text 'my%sshow%s2'", TextInput.command("my show 2"))

    @Test fun percentIsEscaped() = assertEquals("input text '50\\%%soff'", TextInput.command("50% off"))

    @Test fun singleQuoteIsEscaped() = assertEquals("input text 'it'\\''s'", TextInput.command("it's"))

    /** Walks the quoting the way sh does: returns true if the quote is closed only at the end. */
    private fun staysInsideOneArgument(cmd: String): Boolean {
        val rest = cmd.removePrefix("input text ")
        var i = 0
        var inQuote = false
        while (i < rest.length) {
            val c = rest[i]
            if (inQuote) {
                if (c == '\'') inQuote = false
            } else {
                when {
                    c == '\'' -> inQuote = true
                    rest.startsWith("\\'", i) -> i++
                    else -> return false // any unquoted char other than the escape is a breakout
                }
            }
            i++
        }
        return !inQuote
    }

    @Test fun injectionAttemptsStayInsideTheQuotes() {
        val attempts = listOf(
            "a; reboot", "a && reboot", "\$(reboot)", "`reboot`", "a | sh", "a > /sdcard/x",
            "a\nreboot", "'; reboot; '", "'\"'\"'; reboot", "''", "\\'; reboot",
        )
        for (a in attempts) {
            val cmd = TextInput.command(a)
            assertNotNull(a, cmd)
            assertTrue("$a -> $cmd", staysInsideOneArgument(cmd!!))
        }
    }

    @Test fun quoteBreakoutIsExactlyEscaped() =
        assertEquals("input text ''\\'';%sreboot;%s'\\'''", TextInput.command("'; reboot; '"))

    @Test fun newlineAndTabAreDropped() {
        assertEquals("input text 'ab'", TextInput.command("a\nb"))
        assertEquals("input text 'ab'", TextInput.command("a\tb\r"))
        assertTrue(TextInput.unsupported("a\nb"))
    }

    @Test fun controlCharsDropped() = assertEquals("input text 'ab'", TextInput.command("a\u0000\u001b\u007fb"))

    @Test fun nonAsciiDropped() {
        assertEquals("input text 'caf'", TextInput.command("café"))
        assertNull(TextInput.command("😀"))
        assertNull(TextInput.command("日本語"))
        assertTrue(TextInput.unsupported("café"))
        assertTrue(TextInput.unsupported("hi 😀"))
    }

    @Test fun unsupportedFalseForPlainText() {
        assertFalse(TextInput.unsupported("Hello, world! 100%"))
        assertFalse(TextInput.unsupported(""))
    }

    @Test fun emptyIsNull() {
        assertNull(TextInput.command(""))
        assertNull(TextInput.command("\n\t"))
    }

    @Test fun spaceOnlyStillTypes() = assertEquals("input text '%s'", TextInput.command(" "))

    @Test fun capsAt200Characters() {
        val cmd = TextInput.command("a".repeat(500))!!
        assertEquals("input text '${"a".repeat(200)}'", cmd)
    }
}
