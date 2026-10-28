package dev.tidytv.core.ops

import dev.tidytv.core.lists.TvInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val takenAt: Long,
    val serial: String,
    val model: String,
    val sdk: Int,
    val props: Map<String, String>,
    val packages: List<String>,
    val disabled: List<String>,
    val home: String?,
    val settings: Map<String, String>,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }
        fun fromJson(text: String): Backup = JSON.decodeFromString(serializer(), text)
    }
}

/** Everything the TV says about itself right after connecting, read once. */
data class TvSnapshot(
    val info: TvInfo,
    val props: Map<String, String>,
    val installed: Set<String>,
    val disabled: Set<String>,
    val home: String?,
    /** IMEs, accessibility services and TV-input owners: never disabled. */
    val liveProtected: Set<String>,
    val memAvailableKb: Long?,
)

object TvReader {
    suspend fun snapshot(tv: TvShell): TvSnapshot {
        val props = Parse.props(tv.run(Commands.GETPROP))
        if (props.isEmpty()) throw UnexpectedOutput(Commands.GETPROP, "no properties")
        val owners = Parse.ownerPackages(
            tv.run(Commands.ENABLED_IMES),
            tv.run(Commands.DEFAULT_IME),
            tv.run(Commands.A11Y_SERVICES),
            tv.run(Commands.TV_INPUTS),
        )
        return TvSnapshot(
            info = Parse.tvInfo(props),
            props = props,
            installed = Parse.packages(tv.run(Commands.LIST_PACKAGES)),
            disabled = Parse.packages(tv.run(Commands.LIST_DISABLED)),
            home = Parse.component(tv.run(Commands.RESOLVE_HOME)),
            liveProtected = owners,
            memAvailableKb = Parse.memAvailableKb(tv.run(Commands.MEMINFO)),
        )
    }

    /** Mandatory before any change. Settings are kept as the raw `settings list` lines per namespace. */
    suspend fun backup(tv: TvShell, snap: TvSnapshot, now: Long): Backup {
        val settings = listOf("global", "secure", "system").associateWith { tv.run(Commands.settingsList(it)) }
        return Backup(
            takenAt = now,
            serial = snap.info.serial,
            model = snap.info.displayName,
            sdk = snap.info.sdk,
            props = snap.props,
            packages = snap.installed.sorted(),
            disabled = snap.disabled.sorted(),
            home = snap.home,
            settings = settings,
        )
    }
}
