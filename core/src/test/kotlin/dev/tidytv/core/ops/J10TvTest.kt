package dev.tidytv.core.ops

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

class J10TvTest {
    private val apk = ByteArray(5000) { (it % 251).toByte() }
    private fun release(digest: String? = "sha256:" + sha(apk), draft: Boolean = false, prerelease: Boolean = false, url: String = "${J10Tv.DOWNLOAD_PREFIX}v1.2.0/JTV-GO-Server-v1.2.0-release.apk") =
        J10Tv.parseRelease("""{"tag_name":"v1.2.0","draft":$draft,"prerelease":$prerelease,"assets":[
          {"name":"JTV-GO-Server-v1.2.0-release.apk","size":${apk.size},"digest":${digest?.let { "\"$it\"" } ?: "null"},"browser_download_url":"$url"}]}""")!!

    @Test fun picksTheUniversalArmBuild() {
        assertEquals("JTV-GO-Server-v1.2.0-release.apk", (J10Tv.pick(release(), listOf("arm64-v8a", "armeabi-v7a")) as J10Tv.Pick.Ok).plan.assetName)
        assertEquals("JTV-GO-Server-v1.2.0-release.apk", (J10Tv.pick(release(), listOf("armeabi-v7a")) as J10Tv.Pick.Ok).plan.assetName)
        assertTrue(J10Tv.pick(release(), listOf("x86_64")) is J10Tv.Pick.UnsupportedAbi)
        assertTrue(J10Tv.pick(release(prerelease = true), listOf("arm64-v8a")) is J10Tv.Pick.Unverifiable)
    }
    @Test fun refusesWhatItCannotVerify() {
        assertTrue(J10Tv.pick(release(digest = null), listOf("arm64-v8a")) is J10Tv.Pick.Unverifiable)
        assertTrue(J10Tv.pick(release(digest = "md5:abc"), listOf("arm64-v8a")) is J10Tv.Pick.Unverifiable)
        assertTrue(J10Tv.pick(release(draft = true), listOf("arm64-v8a")) is J10Tv.Pick.Unverifiable)
        assertTrue(J10Tv.pick(release(url = "https://evil.example/JTV-GO-Server-v1.2.0-release.apk"), listOf("arm64-v8a")) is J10Tv.Pick.Unverifiable)
    }
    @Test fun verifyChecksSizeAndHash() {
        val plan = (J10Tv.pick(release(), listOf("arm64-v8a")) as J10Tv.Pick.Ok).plan
        assertTrue(J10Tv.verify(plan) { ByteArrayInputStream(apk) })
        assertFalse(J10Tv.verify(plan) { ByteArrayInputStream(apk.copyOf(4999)) })
        assertFalse(J10Tv.verify(plan) { ByteArrayInputStream(apk.also { }.clone().also { it[0] = 9 }) })
    }

