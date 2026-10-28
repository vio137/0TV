package dev.tidytv.core.lists

/** What the TV says about itself (from getprop). */
data class TvInfo(
    val model: String,
    val device: String,
    val product: String,
    val brand: String,
    val manufacturer: String,
    val sdk: Int,
    val release: String,
    val serial: String,
) {
    val displayName: String
        get() = listOf(brand.replaceFirstChar { it.uppercase() }, model)
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }.joinToString(" ")
}

data class ListMatch(val list: DebloatList, val score: Int, val overlap: Int = 0) {
    /** Share of the list's apps (not counting optional ones) that are on this TV: the closer the firmware, the higher. */
    val coverage: Double get() = overlap.toDouble() / maxOf(1, list.entries.size)

    /** Tested on this model (codename or model number found in the list's title). */
    val exact: Boolean get() = score >= ModelMatcher.EXACT
}

/**
 * Picks the upstream list for a TV. A list's first line names the TV, its codenames and the
 * Android version, e.g. "TCL Smart TV Pro, US (device G05, product G05_4K_US, ...)". Same brand
 * with a similar Android version is offered as "similar model, not tested" and never as exact;
 * among those, the list that covers most of the apps installed on this TV comes first, then the one
 * with the fewest apps this TV doesn't have.
 */
object ModelMatcher {
    const val EXACT = 90
    private const val SIMILAR = 20

    /**
     * Model names that many brands' TVs report, e.g. TCL Google TVs say just "Smart TV". They never make a
     * match on their own: "Smart TV" once matched a Fire TV list whose title says "smart TV".
     */
    private val GENERIC_MODELS = setOf(
        "tv", "smarttv", "smarttvpro", "androidtv", "googletv", "ledtv", "uhdtv", "hdtv", "4ktv", "atv", "tvbox", "box",
    )

    /**
     * Lists TidyTV can't apply, so they're never offered. tivo-stream-4k: the firmware refuses every
     * `pm disable-user` (its list suspends apps instead) and uninstalls other home screens at each boot.
     */
    val NOT_FOR_TIDYTV = setOf("tivo-stream-4k")

    fun match(tv: TvInfo, lists: List<DebloatList>, limit: Int = 3, installed: Set<String> = emptySet()): List<ListMatch> =
        lists.filter { it.id !in NOT_FOR_TIDYTV }.map { l -> ListMatch(l, score(tv, l), overlap = l.entries.count { it.pkg in installed }) }
            .filter { it.score >= SIMILAR }
            .sortedWith(compareByDescending<ListMatch> { it.score }.thenByDescending { it.overlap }.thenByDescending { it.coverage }.thenBy { it.list.id })
            .take(limit)

    internal fun score(tv: TvInfo, list: DebloatList): Int {
        val titleTokens = tokens(list.title) + tokens(list.id.replace('-', ' '))
        val compactTitle = compact(list.title + " " + list.id)
        var score = 0

        val codenames = listOf(tv.device, tv.product).map { it.lowercase() }.filter { it.length >= 3 }
        if (codenames.any { it in titleTokens }) score = maxOf(score, 100)

        val brands = listOf(tv.brand, tv.manufacturer).map { it.lowercase() }.filter { it.length >= 2 }
        val brandHit = brands.any { b -> b in titleTokens || list.id.startsWith("$b-") }

        // A model name counts when it is specific: not a generic one, and a model number or the same brand.
        val model = compact(tv.model)
        val specific = model.length >= 4 && model !in GENERIC_MODELS && (brandHit || model.any { it.isDigit() })
        if (specific && (model in titleTokens || compactTitle.contains(model))) score = maxOf(score, 95)
        if (brandHit) {
            var similar = 40
            val major = tv.release.substringBefore('.')
            if (major.isNotEmpty() && Regex("\\b(android|google) tv $major\\b|\\bandroid $major\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(list.title + " " + list.header.getOrNull(1).orEmpty())
            ) similar += 20
            score = maxOf(score, similar)
        }
        return score
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length >= 3 }.toSet()

    private fun compact(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
