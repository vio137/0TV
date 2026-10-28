package dev.tidytv.app.data

/**
 * Fits the exported log into the feedback message. The summary (version, TV, list, step, last problem) and the last
 * actions come first and always survive; the rest of the room goes to the end of the trace, where the failure is.
 * Cutting from the front instead lost the summary, which is what made reports hard to use.
 */
object LogTrim {
    const val TRACE_MARK = "===== trace"
    private const val HEAD_MAX = 2_200
    private const val ACTION_LINES = 40

    fun fit(log: String, max: Int): String {
        if (log.length <= max) return log
        val i = log.indexOf("\n$TRACE_MARK")
        if (i < 0) return log.takeLast(max)
        val head = shrinkHead(log.substring(0, i))
        val room = (max - head.length - 1).coerceAtLeast(0)
        return head + "\n" + log.substring(i + 1).takeLast(room)
    }

    private fun shrinkHead(head: String): String {
        val lines = head.lines()
        val sectionAt = lines.indexOfFirst { it.startsWith("===== ") }
        if (sectionAt < 0) return head.take(HEAD_MAX)
        val summary = lines.take(sectionAt)
        val rest = lines.drop(sectionAt)
        // Keep each section's title and only the last lines of the actions.
        val kept = buildList {
            add(rest.first())
            addAll(rest.drop(1).takeLast(ACTION_LINES))
        }
        return (summary + kept).joinToString("\n").takeLast(HEAD_MAX).let { if (it.length < HEAD_MAX) it else it.substringAfter('\n') }
    }
}