    private class Tv(var installSays: String = "Success", var remoteOk: Boolean = true, val apk: ByteArray, var free: Long = 4_000_000L,
                     var hasSha256: Boolean = true, var hasMd5: Boolean = true, var preexisting: Boolean = false, var sdk: Int = 30) : TvShell {
        val cmds = mutableListOf<String>(); var pushed: ByteArray? = null
        val P = J10Tv.PACKAGE; var installed = preexisting; var focus = ""
        override suspend fun push(bytes: ByteArray, remotePath: String) { pushed = bytes }
        override suspend fun run(command: String): String { cmds += command; return when {
            command == "am get-current-user" -> "0"
            command.startsWith("settings --user 0 get secure android_id") -> "a1b2c3d4e5f60718"
            command.startsWith("getprop ro.build.version.sdk") -> "$sdk"
            command.startsWith("df /data") -> "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/x 8000000 4000000 $free 50% /data"
            command.startsWith("stat -c") -> if (remoteOk) "${pushed?.size}" else "12"
            command.startsWith("sha256sum") -> if (!hasSha256) "sha256sum: not found" else (if (remoteOk) sha(pushed!!) else "0".repeat(64)) + "  f"
            command.startsWith("sha1sum") -> "sha1sum: not found"
            command.startsWith("md5sum") -> if (!hasMd5) "md5sum: not found" else java.security.MessageDigest.getInstance("MD5").digest(pushed!!).joinToString("") { "%02x".format(it) } + "  f"
            command.startsWith("pm install") -> { if (installSays == "Success") installed = true; installSays }
            command.startsWith("dumpsys package $P | grep -m1 firstInstallTime") -> if (installed) "    firstInstallTime=2026-10-28 14:55:11" else ""
            command.startsWith("dumpsys package $P") -> if (installed) "    versionName=1.2.0" else ""
            command.startsWith("cmd package resolve-activity") -> "priority=0\n$P/com.skylake.skytv.jgorunner.MainActivity"
            command.startsWith("am start") -> { focus = "mFocusedApp=ActivityRecord{1 u0 $P/com.skylake.skytv.jgorunner.MainActivity t1}"; "Status: ok" }
            command == Commands.FOCUSED_APP -> focus
            command.startsWith("pm uninstall") -> { installed = false; "Success" }
            command.startsWith("pm list packages $P") -> if (installed) "package:$P" else ""
            else -> "" } }
    }
    private fun plan() = (J10Tv.pick(release(), listOf("arm64-v8a")) as J10Tv.Pick.Ok).plan
    private val goodId = J10Tv.ApkIdentity(J10Tv.PACKAGE, "1.2.0", J10Tv.PINNED_SIGNERS)
    private suspend fun go(tv: Tv, log: ActionLog = MemoryActionLog(), id: J10Tv.ApkIdentity = goodId, allow: Boolean = false) =
        J10Tv.install(tv, plan(), id, { ByteArrayInputStream(apk) }, log, "SER1", allowSizeOnly = allow, sleep = {})

