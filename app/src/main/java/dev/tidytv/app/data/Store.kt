package dev.tidytv.app.data

import dev.tidytv.core.lists.ListCatalog
import dev.tidytv.core.ops.ActionLog
import dev.tidytv.core.ops.Backup
import dev.tidytv.core.ops.FileActionLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A TV this phone has worked on, for the "My TVs" screen and reconnecting after an IP change. */
@Serializable
data class KnownTv(val serial: String, val title: String, val model: String, val host: String, val lastSeen: Long)

/**
 * Everything TidyTV keeps on the phone:
 *   keys/adbkey.pem                 ADB identity ("Always allow" sticks)
 *   tvs/<serial>/backup-<ts>.json   taken before any change
 *   tvs/<serial>/actions.jsonl      append-only change log, drives Restore
 *   tvs/<serial>/tv.json            name and last address
 *   logs/                           connection and command trace for Share logs
 */
class Store(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val keyFile = File(root, "keys/adbkey.pem")
    val logsDir = File(root, "logs")
    /** Private screen cache, excluded from backup along with all app data. */
    val tvPreviewsDir = File(root, "tv-previews")
    /** The update check's cache: when it last asked GitHub, what it found, what was dismissed. */
    val updateFile = File(root, "update.json")
    /** Set after the first successful connect: later "Connect a TV" taps skip the get-ready steps. */
    val onboardedFile = File(root, "onboarded")
    fun onboarded() = onboardedFile.exists()
    fun markOnboarded() { runCatching { onboardedFile.parentFile?.mkdirs(); onboardedFile.writeText("1") } }


    /** Serial numbers become folder names, so only safe characters survive. */
    private fun tvDir(serial: String) = File(root, "tvs/" + serial.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "unknown" })

    /** Real app icons read from TV APKs, one PNG per package. */
    val iconsDir: File get() = File(root, "icons").apply { mkdirs() }

    fun iconFile(pkg: String) = File(iconsDir, pkg.replace(Regex("[^A-Za-z0-9._]"), "_") + ".png")

    fun cachedIcon(pkg: String): String? = iconFile(pkg).takeIf { it.length() > 0 }?.path

    fun actionLog(serial: String): ActionLog = FileActionLog(File(tvDir(serial), "actions.jsonl"))

    /** Optional TizenTube Cobalt: its own record, so Restore Everything never touches it, and a download folder. */
    fun cobaltLog(serial: String): ActionLog = FileActionLog(File(tvDir(serial), "cobalt.jsonl"))
    fun j10Log(serial: String): ActionLog = FileActionLog(File(tvDir(serial), "j10tv.jsonl"))
    val cobaltDir: File get() = File(root, "cobalt").apply { mkdirs() }
    val j10Dir: File get() = File(root, "j10tv").apply { mkdirs() }

    fun saveBackup(backup: Backup): File {
        val f = File(tvDir(backup.serial), "backup-${backup.takenAt}.json")
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(backup.toJson())
        tmp.renameTo(f)
        return f
    }

    fun latestBackup(serial: String): Backup? =
        tvDir(serial).listFiles { f -> f.name.startsWith("backup-") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name }?.let { runCatching { Backup.fromJson(it.readText()) }.getOrNull() }

    /** Apps the owner pinned to this TV's remote bar, in order; null until they change it. */
    fun pinnedApps(serial: String): List<String>? =
        File(tvDir(serial), "pinned.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }

    fun savePinned(serial: String, pkgs: List<String>) {
        val f = File(tvDir(serial), "pinned.txt")
        f.parentFile?.mkdirs()
        f.writeText(pkgs.joinToString("\n"))
    }

    /** Where Send files puts things on this TV; Downloads until the owner picks another folder. */
    fun sendFolder(serial: String): String? = File(tvDir(serial), "sendfolder.txt").takeIf { it.exists() }?.readText()?.trim()

    fun saveSendFolder(serial: String, name: String) {
        val f = File(tvDir(serial), "sendfolder.txt")
        f.parentFile?.mkdirs()
        f.writeText(name)
    }

    fun remember(tv: KnownTv) {
        val f = File(tvDir(tv.serial), "tv.json")
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(KnownTv.serializer(), tv))
    }

    fun knownTvs(): List<KnownTv> =
        File(root, "tvs").listFiles()?.mapNotNull { d ->
            File(d, "tv.json").takeIf { it.exists() }?.let { runCatching { json.decodeFromString(KnownTv.serializer(), it.readText()) }.getOrNull() }
        }?.sortedByDescending { it.lastSeen }.orEmpty()

    companion object {
        /** The bundled upstream lists (assets/lists/<id>.txt, copied from the submodule at build time). */
        fun loadLists(assets: android.content.res.AssetManager): ListCatalog {
            val names = assets.list("lists").orEmpty().filter { it.endsWith(".txt") }
            return ListCatalog(names.associate { it.removeSuffix(".txt") to assets.open("lists/$it").bufferedReader().readText() })
        }
    }
}
