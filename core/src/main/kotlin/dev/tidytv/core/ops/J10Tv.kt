package dev.tidytv.core.ops

import dev.tidytv.core.lists.PackageName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/**
 * Optional, separate J10TV install (https://github.com/JioTV-Go/jiotv_go_app, MIT).
 * The APK is never bundled: the phone downloads it from the project's own GitHub releases, checks size and
 * SHA-256 against the digest GitHub reports for that asset, then installs it as a normal app. Nothing here
 * changes any other app, and Restore Everything does not remove J10TV (use [J10Tv.uninstall]).
 * The project releases one universal ARM build; the TV needs no account details from 0TV - sign-in happens
 * inside the J10TV app itself.
 */
object J10Tv {
    const val RELEASE_API = "https://api.github.com/repos/JioTV-Go/jiotv_go_app/releases/latest"
    const val RELEASES_PAGE = "https://github.com/JioTV-Go/jiotv_go_app/releases"
    const val DOWNLOAD_PREFIX = "https://github.com/JioTV-Go/jiotv_go_app/releases/download/"
    /** Package id read from the manifest of the v1.2.0 release APK with aapt2. */
    const val PACKAGE = "com.skylake.skytv.jgorunner"
    /** Signing certificate (SHA-256) the v1.2.0 release APK carries; self-signed, so this is pinned from the release we inspected. */
    val PINNED_SIGNERS = setOf("f5753de28893ae190a230ed0d158ce976340908e25ea9c1e803d28abc08cf41b")
    const val MIN_SDK = 21
    const val APK_TMP = "/data/local/tmp/tidytv-j10tv.apk"
    private val ASSET_NAME = Regex("^JTV-GO-Server-.+-release\\.apk$")
    private val SHA = Regex("^sha256:([0-9a-f]{64})$")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable data class GhAsset(
        val name: String = "", val size: Long = 0, val digest: String? = null,
        @SerialName("browser_download_url") val url: String = "",
    )
    @Serializable data class GhRelease(
        @SerialName("tag_name") val tag: String = "", val draft: Boolean = false, val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    /** What will be downloaded and how it is checked. The project ships one universal ARM build. */
    data class Plan(val tag: String, val assetName: String, val url: String, val size: Long, val sha256: String)

    sealed interface Pick {
        data class Ok(val plan: Plan) : Pick
        /** The TV runs no ARM ABI the project ships (e.g. x86 box). Nothing to install. */
        data class UnsupportedAbi(val abis: List<String>) : Pick
        /** The release data is missing something we need to verify the download; we refuse rather than guess. */
        data class Unverifiable(val why: String) : Pick
    }

    fun parseRelease(body: String): GhRelease? = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull()

    fun pick(release: GhRelease, abis: List<String>): Pick {
        if (release.draft || release.prerelease || release.tag.isEmpty()) return Pick.Unverifiable("latest release is a draft or pre-release")
        val arm = abis.any { it.trim() == "arm64-v8a" || it.trim() == "armeabi-v7a" || it.trim() == "armeabi" }
        if (!arm) return Pick.UnsupportedAbi(abis)
        val a = release.assets.firstOrNull { ASSET_NAME.matches(it.name) }
            ?: return Pick.Unverifiable("release ${release.tag} has no JTV-GO-Server release APK")
        val sha = a.digest?.let { SHA.matchEntire(it)?.groupValues?.get(1) }
            ?: return Pick.Unverifiable("GitHub reports no SHA-256 for ${a.name}")
        if (a.size <= 0) return Pick.Unverifiable("${a.name} has no size")
        if (!a.url.startsWith(DOWNLOAD_PREFIX + release.tag + "/")) return Pick.Unverifiable("${a.name} is not hosted by the project's releases")
        return Pick.Ok(Plan(release.tag, a.name, a.url, a.size, sha))
    }

    /** True only when exactly [plan].size bytes were read and their SHA-256 equals the published one. */
    fun verify(plan: Plan, open: () -> InputStream): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        open().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n); total += n }
        }
        return total == plan.size && md.digest().joinToString("") { "%02x".format(it) } == plan.sha256
    }

    /** What the phone read out of the downloaded APK itself (manifest package and signing certificates). */
    data class ApkIdentity(val pkg: String, val versionName: String?, val signerSha256: Set<String>)

    data class Preflight(val sdk: Int?, val abis: List<String>, val freeMb: Long?)

    suspend fun preflight(tv: TvShell): Preflight {
        val abis = tv.run("getprop ro.product.cpu.abilist").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val sdk = tv.run("getprop ro.build.version.sdk").trim().toIntOrNull()
        val free = Parse.dfAvailableKb(tv.run("df /data"))?.let { it / 1024 }
        return Preflight(sdk, abis, free)
    }

    enum class Integrity(val text: String) {
        SHA256_ON_TV("SHA-256 checked on the TV"),
        SHA1_ON_TV("SHA-1 checked on the TV (it has no sha256sum; weaker, protects against corruption only)"),
        MD5_ON_TV("MD5 checked on the TV (it has no sha256sum; weaker, protects against corruption only)"),
        SIZE_ONLY("size only: this TV offers no checksum tool, so the transfer was checked by size and by Android's own package and signature check"),
    }

    sealed interface Outcome {
        /** [fresh]: no J10TV was on the TV before, so Remove is allowed. Otherwise it was an update of an existing install. */
        data class Installed(val pkg: String, val version: String?, val launched: Boolean, val integrity: Integrity, val fresh: Boolean) : Outcome
        data class NoSpace(val freeMb: Long?) : Outcome
        data class TransferMismatch(val detail: String) : Outcome
        /** The APK is not the project's: wrong package id or signer. Nothing was sent. */
        data class IdentityMismatch(val detail: String) : Outcome
        data class Refused(val reason: String, val needsTvApproval: Boolean) : Outcome
        /** The TV can't verify the file with any checksum tool; nothing was installed. The caller may retry with [allowSizeOnly] after the owner agrees. */
        data object NeedsWeakCheckConsent : Outcome
        /** pm said Success but the package isn't confirmed on the TV. */
        data object Unconfirmed : Outcome
        data object Disconnected : Outcome
    }

    private fun digestOf(open: () -> InputStream, alg: String): String {
        val md = MessageDigest.getInstance(alg)
        open().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun versionOf(tv: TvShell): String? =
        Parse.lines(tv.run("dumpsys package $PACKAGE | grep -m1 versionName")).firstOrNull()?.substringAfter("versionName=", "")?.takeIf { it.isNotEmpty() }

    private suspend fun firstInstall(tv: TvShell): String? =
        Parse.lines(tv.run("dumpsys package $PACKAGE | grep -m1 firstInstallTime")).firstOrNull()?.substringAfter("firstInstallTime=", "")?.takeIf { it.isNotEmpty() }

    suspend fun install(
        tv: TvShell, plan: Plan, identity: ApkIdentity, open: () -> InputStream, log: ActionLog, tvSerial: String,
        allowSizeOnly: Boolean = false,
        clock: () -> Long = System::currentTimeMillis, sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): Outcome = try {
        // 1. The file itself must be the project's app: right package id, signed only by the pinned certificate.
        if (tvSerial.isBlank() || tvSerial == "unknown") return Outcome.Refused("TidyTV couldn't read this TV's serial, so it can't keep a trustworthy install record. Reconnect and try again.", false)
        if (!TvBinding.ensure(tv, log, clock)) return Outcome.Refused("This TV can't be matched to its install record, so nothing was changed.", false)
        if (identity.pkg != PACKAGE) return Outcome.IdentityMismatch("package id is ${identity.pkg}")
        if (identity.signerSha256.isEmpty() || !PINNED_SIGNERS.containsAll(identity.signerSha256)) return Outcome.IdentityMismatch("signing certificate is not the one the project's releases use")
        // 2. The TV must be able to run it, and have room for the copy plus the install.
        val sdk = tv.run("getprop ro.build.version.sdk").trim().toIntOrNull()
        if (sdk != null && sdk < MIN_SDK) return Outcome.Refused("This TV runs Android API $sdk; J10TV needs API $MIN_SDK or newer.", false)
        val free = Parse.dfAvailableKb(tv.run("df /data"))?.let { it / 1024 }
        if (free != null && free < (plan.size * 2) / 1_048_576 + 200) return Outcome.NoSpace(free)
        // 3. Remember what was there before, so Remove can tell "ours" from "already there".
        val had = Parse.packages(tv.run(Commands.isInstalled(PACKAGE))).contains(PACKAGE)
        val hadVersion = if (had) versionOf(tv) else null
        log.append(ActionRecord(clock(), Op.INSTALL, PACKAGE, Phase.INTENT, before = if (had) "preexisting:${hadVersion ?: "?"}" else "absent", detail = "j10tv ${plan.tag} ${plan.assetName}"))
        open().use { tv.pushStream(it, plan.size, APK_TMP) }
        // 4. Transfer identity: size, then the strongest checksum the TV can compute.
        val remoteSize = tv.run("stat -c %s $APK_TMP").trim().toLongOrNull()
        if (remoteSize != plan.size) { tv.run("rm -f $APK_TMP"); return fail(log, clock, Outcome.TransferMismatch("TV has $remoteSize bytes, expected ${plan.size}")) }
        var integrity = Integrity.SIZE_ONLY
        for ((cmd, alg, level) in listOf(Triple("sha256sum", "SHA-256", Integrity.SHA256_ON_TV), Triple("sha1sum", "SHA-1", Integrity.SHA1_ON_TV), Triple("md5sum", "MD5", Integrity.MD5_ON_TV))) {
            val got = tv.run("$cmd $APK_TMP").trim().substringBefore(' ').lowercase()
            if (!got.matches(Regex("[0-9a-f]{32,64}"))) continue
            val want = if (alg == "SHA-256") plan.sha256 else digestOf(open, alg)
            if (got != want) { tv.run("rm -f $APK_TMP"); return fail(log, clock, Outcome.TransferMismatch("$alg on the TV differs")) }
            integrity = level; break
        }
        if (integrity == Integrity.SIZE_ONLY && !allowSizeOnly) {
            tv.run("rm -f $APK_TMP")
            log.append(ActionRecord(clock(), Op.INSTALL, PACKAGE, Phase.FAILED, detail = "stopped: no checksum tool on the TV, owner consent needed"))
            return Outcome.NeedsWeakCheckConsent
        }
        // 5. Plain install: no downgrade flag, no permission grant, no signature tricks.
        val out = tv.run("pm install -r $APK_TMP")
        tv.run("rm -f $APK_TMP")
        if (!out.contains("Success")) {
            val approval = out.contains("INSTALL_FAILED_USER_RESTRICTED") || out.contains("VERIFICATION_FAILURE") || out.contains("INSTALL_FAILED_ABORTED")
            return fail(log, clock, Outcome.Refused(friendly(out), approval))
        }
        // 6. Verify the exact package, not "something new appeared".
        val present = Parse.packages(tv.run(Commands.isInstalled(PACKAGE))).contains(PACKAGE)
        val ver = if (present) versionOf(tv) else null
        if (!present || (identity.versionName != null && ver != null && ver != identity.versionName)) {
            log.append(ActionRecord(clock(), Op.INSTALL, PACKAGE, Phase.FAILED, detail = "not confirmed after install (version $ver)")); return Outcome.Unconfirmed
        }
        log.append(ActionRecord(clock(), Op.INSTALL, PACKAGE, Phase.DONE, before = if (had) "preexisting:${hadVersion ?: "?"}" else "absent",
            detail = listOf("j10tv", plan.tag, if (had) "updated" else "fresh", tvSerial, firstInstall(tv) ?: "?").joinToString("|")))
        Outcome.Installed(PACKAGE, ver, launch(tv, PACKAGE, sleep), integrity, fresh = !had)
    } catch (e: TvConnectionLost) {
        Outcome.Disconnected
    }

    private fun fail(log: ActionLog, clock: () -> Long, o: Outcome): Outcome {
        log.append(ActionRecord(clock(), Op.INSTALL, PACKAGE, Phase.FAILED, detail = o.toString().take(200)))
        return o
    }

    private fun friendly(out: String): String = when {
        out.contains("OLDER_SDK") -> "This Android version is too old for the J10TV release."
        out.contains("NO_MATCHING_ABIS") -> "The TV's processor type doesn't match this build."
        out.contains("INSUFFICIENT_STORAGE") -> "The TV ran out of storage."
        out.contains("UPDATE_INCOMPATIBLE") -> "A copy signed by someone else is already installed. Remove it on the TV first."
        out.contains("USER_RESTRICTED") || out.contains("VERIFICATION_FAILURE") || out.contains("ABORTED") ->
            "The TV blocked the install. Look for an Install / Allow prompt on the TV screen and check Settings > Security for install-from-ADB limits."
        else -> out.trim().take(200).ifEmpty { "The TV gave no answer." }
    }

    /** Starts the app and checks that it is the one in front. */
    suspend fun launch(tv: TvShell, pkg: String, sleep: suspend (Long) -> Unit): Boolean {
        PackageName.require(pkg)
        val comp = Parse.lines(tv.run("cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER $pkg"))
            .lastOrNull { it.startsWith("$pkg/") } ?: return false
        tv.run("am start -W -n ${Commands.component(comp)}")
        sleep(1500)
        return tv.run(Commands.FOCUSED_APP).contains("$pkg/")
    }

    enum class Removal { REMOVED, NOT_OURS, FAILED }

    /** The last completed J10TV record for this exact TV: ["j10tv", tag, "fresh"|"updated", serial, firstInstallTime]. */
    private fun lastFor(log: ActionLog, tvSerial: String): List<String>? {
        if (tvSerial.isBlank() || tvSerial == "unknown") return null
        val r = log.records().filter { it.target == PACKAGE && it.phase == Phase.DONE && (it.op == Op.INSTALL || it.op == Op.UNINSTALL) }.lastOrNull() ?: return null
        if (r.op != Op.INSTALL) return null
        val f = r.detail?.split('|') ?: return null
        return f.takeIf { it.size >= 5 && it[0] == "j10tv" && it[3] == tvSerial }
    }

    /** True when the log says this feature installed J10TV onto THIS TV when it had none, and it wasn't removed since. */
    fun removable(log: ActionLog, tvSerial: String): Boolean = lastFor(log, tvSerial)?.get(2) == "fresh"

    /**
     * Removes J10TV only when this TV's record says we installed it onto a TV that had none, AND the app on the TV
     * still has the first-install time we recorded (so a copy someone reinstalled later is not ours to remove).
     * A copy that was already there, even if updated here, is left alone: it is the owner's.
     */
    suspend fun uninstall(tv: TvShell, log: ActionLog, tvSerial: String, clock: () -> Long = System::currentTimeMillis): Removal {
        val rec = lastFor(log, tvSerial)?.takeIf { it[2] == "fresh" } ?: return Removal.NOT_OURS
        if (!try { TvBinding.verify(tv, log) } catch (e: TvConnectionLost) { return Removal.FAILED }) return Removal.NOT_OURS
        val now = try { firstInstall(tv) } catch (e: TvConnectionLost) { return Removal.FAILED }
        if (now == null || rec[4] == "?" || now != rec[4]) return Removal.NOT_OURS
        log.append(ActionRecord(clock(), Op.UNINSTALL, PACKAGE, Phase.INTENT))
        val gone = try { tv.run(Commands.uninstall(PACKAGE)).contains("Success") && !Parse.packages(tv.run(Commands.isInstalled(PACKAGE))).contains(PACKAGE) } catch (e: TvConnectionLost) { false }
        log.append(ActionRecord(clock(), Op.UNINSTALL, PACKAGE, if (gone) Phase.DONE else Phase.FAILED))
        return if (gone) Removal.REMOVED else Removal.FAILED
    }
}
