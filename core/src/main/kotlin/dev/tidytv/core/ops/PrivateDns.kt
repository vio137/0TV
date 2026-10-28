package dev.tidytv.core.ops

/** A DNS service that filters ads and trackers. [host] is what Android's Private DNS setting takes. */
enum class AdBlockProvider(val host: String, val title: String, val blurb: String) {
    ADS("dns.adguard-dns.com", "Ads and trackers", "AdGuard DNS"),
    FAMILY("family.adguard-dns.com", "Ads, trackers and adult sites", "AdGuard Family"),
}

/** What Private DNS is set to right now. [mode] is Android's own word: off, opportunistic (Automatic) or hostname. */
data class DnsState(val mode: String?, val host: String?) {
    /** The built-in provider in use, if that is the one set. */
    val provider: AdBlockProvider? get() = if (mode == "hostname") AdBlockProvider.entries.firstOrNull { it.host.equals(host, ignoreCase = true) } else null
    /** A hostname is set that is not one of the built-in providers: the owner's own DNS-over-TLS server (or a router app's). */
    val custom: Boolean get() = mode == "hostname" && !host.isNullOrBlank() && provider == null
    /** Private DNS is pinned to a named server, so every lookup goes to it. */
    val pinned: Boolean get() = mode == "hostname" && !host.isNullOrBlank()
    /** Kept for older callers: a different server than the built-in ones is set. */
    val someoneElses: Boolean get() = custom

    /** One plain line on what the TV is doing right now, shown on the Block ads screen. */
    fun headline(): String = when {
        provider != null -> "Private DNS is set to ${provider!!.blurb}"
        custom -> "Using your DNS-over-TLS server: $host"
        mode == "opportunistic" -> "Automatic Private DNS. No ad blocking."
        mode == "off" || mode == null -> "Private DNS is off. No ad blocking."
        mode == "hostname" -> "Private DNS is set to a server name, but the name is empty."
        else -> "Private DNS mode: $mode"
    }
}

/** Checks a DNS-over-TLS server name before it goes anywhere near the TV: a plain hostname only. */
object DotHost {
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /** The cleaned lower-case hostname, or the reason it was refused. */
    sealed interface Result { data class Ok(val host: String) : Result; data class Bad(val reason: String) : Result }

    fun check(input: String): Result {
        val h = input.trim().trimEnd('.').lowercase()
        if (h.isEmpty()) return Result.Bad("Type the server name, like dns.example.com.")
        if ("://" in h || '/' in h || '?' in h || '#' in h || '@' in h) return Result.Bad("Use the server name only, without https:// or a path.")
        if (':' in h) return Result.Bad("Use the server name only. Private DNS always uses port 853, so leave out the port.")
        if (h.any { it.isWhitespace() }) return Result.Bad("A server name has no spaces.")
        if (h.length > 253) return Result.Bad("That name is too long.")
        val labels = h.split('.')
        if (labels.size < 2) return Result.Bad("Use a full name with a dot, like dns.example.com.")
        if (labels.last().all { it.isDigit() }) return Result.Bad("Private DNS needs a server name, not an IP address.")
        if (labels.any { !LABEL.matches(it) }) return Result.Bad("Use only letters, numbers, dots and hyphens.")
        if (h.endsWith(".local")) return Result.Bad(".local names don't work for Private DNS.")
        return Result.Ok(h)
    }
}

/**
 * Ad blocking through Android's Private DNS (Android 9+): the TV asks a DNS-over-TLS server for every address, and a
 * blocking server such as AdGuard answers "nothing there" for known ad and tracker servers. It cannot block ads served
 * from the same servers as the content (YouTube's video ads, ads inside Netflix or Prime streams). The old values go in
 * the action log, so turning it off puts back what was there.
 */
