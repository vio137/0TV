package dev.tidytv.core.ops

import dev.tidytv.core.lists.PackageName

/**
 * Fetches an app's APK from the TV so the phone can read the app's real icon out of it. Only small
 * APKs (system bloat usually is) are fetched; anything bigger keeps its brand icon or initials.
 */
class ApkFetcher(private val tv: TvShell, private val maxBytes: Long = 12L * 1024 * 1024) {
    private val PATH = Regex("^/[A-Za-z0-9_./~@+=-]+\\.apk$")

    suspend fun fetch(pkg: String): ByteArray? = try {
        val base = Parse.lines(tv.run("pm path ${PackageName.require(pkg)}"))
            .firstOrNull { it.startsWith("package:") && it.endsWith("base.apk") }
            ?: Parse.lines(tv.run("pm path $pkg")).firstOrNull { it.startsWith("package:") }
        val path = base?.removePrefix("package:")?.takeIf { PATH.matches(it) }
        val size = path?.let { tv.run("stat -c %s $it").trim().toLongOrNull() }
        if (path == null || size == null || size <= 0 || size > maxBytes) null else tv.pull(path)
    } catch (e: TvConnectionLost) {
        null
    }
}
