package dev.tidytv.core.ops

/** What one app costs the TV right now. Sizes in MB. */
data class AppFacts(
    val ramMb: Int = 0,
    val startsAtBoot: Boolean = false,
    val appMb: Int = 0,
    val dataMb: Int = 0,
    val cacheMb: Int = 0,
    /** Installed by the owner, not preinstalled: uninstalling it can't be undone. */
    val yours: Boolean = false,
)

/** The TV's storage: free and total of /data, and how much of it is app caches. MB. */
data class StorageFacts(val freeMb: Int?, val totalMb: Int?, val cacheMb: Int)

data class TvFactsReport(val apps: Map<String, AppFacts>, val storage: StorageFacts) {
    fun of(pkg: String) = apps[pkg] ?: AppFacts()
}

/**
 * Reads, in four commands, what the All apps view shows next to each app: memory in use now
 * (`dumpsys meminfo`), whether it starts at boot (BOOT_COMPLETED receivers), storage
 * (`dumpsys diskstats`) and whether the owner installed it (`pm list packages -3`). Read-only.
 * Any part that fails is left empty rather than failing the whole view.
 */
object TvFacts {
    suspend fun read(tv: TvShell): TvFactsReport {
        val yours = runCatching { Parse.packages(tv.run(Commands.LIST_USER_APPS)) }.getOrDefault(emptySet())
        val ram = runCatching { Parse.pssMbByPackage(tv.run(Commands.MEMINFO_PROCS)) }.getOrDefault(emptyMap())
        val boot = runCatching { Parse.componentsIn(tv.run(Commands.BOOT_RECEIVERS)) }.getOrDefault(emptySet())
        val disk = runCatching { Parse.diskstats(tv.run(Commands.DISKSTATS)) }.getOrNull()
        val mb = { b: Long -> ((b + 524_288) / 1_048_576).toInt() }
        val pkgs = yours + ram.keys + boot + disk?.apps?.keys.orEmpty()
        val apps = pkgs.associateWith { p ->
            val d = disk?.apps?.get(p)
            AppFacts(
                ramMb = ram[p] ?: 0,
                startsAtBoot = p in boot,
                appMb = d?.let { mb(it.app) } ?: 0,
                dataMb = d?.let { mb(it.data) } ?: 0,
                cacheMb = d?.let { mb(it.cache) } ?: 0,
                yours = p in yours,
            )
        }
        val cacheMb = mb(disk?.apps?.values?.sumOf { it.cache } ?: 0)
        return TvFactsReport(apps, StorageFacts(disk?.freeKb?.let { (it / 1024).toInt() }, disk?.totalKb?.let { (it / 1024).toInt() }, cacheMb))
    }

    /** What a cache clear did. Sizes in MB. [finished] is false when the TV was still working at the time limit. */
    data class CacheResult(val freedMb: Int, val freeBeforeMb: Int?, val freeAfterMb: Int?, val finished: Boolean)

    /**
     * "Free up space": asks Android to drop every app's cache (`pm trim-caches`), which is always safe because
     * apps rebuild their caches. It runs in the background on the TV while the phone polls the TV's *live* free
     * space (`df /data`), reporting [onProgress] with what's been freed so far. The per-app cache numbers from
     * `dumpsys diskstats` are NOT used to measure this: Android refreshes them about once a day, so they don't
     * move after a clear. Ends when the TV says it's done, or after [maxWaitMs].
     */
    suspend fun freeCaches(
        tv: TvShell,
        onProgress: (freedMb: Int) -> Unit = {},
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        pollMs: Long = 1_500,
        maxWaitMs: Long = 240_000,
    ): CacheResult {
        suspend fun freeKb() = Parse.dfAvailableKb(tv.run(Commands.DF_DATA))
        val before = freeKb()
        tv.run(Commands.TRIM_CACHES_BACKGROUND)
        var waited = 0L
        var finished = false
        var latest = before
        while (waited < maxWaitMs) {
            wait(pollMs)
            waited += pollMs
            latest = freeKb() ?: latest
            if (before != null && latest != null) onProgress(((latest - before) / 1024).toInt().coerceAtLeast(0))
            if (Parse.lines(tv.run(Commands.TRIM_STATUS)).contains("done")) { finished = true; break }
        }
        if (finished) latest = freeKb() ?: latest
        val freed = if (before != null && latest != null) ((latest - before) / 1024).toInt().coerceAtLeast(0) else 0
        if (finished) onProgress(freed)
        return CacheResult(freed, before?.let { (it / 1024).toInt() }, latest?.let { (it / 1024).toInt() }, finished)
    }
}
