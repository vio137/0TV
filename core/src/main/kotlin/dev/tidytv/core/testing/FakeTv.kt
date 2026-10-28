package dev.tidytv.core.testing

import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.ops.Commands
import dev.tidytv.core.ops.TvConnectionLost
import dev.tidytv.core.ops.TvShell

/**
 * A stateful simulated Android TV that answers the commands TidyTV sends, with the output formats
 * real TVs print. Used by every unit test and by the app's demo mode. Failure knobs let tests
 * reproduce field problems: dropped connections, refused commands, changes that don't stick,
 * install errors and Home landing on Google TV's setup app.
 */
class FakeTv(
    val props: MutableMap<String, String> = defaultProps(),
    val installed: MutableSet<String> = mutableSetOf(),
    val disabled: MutableSet<String> = mutableSetOf(),
    val persistent: MutableSet<String> = mutableSetOf(),
    /** HOME activities by component, with their intent-filter priority. */
    val homeClaimants: MutableMap<String, Int> = mutableMapOf(),
    var preferredHome: String? = null,
    val settings: MutableMap<String, String> = mutableMapOf("secure/android_id" to "a1b2c3d4e5f60718"),
    val files: MutableMap<String, String> = mutableMapOf(),
    var wifiOwner: String = "android.uid.system",
    val liveOwners: MutableList<String> = mutableListOf(),
) : TvShell {
    val commands = mutableListOf<String>()
    val pushed = mutableMapOf<String, ByteArray>()

    // Remote and files
    /** Key codes pressed through the remote, in order. */
    val keys = mutableListOf<String>()
    /** Text typed through the remote, already decoded from `input text` encoding. */
    val typed = mutableListOf<String>()
    /** Components opened with `am start -n`. */
    val launched = mutableListOf<String>()
    /** Apps the TV home screen shows (pkg/activity). */
    var tvApps: List<String> = emptyList()
    /** Whether [openSession] gives a kept-open shell, and what went through it. */
    var sessionSupported = false
    val sessionCommands = mutableListOf<String>()
    /** Play Store pages opened on the TV; [playStoreInstalls] makes the owner press Install straight away. */
    val playStoreOpened = mutableListOf<String>()
    var playStoreInstalls = false
    /** Reboots so far, and what a firmware update brings back on the next boot. */
    var reboots = 0
    /** LauncherLook: files written through the theme provider, and Home restarts for a new look. */
    val lookWrites = mutableListOf<String>()
    var lookRestarts = 0
    var reEnableOnReboot: MutableSet<String> = mutableSetOf()
    var installOnReboot: MutableSet<String> = mutableSetOf()
    /** Free space on /sdcard in 1K blocks, for `df`. */
    var freeKb: Long = 8_000_000

    // Failure knobs
    /** Throw [TvConnectionLost] when command number N (1-based) arrives. */
    var dropAtCommand: Int? = null
    /** The foreground Android user `am get-current-user` reports. */
    var currentUser: String = "0"
    /** Throw on the first command matching this prefix. */
    var dropOnPrefix: String? = null
    var refuseDisable: MutableSet<String> = mutableSetOf()
    var disableDoesNotStick: MutableSet<String> = mutableSetOf()
    var enableDoesNotStick: MutableSet<String> = mutableSetOf()
    var installAnswer: String = "Success"
    /** Which of TidyTV's home screens the next pushed APK is. */
    var installAs: HomeApp = HomeApp.SIMPLE
    var startAnswer: String? = null
    var mirrorWritable = true
    /** Apps the owner installed (`pm list packages -3`); uninstalling one removes it for good. */
    val userApps: MutableSet<String> = mutableSetOf()
    /** Preinstalled apps uninstalled for user 0: still on /system, so install-existing brings them back. */
    val removedForUser: MutableSet<String> = mutableSetOf()
    /** Apps whose `pm uninstall --user 0` fails. */
    val refuseRemove: MutableSet<String> = mutableSetOf()
    /** For the All apps view and Free up space: RAM in use (MB), BOOT_COMPLETED receivers, app/data/cache MB. */
    val ramMb: MutableMap<String, Int> = mutableMapOf()
    val bootPkgs: MutableSet<String> = mutableSetOf()
    val diskMb: MutableMap<String, Triple<Int, Int, Int>> = mutableMapOf()
    var dataFreeMb = 5800
    var dataTotalMb = 10000
    var cachesTrimmed = 0
    /** Background clear: how much is left to free, how much each look at `df` frees, and how often it was started. */
    var trimRemainingMb = 0
    var trimMbPerPoll = 120
    var trimStarted = 0
    /** Like a real TV, `dumpsys diskstats` keeps reporting the old per-app caches after a clear (it refreshes ~daily). */
    var staleDiskstats = true
    /** Google TV 14 quirk: the setup app's RecoveryActivity wins Home over the preference. */
    var wraithBeatsPreference = false
    /**
     * A real TCL G06 (Google TV 11): `set-home-activity` and the HOME role are accepted but don't change where
     * the Home key goes. The enabled launcher with the highest priority always wins.
     */
    var strictPriority = false
    /** The HOME role holder (`cmd role add-role-holder`); with [strictPriority] it has no effect on [home]. */
    var homeRole: String? = null
    /** The app on screen: set by `am start`, and by KEYCODE_HOME to wherever [home] points. */
    var focused: String? = null
    /** Home presses, to check the verification really pressed Home. */
    var homePresses = 0
    var getpropBroken = false
    var listPackagesGarbage = false

    override suspend fun push(bytes: ByteArray, remotePath: String) {
        tick("push $remotePath")
        pushed[remotePath] = bytes
        files[remotePath] = "<apk ${bytes.size} bytes>"
        // A real APK names its package in the manifest (UTF-16 in binary XML): install that home screen.
        if (remotePath == Commands.APK_TMP) HomeApp.entries.firstOrNull { app ->
            val utf16 = app.pkg.toByteArray(Charsets.UTF_16LE)
            bytes.indexOf(utf16) >= 0 || bytes.indexOf(app.pkg.toByteArray()) >= 0
        }?.let { installAs = it }
    }

    /** APK bytes by path, for icon tests. Paths come from [apkPaths]. */
    val apkFiles = mutableMapOf<String, ByteArray>()
    val apkPaths = mutableMapOf<String, String>()

    override suspend fun pull(remotePath: String): ByteArray? {
        tick("pull $remotePath")
        return apkFiles[remotePath]
    }

    override suspend fun run(command: String): String {
        tick(command)
        if (command.trim() == dev.tidytv.core.ops.Commands.REBOOT) {
            reboots++
            disabled -= reEnableOnReboot
            installed += installOnReboot
            throw TvConnectionLost("rebooting (simulated)")
        }
        return answer(command)
    }

    /** What installing Projectivy from the Play Store adds. */
    fun installProjectivy() {
        installed += HomeApp.PROJECTIVY.pkg
        homeClaimants[HomeApp.PROJECTIVY.pkg + "/.ui.home.MainActivity"] = 0
    }

    override suspend fun openSession(): dev.tidytv.core.ops.ShellSession? = if (!sessionSupported) null else object : dev.tidytv.core.ops.ShellSession {
        override suspend fun send(command: String) {
            tick(command)
            sessionCommands += command
            answer(command)
        }
        override fun close() = Unit
    }

    private var attempts = 0

    /** A dropped command never reaches the TV, so it is not recorded in [commands]. */
    private fun tick(command: String) {
        attempts++
        val drop = dropAtCommand == attempts || dropOnPrefix?.let { command.startsWith(it) } == true
        if (drop) {
            dropAtCommand = null
            dropOnPrefix = null
            throw TvConnectionLost("connection reset (simulated) at: $command")
        }
        commands += command
    }

    /** Simulates the firmware putting its own Home preference back. */
    fun setHomePreference(component: String) { preferredHome = component }

    fun pkgOf(component: String) = component.substringBefore('/')
    private fun enabled(pkg: String) = pkg in installed && pkg !in disabled
    private fun claimantsNow() = homeClaimants.keys.filter { enabled(pkgOf(it)) }.toSet()

    fun home(): String? {
        val candidates = claimantsNow()
        if (strictPriority) return strictHome(candidates)
        if (wraithBeatsPreference) candidates.firstOrNull { pkgOf(it) == Launchers.SETUPWRAITH }?.let { return it }
        preferredHome?.takeIf { it in candidates }?.let { return it }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()
        val top = candidates.maxOf { homeClaimants.getValue(it) }
        val best = candidates.filter { homeClaimants.getValue(it) == top }
        return if (best.size == 1) best.single() else "android/com.android.internal.app.ResolverActivity"
    }

    /** Android forgets the Home preference whenever the set of HOME apps changes. */
    private fun strictHome(candidates: Set<String>): String? {
        if (candidates.isEmpty()) return null
        val top = candidates.maxOf { homeClaimants.getValue(it) }
        val best = candidates.filter { homeClaimants.getValue(it) == top }
        return if (best.size == 1) best.single() else "android/com.android.internal.app.ResolverActivity"
    }

    private inline fun changingHomeSet(block: () -> Unit) {
        val before = claimantsNow()
        block()
        if (claimantsNow() != before) preferredHome = null
    }

    private fun answer(cmd: String): String {
        val c = cmd.trim()
        val args = c.split(Regex("\\s+"))
        return when {
            c == Commands.GETPROP -> if (getpropBroken) "" else props.entries.joinToString("\n") { "[${it.key}]: [${it.value}]" } + "\n"
            c.startsWith("getprop ") -> (props[args[1]] ?: "") + "\n"

            c.startsWith("am start -a android.intent.action.VIEW -d market://details?id=") -> {
                val p = c.substringAfter("id=")
                playStoreOpened += p
                if (playStoreInstalls && p == HomeApp.PROJECTIVY.pkg) installProjectivy()
                "Starting: Intent { act=android.intent.action.VIEW dat=market://details?id=$p }\n"
            }
            c.startsWith("pm list packages ") && !args[3].startsWith("-") ->
                if (args[3] in installed || args[3] == "android") "package:${args[3]}\n" else ""
            c == Commands.LIST_PACKAGES -> if (listPackagesGarbage) "Error: Could not access the Package Manager.\n"
                else installed.sorted().joinToString("") { "package:$it\n" }
            c == Commands.LIST_DISABLED -> disabled.filter { it in installed }.sorted().joinToString("") { "package:$it\n" }

            c.startsWith("pm disable-user --user 0 ") -> {
                val p = args.last()
                when {
                    p !in installed -> "Error: java.lang.IllegalArgumentException: Unknown package: $p\n"
                    p in refuseDisable || p in persistent ->
                        "Error: java.lang.SecurityException: Shell cannot change component state for $p to 3\n"
                    else -> {
                        if (p !in disableDoesNotStick) changingHomeSet { disabled += p }
                        "Package $p new state: disabled-user\n"
                    }
                }
            }
            c.startsWith("pm enable ") -> {
                val p = args.last()
                if (p !in installed) "Error: java.lang.IllegalArgumentException: Unknown package: $p\n"
                else {
                    if (p !in enableDoesNotStick) changingHomeSet { disabled -= p }
                    "Package $p new state: enabled\n"
                }
            }
            c.startsWith("pm uninstall -k --user 0 ") -> {
                val p = args.last()
                when {
                    p !in installed -> "Failure [not installed for 0]\n"
                    p in refuseRemove -> "Failure [DELETE_FAILED_INTERNAL_ERROR]\n"
                    else -> {
                        changingHomeSet { installed -= p; disabled -= p }
                        if (p !in userApps) removedForUser += p else userApps -= p
                        "Success\n"
                    }
                }
            }
            c.startsWith("cmd package install-existing ") -> {
                val p = args.last()
                if (p in removedForUser) {
                    changingHomeSet { installed += p }
                    removedForUser -= p
                    "Package $p installed for user: 0\n"
                } else "Error: android.content.pm.PackageManager\$NameNotFoundException: Package $p doesn't exist\n"
            }
            c == Commands.LIST_USER_APPS -> userApps.filter { it in installed }.sorted().joinToString("") { "package:$it\n" }
            c == Commands.MEMINFO_PROCS -> "Applications Memory Usage (in Kilobytes):\n\nTotal PSS by process:\n" +
                ramMb.filterKeys { it in installed && it !in disabled }.entries.sortedByDescending { it.value }
                    .mapIndexed { i, (p, mb) -> "    ${"%,d".format(mb * 1024)}K: $p (pid ${1000 + i})\n" }.joinToString("") +
                "\nTotal PSS by OOM adjustment:\n"
            c == Commands.BOOT_RECEIVERS -> bootPkgs.filter { it in installed && it !in disabled }.sorted().joinToString("") { "$it/.BootReceiver\n" }
            c == Commands.DISKSTATS -> {
                val rows = diskMb.filterKeys { it in installed }.entries.sortedBy { it.key }
                fun arr(f: (Triple<Int, Int, Int>) -> Int) = rows.joinToString(",") { (f(it.value).toLong() * 1_048_576).toString() }
                "Data-Free: ${dataFreeMb * 1024}K / ${dataTotalMb * 1024}K total = ${dataFreeMb * 100 / dataTotalMb}% free\n" +
                    "Package Names: [" + rows.joinToString(",") { "\"${it.key}\"" } + "]\n" +
                    "App Sizes: [${arr { it.first }}]\nApp Data Sizes: [${arr { it.second }}]\nCache Sizes: [${arr { it.third }}]\n"
            }
            c == Commands.TRIM_CACHES -> {
                val freed = diskMb.values.sumOf { it.third }
                dataFreeMb += freed
                diskMb.replaceAll { _, v -> Triple(v.first, v.second, 0) }
                cachesTrimmed++
                "trimmed\n"
            }
            c == Commands.TRIM_CACHES_BACKGROUND -> {
                files.remove(Commands.TRIM_DONE_FILE)
                trimRemainingMb = diskMb.values.sumOf { it.third }
                if (trimRemainingMb == 0) files[Commands.TRIM_DONE_FILE] = "done" // nothing to clear: finished at once
                trimStarted++
                "started\n"
            }
            c == Commands.TRIM_STATUS -> if (Commands.TRIM_DONE_FILE in files) "done\n" else "running\n"
            c == Commands.DF_DATA -> {
                // Each look at the free space finds more freed, like the real TV working through its apps.
                if (trimRemainingMb > 0) {
                    val step = minOf(trimRemainingMb, trimMbPerPoll)
                    trimRemainingMb -= step
                    dataFreeMb += step
                    if (trimRemainingMb == 0) { cachesTrimmed++; files[Commands.TRIM_DONE_FILE] = "done"; if (!staleDiskstats) diskMb.replaceAll { _, v -> Triple(v.first, v.second, 0) } }
                }
                "Filesystem      1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-12 ${dataTotalMb * 1024} ${(dataTotalMb - dataFreeMb) * 1024} ${dataFreeMb * 1024}  ${(dataTotalMb - dataFreeMb) * 100 / dataTotalMb}% /data/user/0\n"
            }
            c.startsWith("pm uninstall ") -> {
                val p = args.last()
                if (p !in installed) "Failure [DELETE_FAILED_INTERNAL_ERROR]\n" else {
                    changingHomeSet { installed -= p; disabled -= p }
                    "Success\n"
                }
            }
            c == Commands.INSTALL_TMP -> {
                if (Commands.APK_TMP !in files) "Error: Unable to open file: ${Commands.APK_TMP}\n"
                else {
                    if (installAnswer.startsWith("Success")) changingHomeSet {
                        installed += installAs.pkg
                        homeClaimants[installAs.component] = 0
                    }
                    "Performing Streamed Install\n$installAnswer\n"
                }
            }
            c == Commands.REMOVE_TMP -> { files.remove(Commands.APK_TMP); "" }

            c == Commands.RESOLVE_HOME -> home()?.let { "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n$it\n" }
                ?: "No activity found\n"
            c.startsWith(Commands.RESOLVE_HOME + " ") -> {
                val p = args.last()
                homeClaimants.keys.firstOrNull { pkgOf(it) == p && enabled(p) }
                    ?.let { "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n$it\n" }
                    ?: "No activity found\n"
            }
            c == Commands.QUERY_HOME -> claimantsNow().sorted().joinToString("") { "  $it\n" }
            c.startsWith("cmd package set-home-activity") -> {
                val comp = args.last()
                if (comp in claimantsNow()) { preferredHome = comp; "Success: Home activity set to $comp\n" }
                else "Error: Package $comp is not a valid home activity\n"
            }
            c.startsWith("cmd role add-role-holder") -> {
                val p = args.last()
                homeRole = p
                if (!strictPriority) homeClaimants.keys.firstOrNull { pkgOf(it) == p && enabled(p) }?.let { preferredHome = it }
                ""
            }
            c.startsWith("am start -W -n ") -> {
                startAnswer ?: run {
                    val comp = args[4]
                    if (enabled(pkgOf(comp))) { focused = pkgOf(comp); "Starting: Intent { cmp=$comp }\nStatus: ok\nLaunchState: COLD\nActivity: $comp\nTotalTime: 412\nWaitTime: 420\nComplete\n" }
                    else "Starting: Intent { cmp=$comp }\nError type 3\nError: Activity class {$comp} does not exist.\n"
                }
            }
            c == Commands.KEY_HOME -> { keys += "KEYCODE_HOME"; homePresses++; home()?.let { focused = pkgOf(it) }; "" }
            c == Commands.FOCUSED_APP -> focused?.let { "  mFocusedApp=ActivityRecord{1a2b3c u0 $it/.MainActivity t42}\n" } ?: ""
            c == Commands.OPEN_SETTINGS -> { focused = "com.android.tv.settings"; "Starting: Intent { act=android.settings.SETTINGS }\n" }
            // Looks for the Apple TV style home screen (LauncherLook).
            c.contains("content write --uri content://") -> {
                val f = c.substringAfter(".theme/").substringBefore(" <")
                val stage = c.substringAfter(" < ").substringBefore(" 2>&1")
                val dest = c.substringAfter("rm -f ").substringBefore(" 2>/dev/null")
                val bytes = pushed.remove(stage)
                if (bytes != null) {
                    pushed[dest] = bytes
                    lookWrites += f
                    if (f == "theme_revision") files[dest] = String(bytes)
                }
                "put\n"
            }
            c.contains("content delete --uri content://") && c.endsWith("echo deleted") -> {
                pushed.remove(c.substringAfter("rm -f ").substringBefore(" 2>/dev/null")); "deleted\n"
            }
            c.contains("content delete --uri content://") && c.endsWith("echo cleared") -> {
                val dir = c.substringAfter("mkdir -p ").substringBefore(" 2>/dev/null")
                pushed.keys.removeAll { it.startsWith("$dir/") }; "cleared\n"
            }
            c.startsWith("cat /sdcard/Android/data/") && c.contains("config.json") ->
                (pushed[c.substringAfter("cat ").substringBefore(" 2>/dev/null")]?.let { String(it) } ?: "") + "\n"
            c.contains("am force-stop ") && c.endsWith("echo restarted") -> { lookRestarts++; "restarted\n" }
            c.startsWith("mkdir -p /sdcard/Android/data/") -> "ok\n"
            c.startsWith("printf '%s' '") && c.contains("theme_revision") -> {
                files[c.substringAfter("> ").substringBefore(" &&")] = c.substringAfter("'%s' '").substringBefore("'"); "ok\n"
            }
            c.startsWith("pm grant ") -> "granted\n"
            c.startsWith("pm path ") -> apkPaths[args.last()]?.let { "package:$it\n" } ?: ""
            c.startsWith("stat -c %s ") -> apkFiles[args.last()]?.let { "${it.size}\n" } ?: "stat: cannot access '${args.last()}': No such file or directory\n"
            c.startsWith("echo ") && !c.contains(";") && !c.contains("&&") -> c.removePrefix("echo ").trim('"') + "\n"

            c.startsWith("for p in ") && c.contains("pkgFlags") -> {
                val pkgs = c.removePrefix("for p in ").substringBefore(";").trim().split(" ")
                pkgs.joinToString("") { p ->
                    val flags = when {
                        p !in installed -> ""
                        p in persistent -> "    pkgFlags=[ SYSTEM HAS_CODE PERSISTENT ALLOW_CLEAR_USER_DATA ]\n"
                        else -> "    pkgFlags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ]\n"
                    }
                    "@$p\n$flags"
                }
            }
            c.startsWith("mkdir -p ${Commands.MIRROR_DIR} && for p in ") -> {
                if (!mirrorWritable) "printf: write error: Read-only file system\n" else {
                    val pkgs = c.substringAfter("for p in ").substringBefore(";").split(" ")
                    val file = c.substringAfter(">> ").substringBefore(";")
                    val have = files[file].orEmpty().lines().filter { it.isNotEmpty() }.toMutableList()
                    for (p in pkgs) if (p !in have) have += p
                    files[file] = have.joinToString("") { "$it\n" }
                    "ok\n"
                }
            }
            c == Commands.READ_MIRROR -> files[Commands.MIRROR_FILE].orEmpty() + "END\n"
            c == Commands.READ_REMOVED -> files[Commands.REMOVED_FILE].orEmpty() + "END\n"
            c.startsWith("if [ -e ${Commands.MIRROR_DIR}/") && c.contains(" grep -vxF ") -> {
                val file = c.removePrefix("if [ -e ").substringBefore(' ')
                val pkg = c.substringAfter("grep -vxF ").substringBefore(' ')
                files[file]?.let { cur -> files[file] = cur.lines().filter { it.isNotEmpty() && it != pkg }.joinToString("") { "$it\n" } }
                "ok\n"
            }
            c == Commands.CLEAR_MIRROR -> { files.remove(Commands.MIRROR_FILE); "cleared\n" }
            c == Commands.CLEAR_REMOVED -> { files.remove(Commands.REMOVED_FILE); "cleared\n" }

            c == Commands.MEMINFO -> "MemTotal:        2009240 kB\nMemFree:          112340 kB\nMemAvailable:     ${memAvailable()} kB\n"
            c == Commands.WIFI_OWNERS -> "cname=$wifiOwner\n"
            c == Commands.TV_INPUTS -> liveOwners.joinToString("") { "  inputId: $it/.HdmiService/HW5\n" }

            c == "am get-current-user" -> "$currentUser\n"
            c.startsWith("settings --user 0 get ") -> (settings["${args[4]}/${args[5]}"] ?: "null") + "\n"
            c.startsWith("settings get ") -> (settings["${args[2]}/${args[3]}"] ?: "null") + "\n"
            c.startsWith("settings put ") -> { settings["${args[2]}/${args[3]}"] = args[4]; "" }
            c.startsWith("settings delete ") -> { settings.remove("${args[2]}/${args[3]}"); "Deleted 1 rows\n" }
            c.startsWith("settings list ") -> settings.filterKeys { it.startsWith(args[2] + "/") }
                .entries.joinToString("") { "${it.key.substringAfter('/')}=${it.value}\n" }

            c.startsWith("input keyevent ") -> { keys += args[2]; "" }
            c.startsWith("input text ") -> {
                val raw = c.removePrefix("input text ").removeSurrounding("'").replace("'\\''", "'")
                typed += raw.replace("%s", " ").replace("\\%", "%"); ""
            }
            c.startsWith("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER") ->
                if (tvApps.isEmpty()) "No activities found\n"
                else "${tvApps.size} activities found:\n" + tvApps.mapIndexed { i, comp ->
                    "  Activity #$i:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n    $comp\n"
                }.joinToString("")
            c.startsWith("cmd package query-activities") -> "No activities found\n"
            c.startsWith("am start -n ") -> { launched += args[3]; "Starting: Intent { cmp=${args[3]} }\n" }
            c.startsWith("mkdir -p /sdcard/") && c.endsWith("/TidyTV; echo ok") || c == Commands.LIST_SEND_DIR -> {
                val dir = if (c == Commands.LIST_SEND_DIR) Commands.SEND_DIR else c.removePrefix("mkdir -p ").substringBefore(" &&")
                pushed.keys.filter { it.startsWith("$dir/") }.joinToString("") { it.removePrefix("$dir/") + "\n" } + "ok\n"
            }
            c.startsWith("df ") -> "Filesystem     1K-blocks    Used Available Use% Mounted on\n/dev/fuse       30000000 22000000 $freeKb  74% /storage/emulated\n"

            else -> "/system/bin/sh: ${args.first()}: not found\n"
        }
    }

    /** Each package turned off frees roughly 18 MB in this simulation, for the "Freed X MB" number. */
    private fun memAvailable(): Long = 620_000L + disabled.size * 18_000L

    companion object {
        fun defaultProps() = mutableMapOf(
            "ro.product.model" to "Smart TV Pro",
            "ro.product.device" to "G05",
            "ro.product.name" to "G05_4K_US",
            "ro.product.brand" to "TCL",
            "ro.product.manufacturer" to "TCL",
            "ro.build.version.sdk" to "30",
            "ro.build.version.release" to "11",
            "ro.serialno" to "TCLG05TEST01",
        )

        /** A TCL Google TV with the stock launcherx Home, setupwraith and the usual bloat installed. */
        fun googleTv(sdk: Int = 30): FakeTv = FakeTv().apply {
            props["ro.build.version.sdk"] = sdk.toString()
            props["ro.build.version.release"] = if (sdk >= 34) "14" else "11"
            installed += BASE + listOf(Launchers.LAUNCHERX, Launchers.SETUPWRAITH)
            homeClaimants[Launchers.LAUNCHERX + "/.MainActivity"] = 2
            homeClaimants[Launchers.SETUPWRAITH + "/.RecoveryActivity"] = 1
            homeClaimants["com.android.tv.settings/.FallbackHome"] = -1000
            installed += "com.android.tv.settings"
            preferredHome = Launchers.LAUNCHERX + "/.MainActivity"
            persistent += "com.android.systemui"
            liveOwners += "com.tcl.tvinput"
            settings["secure/enabled_input_methods"] = "com.google.android.leanback.ime/.LeanbackImeService"
            settings["secure/default_input_method"] = "com.google.android.leanback.ime/.LeanbackImeService"
            tvApps = listOf("com.netflix.ninja/.MainActivity", "com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity", "com.android.tv.settings/.MainSettings")
            settings["global/device_provisioned"] = "1"
            settings["secure/user_setup_complete"] = "1"
            settings["global/window_animation_scale"] = "1.0"
            // What a real TCL G06 showed (docs/research/tcl-g06-analysis.md), for the All apps view and Free up space.
            ramMb += mapOf(Launchers.LAUNCHERX to 151, "com.android.vending" to 101, "com.google.android.katniss" to 83,
                "com.google.android.gms" to 80, "com.google.android.apps.mediashell" to 42, "com.tcl.ttvs" to 23, "com.tcl.tv" to 18,
                "com.tcl.guard" to 14, "com.android.systemui" to 27, Launchers.SETUPWRAITH to 11)
            bootPkgs += listOf("com.tcl.guard", "com.tcl.ttvs", "com.tcl.partnercustomizer", "com.google.android.katniss",
                "com.google.android.apps.mediashell", "com.google.android.gms", "com.netflix.ninja", "com.google.android.youtube.tv")
            diskMb += mapOf(Launchers.LAUNCHERX to Triple(35, 184, 167), "com.tcl.waterfall.overseas" to Triple(52, 73, 68),
                "com.google.android.youtube.tv" to Triple(87, 89, 4), "com.google.android.gms" to Triple(132, 106, 3),
                "com.android.vending" to Triple(82, 30, 19), "com.google.android.katniss" to Triple(51, 13, 6),
                "com.netflix.ninja" to Triple(96, 1, 0), "com.tcl.ttvs" to Triple(12, 4, 2))
        }

        /**
         * The real TCL G06 from docs/research/tcl-g06-analysis.md: set-home-activity and the HOME role are accepted
         * but the enabled launcher with the highest priority always owns the Home key (launcherx 2, setupwraith 1,
         * ours 0, fallback -1000). The saved Wi-Fi belongs to the system, so the setup app can be turned off.
         */
        fun tclG06(): FakeTv = googleTv().apply {
            strictPriority = true
            homeRole = Launchers.LAUNCHERX
            focused = Launchers.LAUNCHERX
            wifiOwner = "android.uid.system:1000"
        }

        /** Classic Android TV 9 (Mi Box S style) with com.google.android.tvlauncher. */
        fun androidTv9(): FakeTv = FakeTv().apply {
            props["ro.build.version.sdk"] = "28"
            props["ro.build.version.release"] = "9"
            props["ro.product.model"] = "MIBOX4"
            props["ro.product.device"] = "oneday"
            props["ro.product.brand"] = "Xiaomi"
            props["ro.product.manufacturer"] = "Xiaomi"
            installed += BASE + "com.google.android.tvlauncher"
            homeClaimants["com.google.android.tvlauncher/.MainActivity"] = 2
            homeClaimants["com.android.tv.settings/.FallbackHome"] = -1000
            installed += "com.android.tv.settings"
            settings["global/device_provisioned"] = "1"
            settings["secure/user_setup_complete"] = "1"
        }

        val BASE = listOf(
            "android", "com.android.systemui", "com.android.vending", "com.google.android.gms",
            "com.google.android.leanback.ime", "com.tcl.tvinput", "com.tcl.tv",
            "com.tcl.tv.plus", "com.tcl.channelplus", "com.tcl.tv.tclchannel", "com.tcl.waterfall.overseas",
            "com.tcl.partnercustomizer", "com.tcl.messagebox", "com.tcl.ttvs", "com.tcl.guard",
            "com.google.android.katniss", "com.google.android.apps.mediashell", "com.google.android.feedback",
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.google.android.youtube.tvmusic",
            "com.google.android.play.games",
        )
    }
}

private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}
