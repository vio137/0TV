package dev.tidytv.app

import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest

/** One bounded, private still per TV. Names and network addresses never become file paths. */
class TvPreviewCache(private val dir: File) {
    private fun file(device: TvSummary): File {
        val serial = device.serial.trim()
        val identity = if (serial.isNotEmpty() && serial.lowercase() !in setOf("unknown", "null", "0123456789abcdef"))
            "serial:$serial" else "host:${device.host}|model:${device.model}"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$key.png")
    }
    fun read(device: TvSummary): Bitmap? = runCatching {
        val f = file(device)
        if (f.length() !in 1..4_194_304) return null
        TvPreview.decode(f.readBytes()).also { if (it == null) f.delete() }
    }.getOrNull()
    fun save(device: TvSummary, image: Bitmap) {
        runCatching {
            dir.mkdirs()
            val f = file(device)
            val temp = File(dir, f.name + ".tmp")
            try {
                temp.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                check(temp.renameTo(f))
            } finally { temp.delete() }
        }
    }
}
