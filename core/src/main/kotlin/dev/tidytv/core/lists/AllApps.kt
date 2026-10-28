package dev.tidytv.core.lists

import dev.tidytv.core.ops.AppFacts

/** The All apps view's sections, in display order. */
enum class Tier(val title: String) {
    RECOMMENDED("Recommended"),
    YOUR_CALL("Your call"),
    OTHER_TVS("Seen on other TVs"),
    YOURS("Apps you installed"),
    UNLISTED("Not on any list"),
    LOCKED("Kept safe"),
}

/** Where an app came from. */
enum class Origin(val title: String) { YOURS("Apps you installed"), PREINSTALLED("Preinstalled") }

/** One installed app in the All apps view. [note] is the one-line trade-off or reason. */
data class AppRow(
    val pkg: String,
    val label: String,
    val tier: Tier,
    val note: String,
    val selected: Boolean,
    val group: Group,
) {
    val locked get() = tier == Tier.LOCKED
    /** Where the app came from: the owner installed it, or it shipped with the TV. */
    val origin get() = if (tier == Tier.YOURS) Origin.YOURS else Origin.PREINSTALLED
    /** Can the owner remove it? Everything but the locked apps; unlisted ones carry a "look it up" note. */
    val removable get() = tier != Tier.LOCKED
    /** True when this TV's tested list keeps the app on and the owner can still choose to turn it off. */
    val keptByList get() = tier == Tier.YOUR_CALL && note.startsWith(KEPT_PREFIX)

    companion object {
        internal const val KEPT_PREFIX = "This TV's list keeps it on. "
    }
}

/**
 * Every enabled app on the TV, each in exactly one [Tier]. The rules match [Grouping] and [Protected],
 * so nothing here can be offered that the clean-up would refuse:
 * - Home apps and Android's core / TV input / remote / keyboard / in-use apps are [Tier.LOCKED].
 * - This TV's list: ticked entries are [Tier.RECOMMENDED], the rest [Tier.YOUR_CALL].
 * - Apps this TV's list keeps on but other TVs' lists turn off (the voice assistant, Chromecast, TCL
 *   ttvs...) are [Tier.YOUR_CALL], with the trade-off. Kept apps no list ever turns off stay locked.
 * - Apps other TVs' lists turn off are [Tier.OTHER_TVS]; apps the owner installed are [Tier.YOURS];
 *   anything else preinstalled is [Tier.UNLISTED]. None of these start ticked.
 */
object AllApps {
    /** What turning off an app the list keeps actually costs, in one line. */
    val TRADE_OFFS: Map<String, String> = mapOf(
        "com.google.android.katniss" to "The remote's mic button stops working.",
        "com.google.android.tv.assistant" to "The remote's mic button stops working.",
        "com.google.android.tv.remote.service" to "The remote's mic and the Google TV phone remote stop working.",
        "com.google.android.apps.mediashell" to "Casting to this TV stops working.",
        "com.tcl.ttvs" to "Stops TCL tracking, but TCL firmware updates stop too.",
        "com.google.android.youtube.tvmusic" to "YouTube Music won't open.",
        "com.google.android.apps.nbu.smartconnect.tv" to "Sharing from a phone to the TV stops.",
        "com.tcl.keyhelp" to "Some remote buttons may stop opening their apps.",
        "com.tcl.micmanager" to "The remote's mic may stop working.",
        "com.tcl.suspension" to "TCL's floating shortcut button goes away.",
        "com.google.android.videos" to "The Google TV store and its Live TV rows go away.",
    )

