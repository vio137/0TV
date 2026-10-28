package dev.tidytv.core.ops

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A TV that answers App2TV's commands from a small script and records what ran. */
private class ScriptTv(
    val sdk: Int = 28,
    val abis: String = "armeabi-v7a,armeabi",
    val freeKb: Long = 3_000_000,
    var installedVersion: Long? = null,
    val system: Boolean = false,
    val installOut: String = "Success",
    val installSetsVersion: Long? = null,
    val leanback: Boolean = true,
) : TvShell {
    val cmds = mutableListOf<String>()
    val sizes = mutableMapOf<String, Long>()
    override suspend fun push(bytes: ByteArray, remotePath: String) { sizes[remotePath] = bytes.size.toLong() }
    override suspend fun run(command: String): String {
        cmds += command
        return when {
            command == "getprop ro.build.version.sdk" -> "$sdk"
            command == "getprop ro.product.cpu.abilist" -> abis
            command == "df /data" -> "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/x 8000000 1000000 $freeKb 10% /data"
            command.startsWith("pm list packages -s") -> if (system) "package:com.x.app" else ""
            command.startsWith("pm list packages") -> if (installedVersion != null) "package:com.x.app" else ""
            command.startsWith("dumpsys package") -> "    versionCode=${installedVersion} minSdk=21\n    versionName=1.${installedVersion}"
            command.startsWith("stat -c %s") -> "${sizes[command.removePrefix("stat -c %s ").trim()] ?: 0}"
            command.startsWith("pm install") -> { if (installOut.contains("Success") && installSetsVersion != null) installedVersion = installSetsVersion; installOut }
            command.contains("LEANBACK_LAUNCHER") -> if (leanback) "priority=0\ncom.x.app/.Main" else ""
            command.contains("android.intent.category.LAUNCHER") -> "com.x.app/.Main"
            else -> ""
        }
    }
}

class App2TvTest {
    private fun app(vararg apks: PhoneApk, abis: Set<String> = emptySet(), min: Int = 21, code: Long = 5) =
        PhoneApp("com.x.app", "Xapp", "1.5", code, min, apks.toList().ifEmpty { listOf(PhoneApk("base.apk", 10)) }, abis)

    @Test fun installsOneApkChecksItAndCleansUp() = runTest {
        val tv = ScriptTv(installSetsVersion = 5)
        val stages = mutableListOf<App2Tv.Stage>()
        val out = App2Tv.send(tv, app(), { ByteArray(10).inputStream() }, MemoryActionLog(), { s, _, _ -> stages += s }, { 1L })
        assertTrue(out.toString(), out is App2Tv.Outcome.Installed)
        assertEquals(App2Tv.Opens.TV_LAUNCHER, (out as App2Tv.Outcome.Installed).opens)
        assertTrue(tv.cmds.any { it.startsWith("pm install -r ${App2Tv.TMP_DIR}/") })
        assertTrue("temp files removed", tv.cmds.last { it.startsWith("rm") || it.startsWith("pm") || it.startsWith("cmd") }.let { tv.cmds.any { c -> c == "rm -rf ${App2Tv.TMP_DIR}" } })
        assertEquals(listOf(App2Tv.Stage.CHECKING, App2Tv.Stage.COPYING, App2Tv.Stage.INSTALLING, App2Tv.Stage.VERIFYING), stages)
    }

    @Test fun splitAppsAreInstalledTogether() = runTest {
        val tv = ScriptTv(installSetsVersion = 5)
        val a = app(PhoneApk("base.apk", 10), PhoneApk("split_config.xxhdpi.apk", 4))
        val out = App2Tv.send(tv, a, { ByteArray(it.size.toInt()).inputStream() }, MemoryActionLog(), clock = { 1L })
        assertTrue(out.toString(), out is App2Tv.Outcome.Installed)
        assertTrue(tv.cmds.any { it.startsWith("pm install-multiple -r ") && it.contains("split_config.xxhdpi.apk") })
    }