class PrivateDns(private val tv: TvShell, private val log: ActionLog, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val MIN_SDK = 28
        private const val MODE = "private_dns_mode"
        private const val SPECIFIER = "private_dns_specifier"
        private const val MODE_KEY = "global/$MODE"
        private const val SPECIFIER_KEY = "global/$SPECIFIER"

        /** Server names this app has set, from an action log, oldest first. */
        fun hostsSetIn(records: List<ActionRecord>): List<String> =
            records.filter { it.op == Op.SETTING && it.target == MODE_KEY && it.phase == Phase.DONE }.mapNotNull { it.detail }.filter { it != "automatic" }
    }

    suspend fun read(): DnsState {
        fun clean(v: String) = v.trim().takeIf { it.isNotEmpty() && it != "null" }
        return DnsState(clean(tv.run(Settings.get("global", MODE))), clean(tv.run(Settings.get("global", SPECIFIER))))
    }

    /** Servers this app has set on this TV, oldest first (the built-in ones and the owner's own). */
    fun hostsWeSet(): List<String> = hostsSetIn(log.records())

    /** The owner's own server name this app last set, for pre-filling the field. */
    fun lastCustomHost(): String? = hostsWeSet().lastOrNull { h -> AdBlockProvider.entries.none { it.host == h } }

    /** Turns on a built-in [provider]. Returns the state read back; the caller compares it with what was asked. */
    suspend fun turnOn(provider: AdBlockProvider): DnsState = useHost(provider.host)

    /** Points Private DNS at the owner's own DNS-over-TLS server. The name must pass [DotHost.check]. */
    suspend fun useCustom(host: String): DnsState {
        val ok = DotHost.check(host) as? DotHost.Result.Ok ?: throw IllegalArgumentException("not a valid DNS-over-TLS server name")
        return useHost(ok.host)
    }

    /** True when the last "turn on" was undone because the TV could not look up a name through the server. */
    var lookupFailed: Boolean = false
        private set

    /** One real lookup on the TV. Only an explicit failure counts as failing; a TV that prints nothing is not blamed. */
    suspend fun resolves(): Boolean {
        repeat(3) {
            val out = tv.run("ping -c1 -W3 example.com 2>&1").lowercase()
            if ("unknown host" !in out && "bad address" !in out && "name or service not known" !in out) return true
            kotlinx.coroutines.delay(1500)
        }
        return false
    }

    private suspend fun useHost(host: String): DnsState {
        if (!TvBinding.ensure(tv, log, clock)) throw WrongTvException()
        val before = read()
        // Only the first note per key counts on restore, so what was there before this app touched it is what returns.
        remember(MODE_KEY, before.mode)
        remember(SPECIFIER_KEY, before.host)
        tv.run(Settings.put("global", SPECIFIER, host))
        tv.run(Settings.put("global", MODE, "hostname"))
        val after = read()
        var ok = after.mode == "hostname" && after.host.equals(host, ignoreCase = true)
        lookupFailed = false
        if (ok) {
            // Android takes the setting even when the server can't be reached, and then every lookup fails. Check one.
            lookupFailed = !resolves()
            if (lookupFailed) ok = false
        }
        log.append(ActionRecord(clock(), Op.SETTING, MODE_KEY, if (ok) Phase.DONE else Phase.FAILED, before = before.mode, detail = host))
        if (!ok) undo()
        return if (ok) after else read()
    }

    /**
     * Turns the pinned server off. The saved old value comes back, unless that is itself a server this app set earlier
     * (an old test run can leave one behind): then Private DNS goes to Automatic, so "off" really is off.
     */
    suspend fun turnOff(): DnsState {
        if (!TvBinding.verify(tv, log)) throw WrongTvException()
        val ours = hostsWeSet().map { it.lowercase() }.toSet() + AdBlockProvider.entries.map { it.host }
        undo()
        val now = read()
        if (now.pinned && now.host!!.lowercase() in ours) {
            tv.run(Settings.delete("global", SPECIFIER))
            tv.run(Settings.put("global", MODE, "opportunistic"))
            log.append(ActionRecord(clock(), Op.SETTING, MODE_KEY, Phase.DONE, before = now.mode, detail = "automatic"))
            return read()
        }
        return now
    }

    private suspend fun undo() {
        val first = log.records().changedSettings()
        for (key in listOf(SPECIFIER_KEY, MODE_KEY)) {
            if (key !in first) continue
            val before = first[key]
            val (ns, name) = key.split('/', limit = 2)
            tv.run(if (before == null || before == "null") Settings.delete(ns, name) else Settings.put(ns, name, before))
        }
        // No record means this app never changed it: leave the owner's setting alone.
        if (MODE_KEY in first && first[MODE_KEY] == null) tv.run(Settings.put("global", MODE, "off"))
    }

    /** Written before the change so a crash can't lose the old value. */
    private fun remember(key: String, before: String?) {
        log.append(ActionRecord(clock(), Op.SETTING, key, Phase.INTENT, before = before))
    }
}

/** What the Block ads screen tells people, word for word, so a test can hold it to the truth. */
object AdBlockInfo {
    val BLOCKS = listOf(
        "Ads and trackers inside apps that load them from ad networks: free games, news and sports apps, banner ads.",
        "The ad and promo rows on the TV's home screen.",
        "The TV's own tracking and telemetry calls.",
    )
    val DOES_NOT_BLOCK = listOf(
        "YouTube video ads. YouTube sends them from the same servers as the video.",
        "Ads inside Netflix, Prime Video, Disney+ or Hotstar streams.",
        "Apps that use their own DNS.",
    )
    const val YOUTUBE_WARNING = "YouTube ads will still show. Only YouTube Premium or a patched YouTube app removes them."
    const val DISCLOSURE = "This sends the web addresses your TV looks up to the DNS server you choose (AdGuard, or your own), a third party outside this app, instead of your usual DNS. Your TV's current DNS setting is saved first and put back when you turn this off."
    const val SIDE_EFFECT = "A few free apps may stop working if they need their ads to load. Turn it off any time."
}
