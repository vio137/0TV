package dev.tidytv.core.ops

import java.io.IOException

/**
 * The only way TidyTV talks to a TV. The real one is the adb module's Kadb connection; tests use
 * FakeTv. Output is stdout and stderr together: legacy adb shells have no exit codes, so every
 * check in core parses output, as upstream's scripts do.
 */
interface TvShell {
    /** Runs one shell command. Throws [TvConnectionLost] when the TV stops answering. */
    suspend fun run(command: String): String

    /** Writes a file on the TV (ADB sync). Throws [TvConnectionLost] when the TV stops answering. */
    suspend fun push(bytes: ByteArray, remotePath: String)

    /** Reads a file from the TV (ADB sync); null when this shell can't. Used only for app icons. */
    suspend fun pull(remotePath: String): ByteArray? = null

    /**
     * Streams a file to the TV without holding it in memory, reporting bytes sent so far.
     * The default reads it all and uses [push]; the Kadb shell streams for real.
     */
    suspend fun pushStream(source: java.io.InputStream, size: Long, remotePath: String, onProgress: (Long) -> Unit = {}) {
        val bytes = source.readBytes()
        push(bytes, remotePath)
        onProgress(bytes.size.toLong())
    }

    /**
     * A long-lived shell for quick one-way commands such as remote key presses: each command is
     * one write instead of a new shell. Null when this shell can't keep one open; callers then
     * fall back to [run].
     */
    suspend fun openSession(): ShellSession? = null
}

/** See [TvShell.openSession]. Output is discarded; [send] throws [TvConnectionLost] when the TV stops answering. */
interface ShellSession : AutoCloseable {
    suspend fun send(command: String)
    override fun close()
}

class TvConnectionLost(message: String, cause: Throwable? = null) : IOException(message, cause)
