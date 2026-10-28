package dev.tidytv.core.lists

/** The review screen's groups, in display order. */
enum class Group(val title: String, val onByDefault: Boolean) {
    ADS_TRACKING("Ads & tracking", true),
    PRELOADED("Preloaded apps", true),
    VOICE("Voice assistant", false),
    CASTING("Casting & screen mirroring", false),
    UPDATES("Firmware updates", false),
    STREAMING("Streaming apps", false),
}

data class Candidate(
    val pkg: String,
    val label: String,
    val note: String,
    val group: Group,
    val selected: Boolean,
    val optional: Boolean,
)

/**
 * Sorts a list's entries into review groups. Package names and the list's own section titles
 * decide; anything unclear falls into Preloaded apps. Home-screen apps never appear (see [Launchers]).
 */
object Grouping {
    private val STREAMING_PKGS = listOf(
        "netflix", "youtube", "amazonvideo", "primevideo", "disney", "hulu", "hotstar", "spotify",
        "twitch", "plex", "wbd.stream", "hbo", "peacock", "paramount", "tubi", "pluto", "crunchyroll",
        "dazn", "zee5", "sonyliv", "jio", "erosnow", "eros.now", "mubi", "apple.atve", "rakuten", "viki",
        "vudu", "fubo", "sling", "deezer", "tidal", "amazon.music", "google.android.videos", "tvmusic",
    )

    fun groupOf(entry: ListEntry): Group {
        val p = entry.pkg.lowercase()
        val s = (entry.section + " " + entry.note).lowercase()
        return when {
            p.containsAny("katniss", "alexa", "assistant", "voice", "speech", "vui", "micmanager") ||
                s.containsAny("voice", "assistant", "alexa", "microphone") -> Group.VOICE
            p.containsAny("mediashell", "airplay", "miracast", "dlna", "milink", "screencast", "mirror", "chromecast", ".cast") ||
                s.containsAny("cast", "mirroring", "airplay", "miracast", "dlna") -> Group.CASTING
            p.containsAny(".ota", "ota.", "update", "upgrade", "fota") ||
                s.containsAny("firmware update", "ota", "updater", "update service") -> Group.UPDATES
            STREAMING_PKGS.any { p.contains(it) } || s.contains("streaming") -> Group.STREAMING
            s.containsAny(
                "telemetry", "tracking", "analytics", "acr", " ad", "ads", "promo", "recommendation",
                "sponsored", "patchwall", "content row", "feedback", "report", "metrics", "samba",
                "alphonso", "demo", "shop", "store mode", "retail", "hotel", "data collection",
            ) || p.containsAny("analytics", "ads", "acr", "telemetry", "tracker", "report", "feedback", "demo") ->
                Group.ADS_TRACKING
            else -> Group.PRELOADED
        }
    }

    private val OWNER_CHOICE = Regex("choice|optional|not used|personal|unused|comment out", RegexOption.IGNORE_CASE)
    internal val NOT_OFFERED = Regex(
        "protected|not on this|refused|cannot|can't|keep|needed|build-and-install|not installed",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Candidates for the review screen. [installed] filters to packages actually on the TV.
     * Protected and home-screen apps are left out here and checked again right before each command.
     */
    fun candidates(
        list: DebloatList,
        installed: Set<String>,
        liveProtected: Set<String> = emptySet(),
    ): List<Candidate> {
        val active = list.entries.map { it to false }
        val extras = list.optional.filterNot { NOT_OFFERED.containsMatchIn(it.note) }.map { it to true }
        return (active + extras)
            .filter { (e, _) -> e.pkg in installed }
            .filterNot { (e, _) -> Launchers.isHomeApp(e.pkg) }
            .filterNot { (e, _) -> Protected.isProtected(e.pkg, liveProtected, list.keep) }
            .map { (e, optional) ->
                val group = groupOf(e)
                val choice = OWNER_CHOICE.containsMatchIn(e.section)
                val selected = !optional && group.onByDefault && !(choice && group != Group.ADS_TRACKING) && e.pkg !in Protected.REMOTE_ESSENTIALS
                Candidate(e.pkg, AppLabels.label(e.pkg, e.note), e.note, group, selected, optional)
            }
            .sortedWith(compareBy({ it.group.ordinal }, { it.label.lowercase() }))
    }

    /**
     * "Show more apps", for when this TV's own list finds only a few: apps that OTHER TVs' lists turn
     * off and that are installed here. All start unselected. Never offered: protected and home apps,
     * anything any list says to keep or not touch, and packages already on the review screen
     * ([offered]: the current list's candidates and everything else that list mentions).
     */
    fun moreCandidates(
        catalog: List<DebloatList>,
        installed: Set<String>,
        live: Set<String>,
        keep: Set<String>,
        offered: Set<String> = emptySet(),
    ): List<Candidate> {
        val keptAnywhere = catalog.flatMap { it.keep }.toSet() + keep
        val refused = catalog.flatMap { it.entries + it.optional }.filter { NOT_OFFERED.containsMatchIn(it.note) }.map { it.pkg }.toSet()
        return catalog.asSequence()
            .flatMap { it.entries + it.optional }
            .filter { it.pkg in installed && it.pkg !in offered && it.pkg !in refused && it.pkg !in keptAnywhere }
            .filterNot { Launchers.isHomeApp(it.pkg) }
            .filterNot { Protected.isProtected(it.pkg, live, keep) }
            .distinctBy { it.pkg }
            .map { e -> Candidate(e.pkg, AppLabels.label(e.pkg, e.note), e.note, groupOf(e), selected = false, optional = true) }
            .sortedWith(compareBy({ it.group.ordinal }, { it.label.lowercase() }))
            .toList()
    }

    private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }
}
