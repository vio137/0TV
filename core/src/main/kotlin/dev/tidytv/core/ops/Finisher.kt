package dev.tidytv.core.ops

data class FinishReport(val memBeforeKb: Long?, val memAfterKb: Long?) {
    /** Memory freed, rounded to whole MB. Null when either reading is missing; never negative. */
    val freedMb: Int?
        get() = if (memBeforeKb == null || memAfterKb == null) null
        else ((memAfterKb - memBeforeKb).coerceAtLeast(0) / 1024).toInt()
}

/** End of a run (tv-detox README): how much memory came back, and making sure debugging is off again. */
object Finisher {
    suspend fun measure(tv: TvShell, memBeforeKb: Long?): FinishReport =
        FinishReport(memBeforeKb, runCatching { Parse.memAvailableKb(tv.run(Commands.MEMINFO)) }.getOrNull())

    /** Steps the user follows on the TV. TidyTV can't turn debugging off for them without losing the connection first. */
    val TURN_OFF_STEPS = listOf(
        "On the TV, open Settings › System › Developer options.",
        "Turn off USB debugging and Network debugging (or Wireless debugging).",
        "Come back here and tap Check it's off.",
    )

    /** [portOpen] probes the TV's port 5555. Debugging is off when nothing answers there. */
    suspend fun debuggingOff(portOpen: suspend () -> Boolean): Boolean = !portOpen()
}
