package dev.tidytv.core.flow

/** A TV seen on the network. NSD fills in the friendly name and model before connecting. */
data class FoundTv(
    val host: String,
    /** Cast name from `_googlecast._tcp` TXT `fn`, e.g. "Living room TV". */
    val name: String? = null,
    /** Model from TXT `md`, e.g. "TCL 55C735". */
    val model: String? = null,
    /** Port 5555 answered the probe. */
    val legacyOpen: Boolean = false,
    /** Wireless debugging (`_adb-tls-connect._tcp`) port, when the TV advertises it. */
    val tlsPort: Int? = null,
    /** Pairing port (`_adb-tls-pairing._tcp`), only advertised while the pairing dialog is open. */
    val pairPort: Int? = null,
) {
    val title: String get() = name ?: model ?: host
    val subtitle: String get() = when {
        name != null -> listOfNotNull(model, host).joinToString(" · ")
        model != null -> host
        legacyOpen -> "Ready to connect"
        tlsPort != null -> "Wireless debugging is on"
        else -> "Found on your Wi-Fi"
    }

    /** Same TV seen by another scanner: keep every field either one learned. */
    fun merge(o: FoundTv) = copy(
        name = name ?: o.name,
        model = model ?: o.model,
        legacyOpen = legacyOpen || o.legacyOpen,
        tlsPort = o.tlsPort ?: tlsPort,
        pairPort = o.pairPort ?: pairPort,
    )
}

/** How one connection attempt ended (the adb module maps its ConnectResult to this). */
enum class Attempt { CONNECTED, PORT_CLOSED, UNREACHABLE, DENIED, ALLOW_TIMED_OUT, FAILED }

sealed interface ConnectState {
    /** [lookingFor] is set when a known TV stopped answering at its old address (its IP changed). */
    data class Scanning(val found: List<FoundTv> = emptyList(), val done: Boolean = false, val lookingFor: String? = null) : ConnectState
    /** Nothing answered: type the IP, check it's the same network. */
    data class NothingFound(val badIp: Boolean = false) : ConnectState
    data class Connecting(val tv: FoundTv) : ConnectState
    /** The TV is showing "Allow debugging?". [canRetry] turns on after 30 s. */
    data class PressAllow(val tv: FoundTv, val canRetry: Boolean = false) : ConnectState
    data class Connected(val tv: FoundTv) : ConnectState
    /** Port 5555 is closed but the TV offers Wireless debugging: enter the code shown on the TV. */
    data class Pairing(val tv: FoundTv, val error: String? = null, val busy: Boolean = false) : ConnectState
    /** Port closed and no Wireless debugging seen: help turning on Network debugging. */
    data class TurnOnDebugging(val tv: FoundTv) : ConnectState
    /** Typed address didn't answer. */
    data class Unreachable(val tv: FoundTv) : ConnectState
    data class Denied(val tv: FoundTv) : ConnectState
    data class Failed(val tv: FoundTv, val message: String) : ConnectState
}

sealed interface ConnectEvent {
    data object StartScan : ConnectEvent
    data class Found(val tv: FoundTv) : ConnectEvent
    data object ScanDone : ConnectEvent
    data class Pick(val tv: FoundTv) : ConnectEvent
    data class TypedIp(val text: String) : ConnectEvent
    /** The handshake is waiting on the TV's prompt. */
    data object AwaitingAllow : ConnectEvent
    /** 30 s passed on the Allow prompt. */
    data object AllowRetryAvailable : ConnectEvent
    data class Result(val attempt: Attempt, val message: String? = null) : ConnectEvent
    data class SubmitCode(val code: String, val pairPort: Int?) : ConnectEvent
    data object Retry : ConnectEvent
    data object Back : ConnectEvent
}

/**
 * The connect screen as a pure reducer (plan diagram):
 * scan → tap TV → :5555 → Connected; waiting → "Press Allow"; refused → pairing if the TV
 * advertises Wireless debugging, else "Turn on Network debugging"; nothing found → type the IP.
 * The view model runs the side effects for the state it lands in (scan, connect, pair).
 */
object ConnectMachine {
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun isIp(s: String) = IPV4.matchEntire(s.trim())?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true

