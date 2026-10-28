package dev.tidytv.adb

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** What one connection attempt ended with. */
sealed interface ConnectResult {
    data class Connected(val shell: KadbTvShell) : ConnectResult
    /** Nothing listens on the port: debugging is off, or this TV only offers Wireless debugging. */
    data object PortClosed : ConnectResult
    /** No answer at all: wrong address, TV off, or another network. */
    data object Unreachable : ConnectResult
    /** The TV closed the connection while asking "Allow debugging?": the user pressed Deny. */
    data object Denied : ConnectResult
    /** Nobody pressed Allow in time. */
    data object AllowTimedOut : ConnectResult
    data class Failed(val message: String) : ConnectResult
}

/** Probes a TCP port. Separate so scanning and the connect flow can be tested without sockets. */
fun interface PortProber {
    enum class Result { OPEN, CLOSED, NO_ANSWER }
    suspend fun probe(host: String, port: Int, timeoutMs: Int): Result

    companion object {
        val SOCKETS = PortProber { host, port, timeoutMs ->
            withContext(Dispatchers.IO) {
                try {
                    Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                    Result.OPEN
                } catch (e: ConnectException) {
                    Result.CLOSED
                } catch (e: SocketTimeoutException) {
                    Result.NO_ANSWER
                } catch (e: IOException) {
                    Result.NO_ANSWER
                }
            }
        }
    }
}

/**
 * Connects to a TV over ADB with no code (legacy port 5555), or over the TLS port after pairing.
 *
 * Kadb blocks silently while the TV shows "Allow debugging?", so this tells the UI itself: if the
 * port is open but the handshake hasn't finished after [allowHintMs], [onAwaitingAllow] fires and
 * the app shows "Press Allow on your TV". A first connection with a saved, already-allowed key
 * finishes well inside that window and the hint never shows.
 */
class TvConnector(
    private val prober: PortProber = PortProber.SOCKETS,
    private val allowHintMs: Long = 1_200,
    private val allowTimeoutMs: Long = 90_000,
    private val trace: (String) -> Unit = {},
) {
    suspend fun connect(host: String, port: Int = 5555, onAwaitingAllow: () -> Unit = {}): ConnectResult = coroutineScope {
        when (prober.probe(host, port, 1_500)) {
            PortProber.Result.CLOSED -> return@coroutineScope ConnectResult.PortClosed
            PortProber.Result.NO_ANSWER -> return@coroutineScope ConnectResult.Unreachable
            PortProber.Result.OPEN -> Unit
        }
        trace("connect $host:$port")
        val kadb = Kadb.create(host, port, connectTimeout = 3_000, socketTimeout = (allowTimeoutMs + 5_000).toInt())
        var hinted = false
        val hint = launch {
            delay(allowHintMs)
            hinted = true
            trace("waiting for Allow on the TV")
            onAwaitingAllow()
        }
        val handshake = async(Dispatchers.IO) { runCatching { kadb.shell("echo tidytv-ok").allOutput } }
        val watchdog = launch {
            delay(allowTimeoutMs)
            trace("no Allow after ${allowTimeoutMs / 1000} s")
            kadb.close() // unblocks the handshake
        }
        try {
            val out = handshake.await()
            hint.cancel()
            watchdog.cancel()
            out.fold(
                onSuccess = { text ->
                    if (text.contains("tidytv-ok")) {
                        trace("connected $host:$port")
                        // Keep this session: a new one would ask "Allow debugging?" again unless
                        // the user ticked "Always allow".
                        ConnectResult.Connected(KadbTvShell(kadb, trace))
                    } else {
                        kadb.close(); ConnectResult.Failed("unexpected answer: ${text.take(80)}")
                    }
                },
                onFailure = { e ->
                    kadb.close()
                    trace("connect failed: ${e.javaClass.simpleName}: ${e.message}")
                    when {
                        watchdog.isCompleted && !watchdog.isCancelled -> ConnectResult.AllowTimedOut
                        hinted -> ConnectResult.Denied
                        else -> ConnectResult.Failed(e.message ?: e.javaClass.simpleName)
                    }
                },
            )
        } catch (e: CancellationException) {
            kadb.close()
            throw e
        }
    }

    /**
     * Pairs with a TV that only offers Wireless debugging (Android 11+), then connects to its TLS
     * port. [pairPort] and [code] come from the TV's "Pair device with pairing code" screen.
     */
    suspend fun pairAndConnect(host: String, pairPort: Int, code: String, connectPort: Int): ConnectResult {
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) return ConnectResult.Failed("The pairing code has 6 digits.")
        return try {
            trace("pair $host:$pairPort")
            Kadb.pair(host, pairPort, digits, name = "TidyTV")
            connect(host, connectPort)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            trace("pair failed: ${e.javaClass.simpleName}: ${e.message}")
            ConnectResult.Failed("Pairing didn't work. Check the code and try again (${e.javaClass.simpleName}).")
        }
    }
}