    @Test fun freshInstallVerifiesExactPackageAndCanBeRemoved() = runTest {
        val tv = Tv(apk = apk); val log = MemoryActionLog()
        val o = go(tv, log) as J10Tv.Outcome.Installed
        assertEquals(J10Tv.PACKAGE, o.pkg); assertTrue(o.launched); assertTrue(o.fresh); assertEquals(J10Tv.Integrity.SHA256_ON_TV, o.integrity)
        assertTrue(tv.cmds.none { it.contains("disable") || it.contains("youtube") || it.contains(" -d ") || it.contains(" -g ") || it.contains("pm list packages -3") })
        assertTrue(tv.cmds.contains("rm -f ${J10Tv.APK_TMP}"))
        assertTrue(J10Tv.removable(log, "SER1"))
        assertEquals(J10Tv.Removal.REMOVED, J10Tv.uninstall(tv, log, "SER1"))
        assertFalse(J10Tv.removable(log, "SER1"))
        assertEquals(J10Tv.Removal.NOT_OURS, J10Tv.uninstall(tv, log, "SER1"))
    }
    @Test fun preexistingCopyIsUpdatedButNeverRemoved() = runTest {
        val tv = Tv(apk = apk, preexisting = true); val log = MemoryActionLog()
        val o = go(tv, log) as J10Tv.Outcome.Installed
        assertFalse(o.fresh); assertFalse(J10Tv.removable(log, "SER1"))
        assertEquals(J10Tv.Removal.NOT_OURS, J10Tv.uninstall(tv, log, "SER1"))
        assertTrue(tv.cmds.none { it.startsWith("pm uninstall") })
        assertEquals("preexisting:1.2.0", log.records().last { it.phase == Phase.DONE }.before)
    }
    @Test fun wrongPackageOrSignerNeverReachesTheTv() = runTest {
        val tv = Tv(apk = apk)
        assertTrue(go(tv, id = goodId.copy(pkg = "com.google.android.youtube.tv")) is J10Tv.Outcome.IdentityMismatch)
        assertTrue(go(tv, id = goodId.copy(signerSha256 = setOf("ab".repeat(32)))) is J10Tv.Outcome.IdentityMismatch)
        assertTrue(go(tv, id = goodId.copy(signerSha256 = emptySet())) is J10Tv.Outcome.IdentityMismatch)
        assertNull(tv.pushed)
    }
    @Test fun missingSha256FallsBackAndIsLabelled() = runTest {
        val md5 = go(Tv(apk = apk, hasSha256 = false)) as J10Tv.Outcome.Installed
        assertEquals(J10Tv.Integrity.MD5_ON_TV, md5.integrity)
        val stopped = Tv(apk = apk, hasSha256 = false, hasMd5 = false)
        assertEquals(J10Tv.Outcome.NeedsWeakCheckConsent, go(stopped))
        assertTrue(stopped.cmds.none { it.startsWith("pm install") })
        val none = go(Tv(apk = apk, hasSha256 = false, hasMd5 = false), allow = true) as J10Tv.Outcome.Installed
        assertEquals(J10Tv.Integrity.SIZE_ONLY, none.integrity)
        assertTrue(J10Tv.Integrity.SIZE_ONLY.text.contains("size only"))
    }
    @Test fun oldAndroidIsRefusedBeforeSending() = runTest {
        val tv = Tv(apk = apk, sdk = 19)
        assertTrue(go(tv) is J10Tv.Outcome.Refused); assertNull(tv.pushed)
    }
    @Test fun refusalAndApprovalAreReported() = runTest {
        val r = go(Tv(installSays = "Failure [INSTALL_FAILED_USER_RESTRICTED]", apk = apk))
        assertTrue((r as J10Tv.Outcome.Refused).needsTvApproval)
        val s = go(Tv(installSays = "Failure [INSTALL_FAILED_NO_MATCHING_ABIS]", apk = apk))
        assertFalse((s as J10Tv.Outcome.Refused).needsTvApproval)
    }
    @Test fun badTransferNeverReachesPmInstall() = runTest {
        val tv = Tv(remoteOk = false, apk = apk)
        assertTrue(go(tv) is J10Tv.Outcome.TransferMismatch)
        assertTrue(tv.cmds.none { it.startsWith("pm install") })
    }
    @Test fun noSpaceStopsBeforeSending() = runTest {
        val tv = Tv(apk = apk, free = 100_000L)
        assertTrue(go(tv) is J10Tv.Outcome.NoSpace); assertNull(tv.pushed)
    }

    @Test fun anotherTvsRecordNeverAuthorisesRemoval() = runTest {
        val log = MemoryActionLog()
        go(Tv(apk = apk), log)
        val other = Tv(apk = apk, preexisting = true)
        assertFalse(J10Tv.removable(log, "SER2"))
        assertEquals(J10Tv.Removal.NOT_OURS, J10Tv.uninstall(other, log, "SER2"))
        assertTrue(other.cmds.none { it.startsWith("pm uninstall") })
    }
    @Test fun aCopyReinstalledLaterIsNotOurs() = runTest {
        val log = MemoryActionLog(); val tv = Tv(apk = apk)
        go(tv, log)
        val reinstalled = object : TvShell by tv {
            override suspend fun run(command: String) = if (command.contains("firstInstallTime")) "    firstInstallTime=2026-10-29 09:00:00" else tv.run(command)
        }
        assertEquals(J10Tv.Removal.NOT_OURS, J10Tv.uninstall(reinstalled, log, "SER1"))
        assertTrue(tv.cmds.none { it.startsWith("pm uninstall") })
    }

    @Test fun missingSerialFailsClosed() = runTest {
        val tv = Tv(apk = apk)
        val r = J10Tv.install(tv, plan(), goodId, { ByteArrayInputStream(apk) }, MemoryActionLog(), "", sleep = {})
        assertTrue(r is J10Tv.Outcome.Refused); assertNull(tv.pushed)
        val log = MemoryActionLog(); go(Tv(apk = apk), log)
        assertFalse(J10Tv.removable(log, ""))
    }
}
