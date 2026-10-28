package dev.tidytv.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Finds TVs with ADB on port 5555 in the phone's /24. Every address is probed at once (bounded by
 * [parallel]), so a whole subnet takes about two probe timeouts.
 */
class SubnetScanner(
    private val prober: PortProber = PortProber.SOCKETS,
    private val port: Int = 5555,
    private val timeoutMs: Int = 300,
    private val parallel: Int = 64,
) {
    /** Hosts with the port open, as they are found. [phoneIp] is the phone's own IPv4 address. */
    fun scan(phoneIp: String): Flow<String> = channelFlow {
        val hosts = hostsOf(phoneIp)
        val gate = Semaphore(parallel)
        coroutineScope {
            for (h in hosts) launch(Dispatchers.IO) {
                gate.withPermit {
                    if (prober.probe(h, port, timeoutMs) == PortProber.Result.OPEN) send(h)
                }
            }
        }
    }

    companion object {
        private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

        /** The other 253 addresses of the phone's /24; empty for anything that isn't a private IPv4. */
        fun hostsOf(phoneIp: String): List<String> {
            val m = IPV4.matchEntire(phoneIp) ?: return emptyList()
            val o = m.groupValues.drop(1).map { it.toInt() }
            if (o.any { it > 255 }) return emptyList()
            if (!isPrivate(o)) return emptyList()
            val base = "${o[0]}.${o[1]}.${o[2]}."
            return (1..254).filter { it != o[3] }.map { base + it }
        }

        private fun isPrivate(o: List<Int>) =
            o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)

        fun isValidHost(s: String): Boolean {
            val m = IPV4.matchEntire(s.trim()) ?: return false
            return m.groupValues.drop(1).all { it.toInt() in 0..255 }
        }
    }
}
