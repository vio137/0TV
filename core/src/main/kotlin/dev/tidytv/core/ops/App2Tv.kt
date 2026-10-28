package dev.tidytv.core.ops

import dev.tidytv.core.lists.PackageName

/** One APK file of an app on the phone: the base APK or one of its split APKs. */
data class PhoneApk(val name: String, val size: Long)

/**
 * An app installed on the phone, as App2TV sees it. [nativeAbis] are the processor types the app's
 * own files carry native code for (read from the APK contents), empty for apps with no native code.
 */
data class PhoneApp(
    val pkg: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val apks: List<PhoneApk>,
    val nativeAbis: Set<String>,
) {
    val totalBytes: Long get() = apks.sumOf { it.size }
    val isSplit: Boolean get() = apks.size > 1
}

/** What App2TV found out about the TV before anything is sent. */
data class TvTarget(val sdk: Int?, val abis: List<String>, val freeDataBytes: Long?)

/**
 * Sends an app that is installed on the phone to the TV over the existing ADB connection: copies the
 * app's APK files (base plus any splits) to a temporary folder, runs Android's own installer, checks
 * the result and removes the temporary files. It never uses a downgrade, a signature bypass or
 * any permission grant, and it never replaces an app that is part of the TV's system.
 */
object App2Tv {
    const val TMP_DIR = "/data/local/tmp/app2tv"

    /** Why the TV can't take this app, or null when nothing stops it. Plain English, no codes. */
    sealed interface Verdict {
        data class Ok(val notes: List<String>) : Verdict
        data class Blocked(val reason: String) : Verdict
    }

    fun androidName(api: Int): String = when (api) {
        21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7"; 25 -> "7.1"; 26 -> "8"; 27 -> "8.1"; 28 -> "9"
        29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
        else -> "API $api"
    }

    private fun abiLabel(a: String) = when (a) {
        "arm64-v8a" -> "64-bit ARM"; "armeabi-v7a", "armeabi" -> "32-bit ARM"; "x86" -> "32-bit Intel"; "x86_64" -> "64-bit Intel"; else -> a
    }

    /** True when the TV can run native code built for [lib]. A 64-bit TV also runs 32-bit ARM code. */
    private fun runs(tvAbis: List<String>, lib: String): Boolean {
        val tv = tvAbis.map { it.trim() }.toSet()
        return when (lib) {
            "armeabi" -> "armeabi-v7a" in tv || "armeabi" in tv
            else -> lib in tv
        }
    }

    /** The processor type a split APK carries, from its file name (split_config.arm64_v8a.apk), or null. */
    fun abiOfSplitName(name: String): String? {
        val m = Regex("(?:^|[._-])(arm64[_-]v8a|armeabi[_-]v7a|armeabi|x86[_-]64|x86)\\.apk$").find(name.lowercase()) ?: return null
        return m.groupValues[1].replace('_', '-').let { if (it == "x86-64") "x86_64" else it }
    }

    /** What the TV would run, given its facts. Checked before a single byte is sent. */
    fun check(app: PhoneApp, tv: TvTarget, onTv: TvCopy): Verdict {
        val notes = mutableListOf<String>()
        if (onTv.isSystem) return Verdict.Blocked("${app.label} is part of this TV's own system, so it isn't replaced from here.")
        if (tv.sdk != null && app.minSdk > tv.sdk)
            return Verdict.Blocked("${app.label} needs Android ${androidName(app.minSdk)} or newer, and this TV runs Android ${androidName(tv.sdk)}.")
        if (app.nativeAbis.isNotEmpty() && tv.abis.isNotEmpty() && app.nativeAbis.none { runs(tv.abis, it) }) {
            val has = app.nativeAbis.joinToString(" and ") { abiLabel(it) }
            val tvHas = tv.abis.joinToString(" and ") { abiLabel(it.trim()) }.ifEmpty { "an unknown type" }
            return Verdict.Blocked(
                "${app.label} on your phone only has $has code, and this TV's processor is $tvHas. " +
                    (if (app.isSplit) "Phones install only the parts that match the phone, so the TV version is missing. " else "") +
                    "Get a version of the app built for the TV."
            )
        }
        if (onTv.versionCode != null && onTv.versionCode > app.versionCode)
            return Verdict.Blocked("The TV already has a newer ${app.label} (${onTv.versionName ?: onTv.versionCode}) than your phone (${app.versionName ?: app.versionCode}). Android doesn't allow going back to an older version.")
        if (onTv.versionCode != null) notes += "The TV already has ${app.label}. This will update it and keep its data."
        // Room for the files plus the installer's own copy.
        if (tv.freeDataBytes != null) {
            val need = app.totalBytes * 2 + 100L * 1024 * 1024
            if (tv.freeDataBytes < need)
                return Verdict.Blocked("The TV needs about ${mb(need)} MB free to install ${app.label} and has ${mb(tv.freeDataBytes)} MB. Free up space with Storage first.")
        }
        if (app.isSplit) notes += "This app comes in ${app.apks.size} parts. All of them are sent together."
        notes += "Phone apps aren't always made for a TV remote, so ${app.label} may be hard to use or may not appear in the TV's app list."
        return Verdict.Ok(notes)
    }

