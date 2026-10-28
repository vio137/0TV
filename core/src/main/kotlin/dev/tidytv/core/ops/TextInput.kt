package dev.tidytv.core.ops

/** Builds the `input text` command used to type on the TV from the phone. */
object TextInput {
    private const val MAX_CHARS = 200

    private fun typable(c: Char) = c.code in 0x20..0x7E

    /**
     * Returns a shell command that types [text] on the TV, or null when nothing typable is left.
     * The command runs inside `sh -c` on the TV, so the argument is single-quoted and every
     * single quote is escaped; nothing the user types can start another command. Spaces become
     * `%s` and a literal percent becomes `\%` because that is how `input text` reads them.
     * Control characters and non-ASCII characters are dropped, since `input text` can't type them.
     */
    fun command(text: String): String? {
        val kept = text.filter(::typable).take(MAX_CHARS)
        if (kept.isEmpty()) return null
        val arg = buildString {
            for (c in kept) when (c) {
                ' ' -> append("%s")
                '%' -> append("\\%")
                '\'' -> append("'\\''")
                else -> append(c)
            }
        }
        return "input text '$arg'"
    }

    /** True when some character in [text] can't be typed and was left out, so the UI can warn. */
    fun unsupported(text: String): Boolean = text.any { !typable(it) }
}
