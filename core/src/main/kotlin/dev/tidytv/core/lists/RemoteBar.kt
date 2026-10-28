package dev.tidytv.core.lists

/**
 * Which apps sit on the phone remote's shortcut bar, and in what order. With nothing saved the well-known
 * streaming apps come first; once the owner pins apps their own order wins. Apps the TV doesn't have are skipped.
 */
object RemoteBar {
    val DEFAULTS = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.google.android.youtube.tvkids",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "in.startv.hotstar",
        "com.apple.atve.androidtv.appletv",
        "com.sonyliv",
        "com.graymatrix.did",
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic",
        "com.plexapp.android",
        "org.xbmc.kodi",
    )

    private const val MIN_SHOWN = 6
    private const val MAX_SHOWN = 12

    /** The pinned apps that are on the TV, in the owner's order, or the defaults when nothing was saved. */
    fun pinned(installed: Collection<String>, saved: List<String>?): List<String> {
        val have = installed.toSet()
        return (saved ?: DEFAULTS).filter { it in have }.distinct()
    }

    /** Every app: pinned first, then the rest in the order given. */
    fun order(installed: List<String>, saved: List<String>?): List<String> {
        val first = pinned(installed, saved)
        return first + installed.filterNot { it in first }
    }

    /** What the bar shows: the pinned apps, padded to at least six with the next apps, never more than twelve. */
    fun bar(installed: List<String>, saved: List<String>?): List<String> {
        val ordered = order(installed, saved)
        val n = pinned(installed, saved).size.coerceIn(MIN_SHOWN, MAX_SHOWN)
        return ordered.take(n)
    }

    /** Pin or unpin [pkg]. The first change starts from what the bar showed, so nothing else jumps around. */
    fun toggle(installed: List<String>, saved: List<String>?, pkg: String): List<String> {
        val now = pinned(installed, saved)
        return if (pkg in now) now - pkg else now + pkg
    }

    /** Move a pinned app one place earlier (delta -1) or later (+1). */
    fun move(installed: List<String>, saved: List<String>?, pkg: String, delta: Int): List<String> {
        val now = pinned(installed, saved).toMutableList()
        val i = now.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in now.indices) return now
        now.removeAt(i); now.add(j, pkg)
        return now
    }
}
