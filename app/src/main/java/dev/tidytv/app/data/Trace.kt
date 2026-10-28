package dev.tidytv.app.data

import dev.tidytv.core.ops.LogRedactor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling connection and command trace. Each file is capped; the oldest is dropped. What leaves the
 * phone through Share logs goes through [LogRedactor] first.
 */
class Trace(private val dir: File, private val maxBytes: Long = 512 * 1024, private val keep: Int = 3) {
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val current get() = File(dir, "trace.log")

    @Synchronized
    fun line(text: String) {
        dir.mkdirs()
        if (current.length() > maxBytes) rotate()
        current.appendText("${stamp.format(Date())}  $text\n")
    }

    private fun rotate() {
        File(dir, "trace.$keep.log").delete()
        for (i in keep - 1 downTo 1) File(dir, "trace.$i.log").takeIf { it.exists() }?.renameTo(File(dir, "trace.${i + 1}.log"))
        current.renameTo(File(dir, "trace.1.log"))
    }

    /** Oldest first, redacted. [secrets] are values to hide everywhere, such as serial numbers. */
    @Synchronized
    fun export(header: String, extra: Map<String, String>, secrets: Collection<String>): String {
        val files = (keep downTo 1).map { File(dir, "trace.$it.log") } + current
        val body = buildString {
            appendLine(header)
            for ((name, text) in extra) { appendLine("===== $name"); appendLine(text) }
            appendLine("===== trace")
            files.filter { it.exists() }.forEach { append(it.readText()) }
        }
        return LogRedactor.redact(body, secrets)
    }
}