    @Test fun refusesSixtyFourBitOnlyAppOnA32BitTvInPlainEnglish() = runTest {
        val tv = ScriptTv(abis = "armeabi-v7a,armeabi")
        val a = app(PhoneApk("base.apk", 10), PhoneApk("split_config.arm64_v8a.apk", 5), abis = setOf("arm64-v8a"))
        val out = App2Tv.send(tv, a, { ByteArray(10).inputStream() }, MemoryActionLog(), clock = { 1L })
        assertTrue(out is App2Tv.Outcome.Blocked)
        val r = (out as App2Tv.Outcome.Blocked).reason
        assertTrue(r, r.contains("64-bit ARM") && r.contains("32-bit ARM") && r.contains("missing"))
        assertTrue("nothing was sent", tv.sizes.isEmpty() && tv.cmds.none { it.startsWith("pm install") })
    }

    @Test fun a64BitTvRunsThirtyTwoBitLibraries() = runTest {
        val v = App2Tv.check(app(abis = setOf("armeabi-v7a")), TvTarget(30, listOf("arm64-v8a", "armeabi-v7a"), null), App2Tv.TvCopy(null, null, false))
        assertTrue(v is App2Tv.Verdict.Ok)
    }

    @Test fun refusesWhenAndroidIsTooOldOrNewerVersionOrSystemApp() {
        val tv = TvTarget(28, listOf("armeabi-v7a"), null)
        val old = App2Tv.check(app(min = 33), tv, App2Tv.TvCopy(null, null, false)) as App2Tv.Verdict.Blocked
        assertTrue(old.reason, old.reason.contains("Android 13") && old.reason.contains("Android 9"))
        val newer = App2Tv.check(app(code = 3), tv, App2Tv.TvCopy(9, "9.0", false)) as App2Tv.Verdict.Blocked
        assertTrue(newer.reason, newer.reason.contains("newer"))
        assertTrue(App2Tv.check(app(), tv, App2Tv.TvCopy(1, "1", true)) is App2Tv.Verdict.Blocked)
    }

    @Test fun refusesWhenTheTvHasNoRoom() = runTest {
        val tv = ScriptTv(freeKb = 20_000)
        val out = App2Tv.send(tv, app(PhoneApk("base.apk", 50_000_000)), { ByteArray(1).inputStream() }, MemoryActionLog(), clock = { 1L })
        assertTrue(out is App2Tv.Outcome.Blocked && (out as App2Tv.Outcome.Blocked).reason.contains("free"))
    }

    @Test fun installerFailuresAreExplainedAndTvApprovalIsFlagged() = runTest {
        val a = app()
        assertTrue(App2Tv.explain("Failure [INSTALL_FAILED_NO_MATCHING_ABIS: x]", a).contains("processor"))
        assertTrue(App2Tv.explain("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: x]", a).contains("signed by someone else"))
        val tv = ScriptTv(installOut = "Failure [INSTALL_FAILED_USER_RESTRICTED]")
        val out = App2Tv.send(tv, a, { ByteArray(10).inputStream() }, MemoryActionLog(), clock = { 1L }) as App2Tv.Outcome.Failed
        assertTrue(out.tvPrompt && out.why.contains("TV screen"))
        assertTrue("temp files removed even on failure", tv.cmds.contains("rm -rf ${App2Tv.TMP_DIR}"))
    }

    @Test fun successWithoutTheAppShowingIsNotReportedAsInstalled() = runTest {
        val tv = ScriptTv(installOut = "Success", installSetsVersion = null)
        assertEquals(App2Tv.Outcome.Unconfirmed, App2Tv.send(tv, app(), { ByteArray(10).inputStream() }, MemoryActionLog(), clock = { 1L }))
    }

    @Test fun phoneOnlyAppsAreSaidToHaveNoTvIcon() = runTest {
        val tv = ScriptTv(installSetsVersion = 5, leanback = false)
        val out = App2Tv.send(tv, app(), { ByteArray(10).inputStream() }, MemoryActionLog(), clock = { 1L }) as App2Tv.Outcome.Installed
        assertEquals(App2Tv.Opens.PHONE_LAUNCHER_ONLY, out.opens)
        assertTrue(App2Tv.opensText(out.opens, "Xapp").contains("no TV icon"))
    }

    @Test fun splitNamesGiveTheirProcessorType() {
        assertEquals("arm64-v8a", App2Tv.abiOfSplitName("split_config.arm64_v8a.apk"))
        assertEquals("armeabi-v7a", App2Tv.abiOfSplitName("config.armeabi_v7a.apk"))
        assertNull(App2Tv.abiOfSplitName("split_config.xxhdpi.apk"))
    }
}
