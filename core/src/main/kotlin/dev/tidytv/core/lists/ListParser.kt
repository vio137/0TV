package dev.tidytv.core.lists

/** One package line from a debloat list. */
data class ListEntry(
    val pkg: String,
    /** Title of the comment block the entry sits under, e.g. "Voice, casting and assistants (owner's choice)". */
    val section: String,
    /** Trailing `# ...` comment on the same line, if any. */
    val note: String,
    /** True for commented-out lines such as `# com.tcl.airplay2  # AirPlay`: offered, but off by default. */
    val optional: Boolean = false,
)

data class DebloatList(
    /** Folder name in upstream examples/, e.g. "tcl-g05-google-tv". */
    val id: String,
    /** First line of the file without the leading "#", names the TV, OS and build. */
    val title: String,
    /** Every comment line before the first package line, without the leading "#". */
    val header: List<String>,
    val entries: List<ListEntry>,
    val optional: List<ListEntry>,
    /** From a header line like "Stock launcher: com.google.android.apps.tv.launcherx". */
    val stockLauncher: String?,
    /** Packages the header says to keep. Never disabled, even if listed. */
    val keep: Set<String>,
    val isFireOs: Boolean,
    /** Firmware where `pm disable-user` is refused and upstream uses `pm hide` (Redmi Android 5.1). */
    val needsHide: Boolean,
) {
    val packages: List<String> get() = entries.map { it.pkg }
}

sealed interface ParseResult {
    data class Ok(val list: DebloatList) : ParseResult
    /** The whole list is refused when any entry is not a bare package name. */
    data class Rejected(val id: String, val reasons: List<String>) : ParseResult
}

/**
 * Port of the parse block in upstream tools/disable.sh plus the checks in tests/test_examples.py.
 * Lists come from contributors, so anything unexpected rejects the whole list instead of guessing.
 */
object ListParser {
    private val TOKEN = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    private val STOCK = Regex("Stock launcher:\\s*([A-Za-z_][A-Za-z0-9_.]*[A-Za-z0-9_])")
    private val KEEP_WORDS = Regex("\\b(keep|kept|must stay|stays? enabled|leave .* enabled|left enabled|do not disable|don't disable|needed)\\b", RegexOption.IGNORE_CASE)

    fun parse(id: String, raw: String): ParseResult {
        val text = raw.removePrefix("﻿")
        val lines = text.split("\n").map { it.removeSuffix("\r") }
        val reasons = mutableListOf<String>()
        if (lines.firstOrNull()?.trimStart()?.startsWith("#") != true) {
            reasons += "first line should be a comment naming the TV"
        }

        val header = mutableListOf<String>()
        val entries = mutableListOf<ListEntry>()
        val optional = mutableListOf<ListEntry>()
        var seenPackage = false
        var section = ""
        var blockStart = true // the next comment line opens a new section

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> blockStart = true
                trimmed.startsWith("#") -> {
                    val body = trimmed.removePrefix("#").trim()
                    if (!seenPackage) header += body
                    val opt = optionalEntry(body)
                    if (seenPackage && opt != null) {
                        optional += opt.copy(section = section)
                    } else if (blockStart && body.isNotEmpty()) {
                        section = body
                        blockStart = false
                    }
                }
                else -> {
                    val code = line.substringBefore("#").trim()
                    val note = if (line.contains("#")) line.substringAfter("#").trim() else ""
                    val pkg = code.split(Regex("\\s+")).first()
                    if (code.contains(Regex("\\s")) || !PackageName.isValid(pkg)) {
                        reasons += "line ${index + 1}: not a package name: ${line.take(80)}"
                    } else {
                        seenPackage = true
                        blockStart = false
                        entries += ListEntry(pkg, section, note)
                    }
                }
            }
        }

        if (entries.isEmpty()) reasons += "no packages"
        val dupes = entries.groupBy { it.pkg }.filterValues { it.size > 1 }.keys
        if (dupes.isNotEmpty()) reasons += "listed twice: ${dupes.sorted()}"
        if (reasons.isNotEmpty()) return ParseResult.Rejected(id, reasons)

        val headerText = header.joinToString("\n")
        val listed = entries.map { it.pkg }.toSet()
        return ParseResult.Ok(
            DebloatList(
                id = id,
                title = header.firstOrNull().orEmpty(),
                header = header,
                entries = entries,
                // A commented-out line that is also an active line elsewhere is not optional.
                optional = optional.filter { it.pkg !in listed }.distinctBy { it.pkg },
                stockLauncher = STOCK.find(headerText)?.groupValues?.get(1),
                keep = keepPackages(header),
                isFireOs = headerText.contains("Fire OS", ignoreCase = true) || id.contains("fire-tv"),
                needsHide = headerText.contains("pm hide"),
            ),
        )
    }

    /** `com.tcl.airplay2   # AirPlay` inside a comment: a package followed by nothing or a comment. */
    private fun optionalEntry(body: String): ListEntry? {
        val first = body.split(Regex("\\s+"), limit = 2)
        val pkg = first[0]
        if (!PackageName.isValid(pkg)) return null
        val rest = first.getOrNull(1)?.trim().orEmpty()
        if (rest.isNotEmpty() && !rest.startsWith("#")) return null
        return ListEntry(pkg, "", rest.removePrefix("#").trim(), optional = true)
    }

    /**
     * Header prose such as "Keep com.tcl.tvinput, com.tcl.settings, ..." spans lines, so join the
     * header and look at it sentence by sentence. Over-matching only protects more packages.
     */
    internal fun keepPackages(header: List<String>): Set<String> {
        val sentences = header.joinToString(" ").split(Regex("(?<=[.;:])\\s+(?=[A-Z(])"))
        return sentences.filter { KEEP_WORDS.containsMatchIn(it) }
            .flatMap { s -> TOKEN.findAll(s).map { it.value.trimEnd('.') } }
            .filter { PackageName.isValid(it) && it.count { c -> c == '.' } >= 2 }
            .toSet()
    }
}
