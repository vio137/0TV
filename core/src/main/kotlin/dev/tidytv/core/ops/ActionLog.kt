package dev.tidytv.core.ops

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * REMOVE is "uninstall for the TV's user" (`pm uninstall -k --user 0`): a preinstalled app's APK stays
 * on the system partition and REINSTALL (`cmd package install-existing`) brings it back. For an app
 * the owner installed, REMOVE is a normal uninstall and can't be undone (detail [OWNER_APP]).
 */
enum class Op { DISABLE, ENABLE, INSTALL, UNINSTALL, SET_HOME, SETTING, REMOVE, REINSTALL, TV_BIND }

/** [ActionRecord.detail] of a REMOVE on an app the owner installed: gone for good, not restorable. */
const val OWNER_APP = "owner app"

/** INTENT is written before a change, DONE or FAILED after. An INTENT with no result means "maybe done". */
enum class Phase { INTENT, DONE, FAILED }

@Serializable
data class ActionRecord(
    val ts: Long,
    val op: Op,
    val target: String,
    val phase: Phase,
    /** Value before the change: previous Home component, previous setting value. */
    val before: String? = null,
    val detail: String? = null,
)

/** Append-only record of every change TidyTV makes to one TV. Restore is driven from this. */
interface ActionLog {
    fun append(record: ActionRecord)
    fun records(): List<ActionRecord>
}

class MemoryActionLog : ActionLog {
    private val items = mutableListOf<ActionRecord>()
    override fun append(record: ActionRecord) { synchronized(items) { items += record } }
    override fun records(): List<ActionRecord> = synchronized(items) { items.toList() }
}

/**
 * One JSON object per line, fsync'd after every write so a crash or a dead battery cannot lose a
 * change that was already sent to the TV. A torn last line (crash mid-write) is skipped on read.
 */
class FileActionLog(private val file: File) : ActionLog {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun append(record: ActionRecord) {
        file.parentFile?.mkdirs()
        // After a torn write the file ends mid-line; start a fresh line so this record survives.
        val needsNewline = file.length() > 0 && java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - 1); raf.read() != '\n'.code
        }
        FileOutputStream(file, true).use { out ->
            if (needsNewline) out.write('\n'.code)
            out.write((json.encodeToString(ActionRecord.serializer(), record) + "\n").toByteArray())
            out.fd.sync()
        }
    }

    @Synchronized
    override fun records(): List<ActionRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching { json.decodeFromString(ActionRecord.serializer(), line) }.getOrNull()
        }
    }
}

/**
 * Packages TidyTV turned off and has not turned back on. A DISABLE intent counts even without a
 * result: the command may have reached the TV before the connection dropped, and enabling a
 * package that was never disabled is harmless.
 */
fun List<ActionRecord>.disabledByUs(): Set<String> {
    val state = linkedMapOf<String, Boolean>()
    val confirmed = mutableMapOf<String, Boolean>()
    for (r in this) {
        when {
            r.op == Op.DISABLE && r.phase == Phase.INTENT -> state[r.target] = true
            r.op == Op.DISABLE && r.phase == Phase.DONE -> { state[r.target] = true; confirmed[r.target] = true }
            r.op == Op.DISABLE && r.phase == Phase.FAILED -> state[r.target] = confirmed[r.target] ?: false
            r.op == Op.ENABLE && r.phase == Phase.DONE -> { state[r.target] = false; confirmed[r.target] = false }
        }
    }
    return state.filterValues { it }.keys
}

/**
 * Preinstalled apps TidyTV removed for the TV's user and hasn't brought back. Like [disabledByUs], an
 * INTENT counts: the uninstall may have reached the TV, and install-existing on an app that is still
 * there is harmless. Apps the owner installed are left out: they can't come back ([ownerAppsRemoved]).
 */
fun List<ActionRecord>.removedByUs(): Set<String> {
    val state = linkedMapOf<String, Boolean>()
    val confirmed = mutableMapOf<String, Boolean>()
    for (r in this) when {
        r.op == Op.REMOVE && r.detail == OWNER_APP -> state.remove(r.target)
        r.op == Op.REMOVE && r.phase == Phase.INTENT -> state[r.target] = true
        r.op == Op.REMOVE && r.phase == Phase.DONE -> { state[r.target] = true; confirmed[r.target] = true }
        r.op == Op.REMOVE && r.phase == Phase.FAILED -> state[r.target] = confirmed[r.target] ?: false
        r.op == Op.REINSTALL && r.phase == Phase.DONE -> { state[r.target] = false; confirmed[r.target] = false }
    }
    return state.filterValues { it }.keys
}

/** Everything TidyTV took off the TV and can still put back: turned off or removed for the user. */
fun List<ActionRecord>.offByUs(): Set<String> = disabledByUs() + removedByUs()

/** Apps the owner installed that TidyTV uninstalled at their request: gone for good. */
fun List<ActionRecord>.ownerAppsRemoved(): Set<String> =
    filter { it.op == Op.REMOVE && it.detail == OWNER_APP && it.phase == Phase.DONE }.map { it.target }.toSet()

/** REMOVE intents with no result yet. */
fun List<ActionRecord>.pendingRemoves(): Set<String> {
    val pending = linkedSetOf<String>()
    for (r in this) if (r.op == Op.REMOVE) {
        if (r.phase == Phase.INTENT) pending += r.target else pending -= r.target
    }
    return pending
}

/** DISABLE intents with no result yet: the command may or may not have reached the TV. */
fun List<ActionRecord>.pendingDisables(): Set<String> {
    val pending = linkedSetOf<String>()
    for (r in this) if (r.op == Op.DISABLE) {
        if (r.phase == Phase.INTENT) pending += r.target else pending -= r.target
    }
    return pending
}

