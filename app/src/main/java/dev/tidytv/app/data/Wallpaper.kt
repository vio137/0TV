package dev.tidytv.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A photo from the phone, made ready to be the TV's home screen wallpaper. */
class WallpaperImage(val jpeg: ByteArray, val width: Int, val height: Int, val originalBytes: Long)

/**
 * Turns any photo into a TV wallpaper on the phone, so only a small file crosses the network: turned
 * upright from its EXIF data, cropped to the screen's 16:9 around the centre, scaled down to at most
 * 1920x1080 (never up), and saved as a JPEG under [MAX_BYTES]. A 12 MP phone photo of about 4 MB
 * usually ends up around 250 to 450 KB.
 */
object Wallpaper {
    const val WIDTH = 1920
    const val HEIGHT = 1080
    const val MAX_BYTES = 600 * 1024

    fun fromPhoto(originalBytes: Long, open: () -> InputStream): WallpaperImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rotation = runCatching { open().use { exifRotation(ExifInterface(it)) } }.getOrDefault(0)
        val (w, h) = if (rotation % 180 == 0) bounds.outWidth to bounds.outHeight else bounds.outHeight to bounds.outWidth

        // Decode at the smallest power-of-two size that still covers the 16:9 crop at full HD.
        var sample = 1
        while (min(w / (sample * 2), h / (sample * 2) * 16 / 9) >= WIDTH) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val upright = if (rotation == 0) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                .also { if (it !== decoded) decoded.recycle() }

        val cropped = cropTo16x9(upright)
        val outW = min(WIDTH, cropped.width)
        val outH = (outW * 9f / 16f).roundToInt()
        val scaled = if (cropped.width == outW) cropped else Bitmap.createScaledBitmap(cropped, outW, outH, true)

        var quality = 85
        var bytes = compress(scaled, quality)
        while (bytes.size > MAX_BYTES && quality > 55) { quality -= 10; bytes = compress(scaled, quality) }
        WallpaperImage(bytes, scaled.width, scaled.height, originalBytes)
    }.getOrNull()

    private fun cropTo16x9(b: Bitmap): Bitmap {
        val targetH = b.width * 9 / 16
        return if (targetH <= b.height) {
            Bitmap.createBitmap(b, 0, (b.height - targetH) / 2, b.width, max(1, targetH))
        } else {
            val targetW = b.height * 16 / 9
            Bitmap.createBitmap(b, (b.width - targetW) / 2, 0, max(1, targetW), b.height)
        }
    }

    private fun compress(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun exifRotation(e: ExifInterface) = when (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

/** The looks bundled from cleanyourtv's themes/ (see the copyThemes task): config.json plus wallpapers. */
object Looks {
    fun load(assets: android.content.res.AssetManager, id: String): dev.tidytv.core.ops.LookFiles? = runCatching {
        val dir = "themes/$id"
        fun read(name: String) = runCatching { assets.open("$dir/$name").use { it.readBytes() } }.getOrNull()
        val config = read("config.json")?.decodeToString() ?: return null
        val collection = assets.list("$dir/wallpapers").orEmpty().filter { it.matches(Regex("(?i).+\\.(jpe?g|png|webp)")) }.sorted()
        dev.tidytv.core.ops.LookFiles(
            id = id,
            config = config,
            wallpaper = read("wallpaper.jpg"),
            wallpapers = collection.map { it to assets.open("$dir/wallpapers/$it").use { s -> s.readBytes() } },
            captions = read("wallpapers/captions.txt"),
        )
    }.getOrNull()
}
