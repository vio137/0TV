package dev.tidytv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.tidytv.app.PhoneAppEntry
import dev.tidytv.core.ops.App2Tv
import dev.tidytv.core.ops.PhoneApk
import dev.tidytv.core.ops.PhoneApp
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Reads the apps installed on this phone for App2TV: the list shown to the user, and the package
 * files (base plus splits) of one chosen app. Nothing is copied here. Files are streamed on send.
 */
object PhoneApps {
    fun list(ctx: Context): List<PhoneAppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .map { PhoneAppEntry(it.first, it.second) }
    }

    private fun files(ctx: Context, pkg: String): Pair<android.content.pm.ApplicationInfo, List<File>>? {
        val info = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val all = buildList {
            add(File(info.sourceDir))
            info.splitSourceDirs?.forEach { add(File(it)) }
        }
        return info to all
    }

    fun load(ctx: Context, pkg: String): PhoneApp? {
        val pm = ctx.packageManager
        val (info, all) = files(ctx, pkg) ?: return null
        if (all.any { !it.canRead() }) return null
        val pi = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: return null
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        val abis = mutableSetOf<String>()
        for (f in all) {
            App2Tv.abiOfSplitName(f.name)?.let { abis += it }
            runCatching {
                ZipFile(f).use { z ->
                    z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") }
                        .mapNotNull { it.removePrefix("lib/").substringBefore('/').takeIf { a -> a.isNotEmpty() } }
                        .forEach { abis += it }
                }
            }
        }
        return PhoneApp(
            pkg, info.loadLabel(pm).toString(), pi.versionName, code,
            if (android.os.Build.VERSION.SDK_INT >= 24) info.minSdkVersion else 1,
            all.map { PhoneApk(it.name, it.length()) }, abis,
        )
    }

    fun open(ctx: Context, pkg: String, apk: PhoneApk): InputStream {
        val (_, all) = files(ctx, pkg) ?: throw java.io.IOException("app is gone")
        val f = all.firstOrNull { it.name == apk.name } ?: throw java.io.IOException("file is gone")
        return f.inputStream()
    }
}
