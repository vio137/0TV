package dev.tidytv.core.ops

/** What happened to one file. */
sealed interface SendResult {
    data class Sent(val remotePath: String) : SendResult
    /** The TV hasn't room for it; [freeBytes] is what's left. */
    data class NoSpace(val freeBytes: Long) : SendResult
    data object Disconnected : SendResult
}

/** Where on the TV's shared storage Send files puts things; always in a TidyTV subfolder so nothing is overwritten. */
enum class SendFolder(val dir: String, val title: String) {
    DOWNLOAD("Download", "Downloads"),
    MOVIES("Movies", "Movies"),
    MUSIC("Music", "Music"),
    PICTURES("Pictures", "Pictures"),
    DOCUMENTS("Documents", "Documents");

    val path: String get() = "/sdcard/$dir/TidyTV"

    companion object {
        fun of(name: String?): SendFolder = entries.firstOrNull { it.name == name } ?: DOWNLOAD
    }
}

/**
 * Sends files from the phone to the TV's Downloads › TidyTV folder, where the TV's own file
 * manager or media player can open them. Names are made safe and unique on the TV; free space is
 * checked first so a big video fails up front rather than halfway.
 */
class FileSender(private val tv: TvShell, private val folder: SendFolder = SendFolder.DOWNLOAD) {
    /** Bytes free on the TV's shared storage, or null when `df` can't be read. */
    suspend fun freeBytes(): Long? = Parse.dfAvailableKb(tv.run(Commands.DF_SDCARD))?.let { it * 1024 }

    /** Names already in the folder (creating it if needed), so new files never overwrite. */
    suspend fun takenNames(): Set<String> =
        tv.run(Commands.listSendDir(folder.path)).lines().map { it.trim() }.filter { it.isNotEmpty() && it != "ok" }.toSet()

    suspend fun send(
        name: String,
        size: Long,
        open: () -> java.io.InputStream,
        taken: MutableSet<String>,
        onProgress: (Long) -> Unit = {},
    ): SendResult = try {
        val free = freeBytes()
        if (free != null && size > free - RESERVE_BYTES) SendResult.NoSpace(free)
        else {
            val safe = SafeName.unique(SafeName.clean(name), taken)
            val path = "${folder.path}/$safe"
            open().use { tv.pushStream(it, size, path, onProgress) }
            taken += safe
            SendResult.Sent(path)
        }
    } catch (e: TvConnectionLost) {
        SendResult.Disconnected
    }

    private companion object {
        /** Leave the TV some breathing room: never fill it to the last 200 MB. */
        const val RESERVE_BYTES = 200L * 1024 * 1024
    }
}