    fun reduce(state: ConnectState, event: ConnectEvent): ConnectState = when (event) {
        ConnectEvent.StartScan -> ConnectState.Scanning()
        ConnectEvent.Back -> ConnectState.Scanning()

        is ConnectEvent.Found -> when (state) {
            is ConnectState.Scanning -> {
                val merged = mergeInto(state.found, event.tv)
                val wanted = state.lookingFor?.let { n -> merged.firstOrNull { it.name == n && it.reachable } }
                if (wanted != null) ConnectState.Connecting(wanted) else state.copy(found = merged)
            }
            is ConnectState.NothingFound -> ConnectState.Scanning(found = listOf(event.tv))
            // Wireless debugging showed up while we were explaining how to turn debugging on.
            is ConnectState.TurnOnDebugging -> if (event.tv.host == state.tv.host && event.tv.tlsPort != null) {
                ConnectState.Pairing(state.tv.merge(event.tv))
            } else state
            is ConnectState.Pairing -> if (event.tv.host == state.tv.host) state.copy(tv = state.tv.merge(event.tv)) else state
            else -> state
        }

        ConnectEvent.ScanDone -> when (state) {
            is ConnectState.Scanning -> if (state.found.isEmpty()) ConnectState.NothingFound() else state.copy(done = true, lookingFor = null)
            else -> state
        }

        is ConnectEvent.Pick -> ConnectState.Connecting(event.tv)

        is ConnectEvent.TypedIp -> {
            val ip = event.text.trim()
            if (isIp(ip)) ConnectState.Connecting(FoundTv(ip)) else ConnectState.NothingFound(badIp = true)
        }

        ConnectEvent.AwaitingAllow -> when (state) {
            is ConnectState.Connecting -> ConnectState.PressAllow(state.tv)
            else -> state
        }

        ConnectEvent.AllowRetryAvailable -> when (state) {
            is ConnectState.PressAllow -> state.copy(canRetry = true)
            else -> state
        }

        ConnectEvent.Retry -> when (state) {
            is ConnectState.PressAllow -> ConnectState.Connecting(state.tv)
            is ConnectState.Denied -> ConnectState.Connecting(state.tv)
            is ConnectState.Failed -> ConnectState.Connecting(state.tv)
            is ConnectState.TurnOnDebugging -> ConnectState.Connecting(state.tv)
            is ConnectState.Unreachable -> ConnectState.Connecting(state.tv)
            else -> state
        }

        is ConnectEvent.SubmitCode -> when (state) {
            is ConnectState.Pairing -> {
                val digits = event.code.filter { it.isDigit() }
                val port = event.pairPort ?: state.tv.pairPort
                when {
                    digits.length != 6 -> state.copy(error = "The code has 6 digits.", busy = false)
                    port == null || port !in 1..65535 -> state.copy(error = "Type the port shown after the IP address on your TV.", busy = false)
                    state.tv.tlsPort == null -> state.copy(error = "Your TV isn't showing Wireless debugging any more. Open it again.", busy = false)
                    else -> state.copy(tv = state.tv.copy(pairPort = port), error = null, busy = true)
                }
            }
            else -> state
        }

        is ConnectEvent.Result -> onResult(state, event)
    }

    private fun onResult(state: ConnectState, e: ConnectEvent.Result): ConnectState {
        val tv = when (state) {
            is ConnectState.Connecting -> state.tv
            is ConnectState.PressAllow -> state.tv
            is ConnectState.Pairing -> state.tv
            else -> return state
        }
        if (state is ConnectState.Pairing) {
            return when (e.attempt) {
                Attempt.CONNECTED -> ConnectState.Connected(tv)
                else -> state.copy(busy = false, error = e.message ?: "Pairing didn't work. Check the code and try again.")
            }
        }
        return when (e.attempt) {
            Attempt.CONNECTED -> ConnectState.Connected(tv)
            Attempt.PORT_CLOSED -> if (tv.tlsPort != null) ConnectState.Pairing(tv) else ConnectState.TurnOnDebugging(tv)
            // A named TV that stops answering probably got a new address from the router: look for it.
            Attempt.UNREACHABLE -> if (tv.name != null) ConnectState.Scanning(lookingFor = tv.name) else ConnectState.Unreachable(tv)
            Attempt.DENIED -> ConnectState.Denied(tv)
            Attempt.ALLOW_TIMED_OUT -> ConnectState.PressAllow(tv, canRetry = true)
            Attempt.FAILED -> ConnectState.Failed(tv, e.message ?: "Something went wrong.")
        }
    }

    private val FoundTv.reachable get() = legacyOpen || tlsPort != null

    /** Merge by host, newest info wins; reachable TVs first, then by name. */
    private fun mergeInto(list: List<FoundTv>, tv: FoundTv): List<FoundTv> {
        val i = list.indexOfFirst { it.host == tv.host }
        val next = if (i < 0) list + tv else list.toMutableList().also { it[i] = it[i].merge(tv) }
        return next.sortedWith(compareByDescending<FoundTv> { it.reachable }.thenBy { it.name == null }.thenBy { it.title.lowercase() })
    }
}
