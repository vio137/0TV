package dev.tidytv.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File

/**
 * Reads an app's real icon straight out of its APK, fetched from the TV. Android's own
 * PackageManager parses the archive; nothing is installed on the phone.
 */
class ApkIcons(private val context: Context, private val store: Store) {
    /** The app's own name (its label in the APK), or null. */
    fun labelFromApk(pkg: String, apk: ByteArray): String? {
        val tmp = File(context.cacheDir, "apk/$pkg.label.apk").apply { parentFile?.mkdirs(); writeBytes(apk) }
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(tmp.path, 0) ?: return null
            val ai = info.applicationInfo ?: return null
            if (info.packageName != pkg) return null
            ai.sourceDir = tmp.path
            ai.publicSourceDir = tmp.path
            return ai.loadLabel(pm).toString().trim().takeIf { it.isNotEmpty() && it != pkg }
        } finally {
            tmp.delete()
        }
    }

    fun iconFromApk(pkg: String, apk: ByteArray): String? {
        val tmp = File(context.cacheDir, "apk/$pkg.apk").apply { parentFile?.mkdirs(); writeBytes(apk) }
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(tmp.path, 0) ?: return null
            val ai = info.applicationInfo ?: return null
            if (info.packageName != pkg || ai.icon == 0) return null
            ai.sourceDir = tmp.path
            ai.publicSourceDir = tmp.path
            val drawable = ai.loadIcon(pm) ?: return null
            val size = 144
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bmp))
            val out = store.iconFile(pkg)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return out.path
        } finally {
            tmp.delete()
        }
    }
}
