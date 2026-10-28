package dev.tidytv.core.ops

/** Turns arbitrary file names into names that are safe to place in a remote path. */
object SafeName {
    private const val MAX_LEN = 100
    private const val MAX_EXT = 10

    private fun allowed(c: Char) = c.isLetterOrDigit() || c in " .-_()"

    private fun trimEdges(s: String) = s.trim { it == ' ' || it == '.' }

    /** Index of the extension dot, or -1 when there is no 1-10 character extension. */
    private fun extDot(name: String): Int {
        val i = name.lastIndexOf('.')
        return if (i > 0 && name.length - i - 1 in 1..MAX_EXT) i else -1
    }

    /**
     * Makes [name] safe to join onto a directory inside single quotes: no slashes, quotes,
     * `$`, backticks or control characters, no leading dot (so no hidden files or `..`),
     * at most 100 characters with the extension kept, and never empty.
     */
    fun clean(name: String): String {
        var s = name.map { if (allowed(it)) it else '_' }.joinToString("")
        while ("__" in s) s = s.replace("__", "_")
        s = trimEdges(s)
        if (s.length > MAX_LEN) {
            val dot = extDot(s)
            s = if (dot < 0) {
                trimEdges(s.take(MAX_LEN))
            } else {
                val ext = s.substring(dot)
                trimEdges(s.substring(0, dot).take(MAX_LEN - ext.length)) + ext
            }
        }
        return s.ifEmpty { "file" }
    }

    /**
     * Returns [clean] if free, otherwise `name (2).ext`, `name (3).ext` and so on, so two
     * files with the same name never overwrite each other on the TV.
     */
    fun unique(clean: String, taken: Set<String>): String {
        if (clean !in taken) return clean
        val dot = extDot(clean)
        val base = if (dot < 0) clean else clean.substring(0, dot)
        val ext = if (dot < 0) "" else clean.substring(dot)
        var n = 2
        while (true) {
            val c = "$base ($n)$ext"
            if (c !in taken) return c
            n++
        }
    }
}
