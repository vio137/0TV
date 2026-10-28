package dev.tidytv.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** The selected device's private cached still; not a live video feed. */
data class TvPreview(val image: Bitmap? = null, val capturedAt: Long? = null, val loading: Boolean = false) {
    companion object {
        fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
            var sample = 1
            while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            // Protected content is often an all-black frame. Do not call it a current screen.
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            if (pixels.all { (it and 0x00ffffff) == 0 }) { bitmap.recycle(); return null }
            return bitmap
        }
    }
}