    private fun mb(b: Long) = (b + 524_288) / 1_048_576

    /** What the TV has for this package right now. */
    data class TvCopy(val versionCode: Long?, val versionName: String?, val isSystem: Boolean)

    suspend fun readTarget(tv: TvShell): TvTarget {
        val sdk = tv.run("getprop ro.build.version.sdk").trim().toIntOrNull()
        val abis = tv.run("getprop ro.product.cpu.abilist").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val free = Parse.dfAvailableKb(tv.run("df /data"))?.let { it * 1024 }
        return TvTarget(sdk, abis, free)
    }

    suspend fun readTvCopy(tv: TvShell, pkg: String): TvCopy {
        PackageName.require(pkg)
        val present = Parse.packages(tv.run(Commands.isInstalled(pkg))).contains(pkg)
        if (!present) return TvCopy(null, null, false)
        val dump = tv.run("dumpsys package $pkg")
        val code = Regex("versionCode=(\\d+)").find(dump)?.groupValues?.get(1)?.toLongOrNull()
        val name = Regex("versionName=([^\\s]+)").find(dump)?.groupValues?.get(1)
        val system = runCatching { Parse.packages(tv.run("pm list packages -s $pkg")).contains(pkg) }.getOrDefault(false)
        return TvCopy(code, name, system)
    }

    /** How the app can be opened on the TV after it installs. */
    enum class Opens { TV_LAUNCHER, PHONE_LAUNCHER_ONLY, NO_ICON }

    sealed interface Outcome {
        data class Installed(val label: String, val versionName: String?, val opens: Opens, val updated: Boolean) : Outcome
        data class Blocked(val reason: String) : Outcome
        /** The installer said no. [why] is plain English; [tvPrompt] means the TV is waiting on, or refused, an on-screen approval. */
        data class Failed(val why: String, val tvPrompt: Boolean) : Outcome
        data class TransferMismatch(val file: String) : Outcome
        /** The installer said Success but the TV doesn't show the app. */
        data object Unconfirmed : Outcome
        data object Disconnected : Outcome
    }

    /** Where the install is, for the progress line. */
    enum class Stage { CHECKING, COPYING, INSTALLING, VERIFYING }

    /** Turns the installer's answer into a plain sentence. Unknown answers are passed on in short, never hidden. */
    fun explain(out: String, app: PhoneApp): String {
        val o = out
        return when {
            o.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> "${app.label} has no code for this TV's processor. Phone apps are often built only for 64-bit phones."
            o.contains("INSTALL_FAILED_OLDER_SDK") -> "${app.label} needs a newer Android than this TV has."
            o.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> "The TV ran out of storage while installing. Free up space and try again."
            o.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "The TV already has a newer version of ${app.label}."
            o.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || o.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE") ->
                "The TV has a copy of ${app.label} signed by someone else (for example from Google Play). It would have to be removed on the TV first, which erases its data, so it isn't done automatically."
            o.contains("INSTALL_FAILED_MISSING_SHARED_LIBRARY") || o.contains("INSTALL_FAILED_MISSING_FEATURE") ->
                "${app.label} needs something this TV doesn't have (such as a phone feature or Google service)."
            o.contains("INSTALL_FAILED_DUPLICATE_PERMISSION") || o.contains("INSTALL_FAILED_CONFLICTING_PROVIDER") ->
                "${app.label} clashes with an app already on the TV. Remove the other app on the TV first."
            o.contains("INSTALL_FAILED_USER_RESTRICTED") || o.contains("INSTALL_FAILED_VERIFICATION_FAILURE") || o.contains("INSTALL_FAILED_ABORTED") ->
                "The TV blocked the install. Look for an Install or Allow prompt on the TV screen, and check the TV's settings for limits on installing over ADB."
            o.contains("INSTALL_PARSE_FAILED") || o.contains("INSTALL_FAILED_INVALID_APK") || o.contains("INSTALL_FAILED_DEXOPT") ->
                "The TV couldn't read the app files. The copy may be damaged, or the app may not be built for this TV."
            o.contains("INSTALL_FAILED_MISSING_SPLIT") -> "Some parts of ${app.label} are missing on the phone, so it can't be installed on its own."
            o.contains("INSTALL_FAILED_TEST_ONLY") -> "${app.label} is a test build, which the TV won't install."
            o.contains("INSTALL_FAILED_NO_SPACE") || o.contains("No space left") -> "The TV ran out of storage."
            else -> "The TV's installer said: " + (o.trim().lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "nothing").ifEmpty { "nothing" }
        }
    }

