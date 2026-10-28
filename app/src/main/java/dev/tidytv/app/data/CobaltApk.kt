package dev.tidytv.app.data

import android.content.Context
import android.content.pm.PackageManager
import dev.tidytv.core.ops.Cobalt
import java.io.File
import java.security.MessageDigest

/** Reads the manifest package id, version and signing certificates out of a downloaded APK with Android's own parser. */
object CobaltApk {
    @Suppress("DEPRECATION")
    // The modern lambda is only invoked when SDK_INT >= 28 (signerBytes; CobaltApkTest.api27UsesOnlyLegacySignatures), which lint cannot see through the lambda.
    @android.annotation.SuppressLint("NewApi")
    fun identity(context: Context, file: File): Cobalt.ApkIdentity? = runCatching {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: return null
        val signers = signerBytes(
            android.os.Build.VERSION.SDK_INT,
            modern = { info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } },
            legacy = { info.signatures?.map { it.toByteArray() } },
        ) ?: return null
        Cobalt.ApkIdentity(
            info.packageName, info.versionName,
            signers.map { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }.toSet(),
        )
    }.getOrNull()

    /** API 28+ reports the full signer set; API 26-27 only has the legacy list. Empty means unknown, so callers fail closed. */
    fun signerBytes(sdk: Int, modern: () -> List<ByteArray>?, legacy: () -> List<ByteArray>?): List<ByteArray>? =
        (if (sdk >= 28) modern() else legacy())?.takeIf { it.isNotEmpty() }
}