/** The Home component before TidyTV first changed it, or null if Home was never touched. */
fun List<ActionRecord>.originalHome(): String? =
    firstOrNull { it.op == Op.SET_HOME && it.phase != Phase.FAILED && it.before != null }?.before

enum class Ownership { OURS, MAYBE_OURS, NOT_OURS }

/**
 * Opaque token for this TV and Android user 0, from `settings --user 0 get secure android_id`, only while user 0 is the foreground user; null
 * when unreadable or implausible. It tells two boxes apart in practice; it is not a proven-unique hardware ID.
 */
fun tvToken(androidIdOut: String, user: Int = 0): String? {
    val id = androidIdOut.trim().lowercase()
    if (!Regex("[0-9a-f]{16,32}").matches(id)) return null          // null, empty, "unknown", errors: all fail closed
    if (id.toSet().size < 4) return null                            // 0000..., ffff..., repeated patterns
    if (id == "9774d56d682e549c") return null                       // the well-known shared default Android ID
    val d = java.security.MessageDigest.getInstance("SHA-256").digest("u$user:$id".toByteArray())
    return d.take(12).joinToString("") { "%02x".format(it) }        // 96 bits: collisions are not a practical concern
}

private fun ActionRecord.detailField(key: String): String? =
    detail?.split(';')?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

/**
 * Who owns [pkg] on the TV identified by [tv] (see [tvToken]). Only records written for that exact TV count; a
 * missing or different TV token means NOT_OURS. OURS: an install we completed and have not undone. MAYBE_OURS:
 * an attempt with a proven-absent prestate and no confirmed outcome (app died, or the reply was lost after the TV
 * succeeded); Restore may remove it only after checking the live package list. Everything else, including a
 * missing, corrupt or unknown prestate, is NOT_OURS and never authorises an uninstall.
 */
fun List<ActionRecord>.launcherOwnership(pkg: String, tv: String?): Ownership {
    if (tv == null) return Ownership.NOT_OURS
    var done = false
    var maybe = false
    for (r in this) {
        if (r.target != pkg) continue
        if (r.op == Op.UNINSTALL && r.phase == Phase.DONE) { done = false; maybe = false; continue }
        if (r.op != Op.INSTALL || r.detailField("tv") != tv) continue
        when (r.phase) {
            Phase.INTENT -> maybe = r.detailField("prestate").let { it == "absent" || it == "ours" }
            Phase.DONE -> { done = true; maybe = false }
            Phase.FAILED -> {}   // a failed command can still follow a remote success: keep what the intent proved
        }
    }
    return if (done) Ownership.OURS else if (maybe) Ownership.MAYBE_OURS else Ownership.NOT_OURS
}

/** True only for a confirmed install of ours on this exact TV. */
fun List<ActionRecord>.launcherInstalledByUs(pkg: String, tv: String?) = launcherOwnership(pkg, tv) == Ownership.OURS

/** Settings changed by TidyTV: key "namespace/name" -> value before the first change. */
fun List<ActionRecord>.changedSettings(): Map<String, String?> {
    val result = linkedMapOf<String, String?>()
    for (r in this) if (r.op == Op.SETTING && r.phase != Phase.FAILED && r.target !in result) result[r.target] = r.before
    return result
}


/**
 * Ties one action log to one TV. The first change writes a TV_BIND record with the TV's [tvToken]; every later
 * change and every recovery re-reads the token and refuses on a mismatch, an unreadable identity, or a log that
 * has records but no binding. This tells boxes apart in practice; the Android ID is not a proven-unique hardware ID.
 */
object TvBinding {
    /** Every change TidyTV makes targets Android user 0 (`pm ... --user 0`), so the binding names that user explicitly. */
    private const val USER = 0
    const val CURRENT_USER_CMD = "am get-current-user"
    const val ID_CMD = "settings --user $USER get secure android_id"

    /** Null (fail closed) when the foreground user is not [USER] or cannot be read, or the ID is implausible. */
    suspend fun token(tv: TvShell): String? = try {
        if (tv.run(CURRENT_USER_CMD).trim().toIntOrNull() != USER) null else tvToken(tv.run(ID_CMD), USER)
    } catch (e: TvConnectionLost) { throw e } catch (e: Exception) { null }

    /** Before a change: writes the binding on a fresh log, true only when this log belongs to this exact TV. */
    suspend fun ensure(tv: TvShell, log: ActionLog, clock: () -> Long = System::currentTimeMillis): Boolean {
        val now = token(tv) ?: return false
        val bound = log.records().filter { it.op == Op.TV_BIND && it.phase == Phase.DONE }
        if (bound.isEmpty()) {
            if (log.records().isNotEmpty()) return false     // changes recorded with no TV on file: unknown owner
            log.append(ActionRecord(clock(), Op.TV_BIND, "tv", Phase.DONE, detail = "tv=$now"))
            return true
        }
        return bound.all { it.detail == "tv=$now" }
    }

    /** Before a recovery: true when there is nothing recorded yet, or the log is bound to this exact TV. */
    suspend fun verify(tv: TvShell, log: ActionLog): Boolean {
        val records = log.records()
        if (records.isEmpty()) return true
        val now = token(tv) ?: return false
        val bound = records.filter { it.op == Op.TV_BIND && it.phase == Phase.DONE }
        return bound.isNotEmpty() && bound.all { it.detail == "tv=$now" }
    }
}

class WrongTvException : IllegalStateException("This record belongs to a different TV. Nothing was changed.")
