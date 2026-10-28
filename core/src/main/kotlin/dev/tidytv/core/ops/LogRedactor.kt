package dev.tidytv.core.ops

/**
 * Shared logs must carry no personal data: serial numbers, Wi-Fi names, MAC addresses, accounts,
 * e-mails and the first three parts of local IP addresses are removed before a log leaves the phone.
 */
object LogRedactor {
    private val SENSITIVE_PROP = Regex(
        "^(\\s*\\[[^]]*(serial|ssid|bssid|mac|wifi\\.interface|imei|account|email|bt\\.name|device_name|hostname|net\\.dns)[^]]*]: \\[)[^]]*(])",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val MAC = Regex("\\b[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}\\b")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val IPV4 = Regex("\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b")
    private val SSID = Regex("(?i)(ssid[=:]\\s*\"?)[^\",\\s]+")

    fun redact(text: String, secrets: Collection<String> = emptyList()): String {
        var out = SENSITIVE_PROP.replace(text) { it.groupValues[1] + "hidden" + it.groupValues[3] }
        for (s in secrets.filter { it.length >= 4 }) out = out.replace(s, "<hidden>")
        out = EMAIL.replace(out, "<email>")
        out = MAC.replace(out, "<mac>")
        out = SSID.replace(out) { it.groupValues[1] + "<hidden>" }
        out = IPV4.replace(out) { m ->
            val parts = m.groupValues.drop(1)
            if (parts.any { it.toInt() > 255 }) m.value else "x.x.x.${parts[3]}"
        }
        return out
    }
}