    fun build(
        list: DebloatList?,
        catalog: List<DebloatList>,
        installed: Set<String>,
        disabled: Set<String>,
        yours: Set<String>,
        live: Set<String>,
    ): List<AppRow> {
        val on = installed - disabled
        val offered = list?.let { Grouping.candidates(it, on, live).associateBy { c -> c.pkg } }.orEmpty()
        val entries = catalog.flatMap { l -> (l.entries + l.optional).map { l.id to it } }
        val listedBy = entries.filterNot { (_, e) -> Grouping.NOT_OFFERED.containsMatchIn(e.note) }.groupBy({ it.second.pkg }, { it.second })
        val refused = entries.filter { (_, e) -> Grouping.NOT_OFFERED.containsMatchIn(e.note) }.map { it.second.pkg }.toSet()
        val keptElsewhere = catalog.filter { it.id != list?.id }.flatMap { it.keep }.toSet()

        return on.sorted().map { pkg ->
            val known = listedBy[pkg]?.firstOrNull()
            val note = known?.note.orEmpty()
            val label = AppLabels.label(pkg, note)
            val group = known?.let { Grouping.groupOf(it) } ?: Group.PRELOADED
            fun row(tier: Tier, why: String, selected: Boolean = false) = AppRow(pkg, label, tier, why, selected, group)
            val core = Protected.reason(pkg, live)
            val cand = offered[pkg]
            when {
                Launchers.isHomeApp(pkg) -> row(Tier.LOCKED, "Your TV's home screen. Change it with Home screen.")
                core != null -> row(Tier.LOCKED, core.replaceFirstChar { it.uppercase() } + ".")
                pkg in yours -> row(Tier.YOURS, "Uninstalling is permanent. Get it again from Google Play.")
                cand != null -> AppRow(pkg, cand.label, if (cand.selected) Tier.RECOMMENDED else Tier.YOUR_CALL, TRADE_OFFS[pkg]?.takeIf { pkg in Protected.REMOTE_ESSENTIALS } ?: cand.note, cand.selected, cand.group)
                list != null && pkg in list.keep ->
                    if (listedBy.containsKey(pkg)) row(Tier.YOUR_CALL, AppRow.KEPT_PREFIX + (TRADE_OFFS[pkg] ?: "Other TVs turn it off; only do it if you don't use it."))
                    else row(Tier.LOCKED, "This TV's tested list keeps it on.")
                pkg in refused && !listedBy.containsKey(pkg) -> row(Tier.LOCKED, "Other TVs' lists say to leave it alone.")
                listedBy.containsKey(pkg) -> row(Tier.OTHER_TVS,
                    if (pkg in keptElsewhere) "Some TVs need this. Turned off on ${listedBy[pkg]!!.size} others." else note.ifBlank { "Turned off on ${listedBy[pkg]!!.size} other TVs." })
                else -> row(Tier.UNLISTED, "Not on any tested list. Look it up before you turn it off.")
            }
        }.sortedWith(compareBy({ it.tier.ordinal }, { it.label.lowercase() }))
    }
}

/** One section of the All apps view: what can be removed first, the rest behind "Show N more". */
data class AppSection(val origin: Origin, val removable: List<AppRow>, val more: List<AppRow>) {
    val title get() = origin.title
    val size get() = removable.size + more.size
    val all get() = removable + more
}

/** Turns [AllApps.build]'s rows into the two sections the screen shows, plus the savings summary and search. */
object AllAppsView {
    /** What picking [selected] frees: RAM now, and storage (app + data + cache). MB. */
    data class Savings(val ramMb: Int, val storageMb: Int)

    private fun storage(f: AppFacts?) = f?.let { it.appMb + it.dataMb + it.cacheMb } ?: 0

    /** "Apps you installed" first, then "Preinstalled". Empty sections are left out. */
    fun sections(rows: List<AppRow>, facts: Map<String, AppFacts>): List<AppSection> =
        Origin.entries.mapNotNull { origin ->
            val mine = rows.filter { it.origin == origin }
            if (mine.isEmpty()) return@mapNotNull null
            val removable = mine.filter { it.removable && it.tier != Tier.UNLISTED }
            val more = mine.filter { !it.removable || it.tier == Tier.UNLISTED }
            val byTier = compareBy<AppRow> { it.tier.ordinal }
            val bySaving = compareByDescending<AppRow> { facts[it.pkg]?.ramMb ?: 0 }
                .thenByDescending { storage(facts[it.pkg]) }.thenBy { it.label.lowercase() }
            AppSection(
                origin,
                if (origin == Origin.YOURS) removable.sortedWith(compareByDescending<AppRow> { storage(facts[it.pkg]) }.thenBy { it.label.lowercase() })
                else removable.sortedWith(byTier.then(bySaving)),
                more.sortedWith(byTier.then(bySaving)),
            )
        }

    fun savings(selected: Collection<String>, facts: Map<String, AppFacts>): Savings {
        val picked = selected.toSet().mapNotNull { facts[it] }
        return Savings(picked.sumOf { it.ramMb }, picked.sumOf { it.appMb + it.dataMb + it.cacheMb })
    }

    /** Case-insensitive match on the name or the package. A blank query keeps everything. */
    fun search(rows: List<AppRow>, query: String): List<AppRow> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) rows else rows.filter { q in it.label.lowercase() || q in it.pkg.lowercase() }
    }
}
