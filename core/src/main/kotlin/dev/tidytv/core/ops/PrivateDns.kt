package dev.tidytv.core.ops

/** A DNS service that filters ads and trackers. [host] is what Android's Private DNS setting takes. */
enum class AdBlockProvider(val host: String, val title: String, val blurb: String) {
    ADS("dns.adguard-dns.com", "Ads and trackers", "AdGuard DNS"),
    FAMILY("family.adguard-dns.com", "Ads, trackers and adult sites", "AdGuard Family"),
}

/** What Private DNS is set to right now. */
data class DnsState(val mode: String?, val host: String?) {
    /** The provider TidyTV set, if it is the one in use. */
    val provider: AdBlockProvider? get() = if (mode == "hostname") AdBlockProvider.entries.firstOrNull { it.host == host } else null
    /** The owner (or their router app) set a different Private DNS: TidyTV asks before replacing it. */
    val someoneElses: Boolean get() = mode == "hostname" && provider == null
}

/**
 * Ad blocking through Android's Private DNS (Android 9+): the TV asks AdGuard's DNS for every address, and AdGuard
 * answers "nothing there" for known ad and tracker servers. It cannot block ads served from the same servers as the
 * content (YouTube's video ads, ads inside Netflix or Prime streams). The old values go in the action log, so
 * Restore puts back exactly what was there.
 */
class PrivateDns(private val tv: TvShell, private val log: ActionLog, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val MIN_SDK = 28
        private const val MODE = "private_dns_mode"
        private const val SPECIFIER = "private_dns_specifier"
        private const val MODE_KEY = "global/$MODE"
        private const val SPECIFIER_KEY = "global/$SPECIFIER"
    }

    suspend fun read(): DnsState {
        fun clean(v: String) = v.trim().takeIf { it.isNotEmpty() && it != "null" }
        return DnsState(clean(tv.run(Settings.get("global", MODE))), clean(tv.run(Settings.get("global", SPECIFIER))))
    }

    /** Turns on [provider]. Returns the state read back; the caller compares it with what was asked. */
    suspend fun turnOn(provider: AdBlockProvider): DnsState {
        if (!TvBinding.ensure(tv, log, clock)) throw WrongTvException()
        val before = read()
        remember(MODE_KEY, before.mode)
        remember(SPECIFIER_KEY, before.host)
        tv.run(Settings.put("global", SPECIFIER, provider.host))
        tv.run(Settings.put("global", MODE, "hostname"))
        val after = read()
        val ok = after.provider == provider
        log.append(ActionRecord(clock(), Op.SETTING, MODE_KEY, if (ok) Phase.DONE else Phase.FAILED, before = before.mode, detail = provider.host))
        if (!ok) undo()
        return if (ok) after else read()
    }

    /** Puts Private DNS back as it was before TidyTV first touched it. */
    suspend fun turnOff(): DnsState {
        if (!TvBinding.verify(tv, log)) throw WrongTvException()
        undo()
        return read()
    }

    private suspend fun undo() {
        val first = log.records().changedSettings()
        for (key in listOf(SPECIFIER_KEY, MODE_KEY)) {
            if (key !in first) continue
            val before = first[key]
            val (ns, name) = key.split('/', limit = 2)
            tv.run(if (before == null || before == "null") Settings.delete(ns, name) else Settings.put(ns, name, before))
        }
        // No record means TidyTV never changed it: leave the owner's setting alone.
        if (MODE_KEY in first && first[MODE_KEY] == null) tv.run(Settings.put("global", MODE, "off"))
    }

    /** Written before the change so a crash can't lose the old value; only the first note per key is used on restore. */
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
    const val DISCLOSURE = "This sends the web addresses your TV looks up to AdGuard's DNS service (AdGuard), a third party outside this app, instead of your usual DNS. Your TV's current DNS setting is saved first and put back exactly when you turn this off."
    const val SIDE_EFFECT = "A few free apps may stop working if they need their ads to load. Turn it off any time."
}