    suspend fun send(
        tv: TvShell, app: PhoneApp, open: (PhoneApk) -> java.io.InputStream, log: ActionLog,
        onStage: (Stage, Int, Int) -> Unit = { _, _, _ -> },
        clock: () -> Long = System::currentTimeMillis,
    ): Outcome = try {
        PackageName.require(app.pkg)
        onStage(Stage.CHECKING, 0, app.apks.size)
        val target = readTarget(tv)
        val before = readTvCopy(tv, app.pkg)
        when (val v = check(app, target, before)) {
            is Verdict.Blocked -> return Outcome.Blocked(v.reason)
            is Verdict.Ok -> Unit
        }
        log.append(ActionRecord(clock(), Op.INSTALL, app.pkg, Phase.INTENT, before = if (before.versionCode != null) "preexisting:${before.versionName ?: before.versionCode}" else "absent", detail = "app2tv"))
        tv.run("rm -rf $TMP_DIR; mkdir -p $TMP_DIR")
        val remote = mutableListOf<String>()
        try {
            app.apks.forEachIndexed { i, apk ->
                onStage(Stage.COPYING, i + 1, app.apks.size)
                val path = "$TMP_DIR/${i}_${SafeName.clean(apk.name)}"
                open(apk).use { tv.pushStream(it, apk.size, path) }
                val got = tv.run("stat -c %s $path").trim().toLongOrNull()
                if (got != apk.size) { log.append(ActionRecord(clock(), Op.INSTALL, app.pkg, Phase.FAILED, detail = "app2tv transfer size")); return Outcome.TransferMismatch(apk.name) }
                remote += path
            }
            onStage(Stage.INSTALLING, app.apks.size, app.apks.size)
            // Plain install: no downgrade flag, no permission grant, no signature tricks.
            val out = if (remote.size == 1) tv.run("pm install -r ${remote[0]}") else tv.run("pm install-multiple -r ${remote.joinToString(" ")}")
            if (!out.contains("Success")) {
                val tvPrompt = out.contains("USER_RESTRICTED") || out.contains("VERIFICATION_FAILURE") || out.contains("ABORTED")
                log.append(ActionRecord(clock(), Op.INSTALL, app.pkg, Phase.FAILED, detail = "app2tv ${out.trim().take(120)}"))
                return Outcome.Failed(explain(out, app), tvPrompt)
            }
        } finally {
            runCatching { tv.run("rm -rf $TMP_DIR") }
        }
        onStage(Stage.VERIFYING, app.apks.size, app.apks.size)
        val after = readTvCopy(tv, app.pkg)
        if (after.versionCode == null || after.versionCode != app.versionCode) {
            log.append(ActionRecord(clock(), Op.INSTALL, app.pkg, Phase.FAILED, detail = "app2tv not confirmed"))
            return Outcome.Unconfirmed
        }
        log.append(ActionRecord(clock(), Op.INSTALL, app.pkg, Phase.DONE, before = if (before.versionCode != null) "preexisting:${before.versionName ?: before.versionCode}" else "absent", detail = "app2tv"))
        Outcome.Installed(app.label, after.versionName, opensOn(tv, app.pkg), updated = before.versionCode != null)
    } catch (e: TvConnectionLost) {
        Outcome.Disconnected
    }

    suspend fun opensOn(tv: TvShell, pkg: String): Opens {
        suspend fun resolves(cat: String): Boolean = Parse.lines(tv.run("cmd package resolve-activity --brief -c $cat $pkg")).any { it.startsWith("$pkg/") }
        return when {
            resolves("android.intent.category.LEANBACK_LAUNCHER") -> Opens.TV_LAUNCHER
            resolves("android.intent.category.LAUNCHER") -> Opens.PHONE_LAUNCHER_ONLY
            else -> Opens.NO_ICON
        }
    }

    fun opensText(o: Opens, label: String): String = when (o) {
        Opens.TV_LAUNCHER -> "$label is on the TV and shows in its app list."
        Opens.PHONE_LAUNCHER_ONLY -> "$label is on the TV, but it was made for phones and has no TV icon. It may not show in the home screen's app list. Open it from the TV's Settings > Apps, or with a launcher app that lists all apps."
        Opens.NO_ICON -> "$label is on the TV, but it has no screen to open (it may be a helper app)."
    }
}
