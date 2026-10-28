package dev.tidytv.core.ops

import java.util.UUID

/** One private still frame, never a recording. Remove the TV-side file even when pulling fails. */
object ScreenCapture {
    const val MAX_BYTES = 12 * 1024 * 1024
    suspend fun read(shell: TvShell): ByteArray? {
        val path = "/data/local/tmp/otv-screen-${UUID.randomUUID()}.png"
        try {
            shell.run("timeout 8 screencap -p $path")
            val size = shell.run("stat -c %s $path").trim().toLongOrNull() ?: return null
            if (size !in 8..MAX_BYTES.toLong()) return null
            val bytes = shell.pull(path) ?: return null
            if (bytes.size.toLong() != size || !bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) return null
            return bytes
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try { shell.run("rm -f $path") } catch (_: Exception) { /* Best effort on a lost connection. */ }
            }
        }
    }
}
