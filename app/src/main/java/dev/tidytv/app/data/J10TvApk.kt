package dev.tidytv.app.data

import android.content.Context
import dev.tidytv.core.ops.J10Tv
import java.io.File

/** Reads the J10TV manifest package id, version and signing certificates out of a downloaded APK with Android's own parser. */
object J10TvApk {
    fun identity(context: Context, file: File): J10Tv.ApkIdentity? =
        CobaltApk.identity(context, file)?.let { J10Tv.ApkIdentity(it.pkg, it.versionName, it.signerSha256) }
}
