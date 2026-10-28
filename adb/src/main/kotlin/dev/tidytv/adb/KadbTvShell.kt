package dev.tidytv.adb

import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import dev.tidytv.core.ops.TvConnectionLost
import dev.tidytv.core.ops.TvShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.source
import dev.tidytv.core.ops.ShellSession
import kotlin.concurrent.thread
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/**
 * The phone's ADB identity. The key is created once and kept in app storage, so after the user
 * ticks "Always allow" the TV never asks again.
 */
object AdbIdentity {
    @Volatile private var configured: File? = null

    @Synchronized
    fun use(keyFile: File) {
        if (configured == keyFile) return
        keyFile.parentFile?.mkdirs()
        KadbCert.configure(OkioFilePrivateKeyStore(keyFile.toOkioPath()))
        KadbCert.ensureReady()
        configured = keyFile
    }
}

/** [TvShell] over a connected Kadb session. Every I/O error becomes [TvConnectionLost]. */
class KadbTvShell(private val kadb: Kadb, private val trace: (String) -> Unit = {}) : TvShell, AutoCloseable {
    override suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        try {
            val started = System.nanoTime()
            val out = kadb.shell(command).allOutput
            trace("$ $command  (${(System.nanoTime() - started) / 1_000_000} ms)\n${out.trimEnd().take(2000)}")
            out
        } catch (e: IOException) {
            trace("$ $command  !! ${e.javaClass.simpleName}: ${e.message}")
            throw TvConnectionLost(e.message ?: "connection lost", e)
        } catch (e: IllegalStateException) {
            throw TvConnectionLost(e.message ?: "connection lost", e)
        }
    }

    override suspend fun push(bytes: ByteArray, remotePath: String) = withContext(Dispatchers.IO) {
        try {
            kadb.push(Buffer().write(bytes), remotePath, mode = "644".toInt(8), lastModifiedMs = System.currentTimeMillis())
            trace("push ${bytes.size} bytes -> $remotePath")
        } catch (e: IOException) {
            trace("push $remotePath !! ${e.message}")
            throw TvConnectionLost(e.message ?: "connection lost", e)
        }
    }

    override suspend fun pull(remotePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val sink = Buffer()
            kadb.pull(sink, remotePath)
            trace("pull $remotePath (${sink.size} bytes)")
            sink.readByteArray()
        } catch (e: IOException) {
            trace("pull $remotePath !! ${e.message}")
            null
        }
    }

    override suspend fun pushStream(source: java.io.InputStream, size: Long, remotePath: String, onProgress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            var sent = 0L
            val counting = object : okio.ForwardingSource(source.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n = super.read(sink, byteCount)
                    if (n > 0) { sent += n; onProgress(sent) }
                    return n
                }
            }
            try {
                kadb.push(counting, remotePath, mode = "644".toInt(8), lastModifiedMs = System.currentTimeMillis())
                trace("push stream $sent/$size bytes -> $remotePath")
            } catch (e: IOException) {
                trace("push stream $remotePath !! ${e.message}")
                throw TvConnectionLost(e.message ?: "connection lost", e)
            }
        }

    /**
     * A plain `shell:` stream kept open for the remote: each command is one line written to it.
     * The legacy protocol works on every TV (shell v2 doesn't exist on older ones); output is
     * read and thrown away on a daemon thread so the stream never backs up.
     */
    override suspend fun openSession(): ShellSession? = withContext(Dispatchers.IO) {
        try {
            val stream = kadb.open("shell:")
            thread(isDaemon = true, name = "tidytv-remote-drain") {
                runCatching { while (!stream.source.exhausted()) stream.source.skip(stream.source.buffer.size.coerceAtLeast(1)) }
            }
            trace("remote session open")
            object : ShellSession {
                override suspend fun send(command: String) = withContext(Dispatchers.IO) {
                    try {
                        stream.sink.writeUtf8(command).writeUtf8("\n").flush()
                    } catch (e: IOException) {
                        trace("remote session !! ${e.message}")
                        throw TvConnectionLost(e.message ?: "connection lost", e)
                    }
                }
                override fun close() { runCatching { stream.close() } }
            }
        } catch (e: Exception) {
            trace("remote session unavailable: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    val isAlive: Boolean get() = kadb.connectionCheck()

    override fun close() = runCatching { kadb.close() }.let { }
}
