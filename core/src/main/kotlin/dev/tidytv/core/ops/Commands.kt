package dev.tidytv.core.ops

import dev.tidytv.core.lists.PackageName

/**
 * Every shell command TidyTV sends, in one place. Anything variable that goes into a command is a
 * validated package name or a component built from one; nothing else is ever interpolated.
 */
object Commands {
    const val MIRROR_DIR = "/data/local/tmp/tidytv"
    const val MIRROR_FILE = "$MIRROR_DIR/disabled.txt"
    const val APK_TMP = "/data/local/tmp/tidytv-home.apk"
    private const val HOME_ARGS = "-a android.intent.action.MAIN -c android.intent.category.HOME"

    const val GETPROP = "getprop"
    const val LIST_PACKAGES = "pm list packages"
    const val LIST_DISABLED = "pm list packages -d"
    const val RESOLVE_HOME = "cmd package resolve-activity --brief $HOME_ARGS"
    const val QUERY_HOME = "cmd package query-activities --brief $HOME_ARGS"
    const val MEMINFO = "cat /proc/meminfo"
    const val WIFI_OWNERS = "dumpsys wifi | grep -o 'cname=[^ ]*'"
    const val TV_INPUTS = "dumpsys tv_input"
    const val ENABLED_IMES = "settings get secure enabled_input_methods"
    const val DEFAULT_IME = "settings get secure default_input_method"
    const val A11Y_SERVICES = "settings get secure enabled_accessibility_services"
    const val PROVISIONED = "settings get global device_provisioned"
    const val SETUP_COMPLETE = "settings get secure user_setup_complete"
    const val READ_MIRROR = "if [ -e $MIRROR_FILE ]; then cat $MIRROR_FILE; fi; echo END"
    const val CLEAR_MIRROR = "rm -f $MIRROR_FILE; echo cleared"
    /** The on-TV undo list for apps removed for the user (REMOVE), next to the one for disables. */
    const val REMOVED_FILE = "$MIRROR_DIR/removed.txt"
    const val READ_REMOVED = "if [ -e $REMOVED_FILE ]; then cat $REMOVED_FILE; fi; echo END"
    const val CLEAR_REMOVED = "rm -f $REMOVED_FILE; echo cleared"
    /** Apps the owner installed (not preinstalled). */
    const val LIST_USER_APPS = "pm list packages -3"
    /** Asks Android to free app caches until this much space is free, i.e. all of them. Safe: caches rebuild. */
    const val TRIM_CACHES = "pm trim-caches 999G; echo trimmed"
    /** The background clear leaves this file when it's finished; the phone polls it and the free space. */
    const val TRIM_DONE_FILE = "$MIRROR_DIR/trim.done"
    /**
     * Starts the cache clear in the background, so the phone can show live progress instead of waiting on one
     * long command. It survives a dropped connection, and prints "started" at once.
     */
    const val TRIM_CACHES_BACKGROUND =
        "mkdir -p $MIRROR_DIR && rm -f $TRIM_DONE_FILE && (nohup sh -c 'pm trim-caches 999G >/dev/null 2>&1; echo done > $TRIM_DONE_FILE' >/dev/null 2>&1 &); echo started"
    const val TRIM_STATUS = "if [ -e $TRIM_DONE_FILE ]; then echo done; else echo running; fi"
    const val DISKSTATS = "dumpsys diskstats"
    const val MEMINFO_PROCS = "dumpsys meminfo"
    const val BOOT_RECEIVERS = "cmd package query-receivers --brief -a android.intent.action.BOOT_COMPLETED"
    const val DF_DATA = "df /data"
    const val INSTALL_TMP = "pm install -r $APK_TMP"
    const val REMOVE_TMP = "rm -f $APK_TMP"

    fun settingsList(namespace: String): String {
        require(namespace in NAMESPACES)
        return "settings list $namespace"
    }

    fun disable(pkg: String) = "pm disable-user --user 0 ${PackageName.require(pkg)}"
    fun enable(pkg: String) = "pm enable ${PackageName.require(pkg)}"
    fun uninstall(pkg: String) = "pm uninstall ${PackageName.require(pkg)}"
    /** Uninstall for the TV's user only; a preinstalled APK stays and [installExisting] brings it back. */
    fun removeForUser(pkg: String) = "pm uninstall -k --user 0 ${PackageName.require(pkg)}"
    fun installExisting(pkg: String) = "cmd package install-existing ${PackageName.require(pkg)}"

    /** One round trip for many packages: prints "@pkg" then its pkgFlags line (or nothing). */
    fun flags(pkgs: Collection<String>): String {
        pkgs.forEach { PackageName.require(it) }
        return "for p in ${pkgs.joinToString(" ")}; do echo \"@${'$'}p\"; dumpsys package ${'$'}p | grep -m1 pkgFlags; done"
    }

    /** Adds each package to an on-TV undo list unless it is already there (a retry used to list one twice). */
    private fun appendOnce(file: String, pkgs: Collection<String>): String =
        "mkdir -p $MIRROR_DIR && for p in ${pkgs.joinToString(" ")}; do grep -qxF \$p $file 2>/dev/null || printf '%s\\n' \$p >> $file; done && echo ok"

    /** Appends to the on-TV undo list and prints "ok" only if the write worked. */
    fun mirror(pkgs: Collection<String>): String {
        pkgs.forEach { PackageName.require(it) }
        return appendOnce(MIRROR_FILE, pkgs)
    }

    /** Appends to the on-TV list of removed apps and prints "ok" only if the write worked. */
    fun mirrorRemoved(pkgs: Collection<String>): String {
        pkgs.forEach { PackageName.require(it) }
        return appendOnce(REMOVED_FILE, pkgs)
    }

    fun unmirrorRemoved(pkg: String): String {
        val p = PackageName.require(pkg)
        return "if [ -e $REMOVED_FILE ]; then grep -vxF $p $REMOVED_FILE > $REMOVED_FILE.tmp; mv $REMOVED_FILE.tmp $REMOVED_FILE; fi; echo ok"
    }

    /** Drops one package from the on-TV undo list (it was turned back on by itself); prints "ok". */
    fun unmirror(pkg: String): String {
        val p = PackageName.require(pkg)
        return "if [ -e $MIRROR_FILE ]; then grep -vxF $p $MIRROR_FILE > $MIRROR_FILE.tmp; mv $MIRROR_FILE.tmp $MIRROR_FILE; fi; echo ok"
    }

    fun resolveHomeOf(pkg: String) = "$RESOLVE_HOME ${PackageName.require(pkg)}"
    fun setHome(component: String) = "cmd package set-home-activity --user 0 ${component(component)}"
    fun addHomeRole(pkg: String) =
        "cmd role add-role-holder --user 0 android.app.role.HOME ${PackageName.require(pkg)}"

    /** Where the cleanyourtv home screen reads config.json and theme_revision (tools/theme.sh). */
    fun configDir(pkg: String) = "/sdcard/Android/data/${PackageName.require(pkg)}/files"
    fun makeConfigDir(pkg: String) = "mkdir -p ${configDir(pkg)} && echo ok"
    fun writeRevision(pkg: String, revision: Long) = "printf '%s' '$revision' > ${configDir(pkg)}/theme_revision && echo ok"
    // Looks for the Apple TV style home screen (see LauncherLook and upstream tools/theme.sh).
    private val LOOK_FILE = Regex("^(wallpapers/)?[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")
    private fun lookFile(name: String): String { require(LOOK_FILE.matches(name)) { "bad file name" }; return name }
    fun lookStage(name: String) = "/data/local/tmp/tidytv-look-" + lookFile(name).replace('/', '-')

    /**
     * Writes a staged file through the launcher's ThemeProvider, so the launcher owns it; falls back to cp.
     * Android 8 and 9 print "Unsupported operation" for `content write` but exit 0, so that counts as failure.
     */
    fun lookPut(pkg: String, name: String): String {
        val dir = configDir(pkg)
        val f = lookFile(name)
        val stage = lookStage(f)
        val sub = if (f.startsWith("wallpapers/")) "mkdir -p $dir/wallpapers 2>/dev/null; " else ""
        return "${sub}rm -f $dir/$f 2>/dev/null; { o=\$(content write --uri content://${PackageName.require(pkg)}.theme/$f < $stage 2>&1) && " +
            "case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $stage $dir/$f 2>/dev/null; rm -f $stage; echo put"
    }

    fun lookDelete(pkg: String, name: String) =
        "rm -f ${configDir(pkg)}/${lookFile(name)} 2>/dev/null; content delete --uri content://${PackageName.require(pkg)}.theme/${lookFile(name)} 2>/dev/null; echo deleted"

    fun lookClearWallpapers(pkg: String): String {
        val dir = configDir(pkg) + "/wallpapers"
        return "mkdir -p $dir 2>/dev/null; rm -f $dir/*.jpg $dir/*.jpeg $dir/*.png $dir/*.webp 2>/dev/null; " +
            "content delete --uri content://${PackageName.require(pkg)}.theme/wallpapers 2>/dev/null; echo cleared"
    }

    fun lookReadConfig(pkg: String) = "cat ${configDir(pkg)}/config.json 2>/dev/null; echo"

    /**
     * Restarts the home screen so it rereads its look (upstream tools/theme.sh). A force-stop drops the
     * app's accessibility services from the enabled list, so the list is put back when it had them.
     */
    fun restartLook(pkg: String): String {
        val p = PackageName.require(pkg)
        return "a=\$(settings get secure enabled_accessibility_services); am force-stop $p; " +
            "case \"\$(cmd package resolve-activity --brief $HOME_ARGS | tail -n 1)\" in $p/*) am start $HOME_ARGS >/dev/null ;; *) am start -n $p/.MainActivity >/dev/null ;; esac; " +
            "case \"\$a\" in *$p*) sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1; sleep 1; " +
            "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; " +
            "settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac; echo restarted"
    }

    fun grantLocation(pkg: String) = "pm grant ${PackageName.require(pkg)} android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo granted"

    /**
     * The home screen's own editing features, off by default upstream: hold OK on an app for Move / Remove / App info
     * (`tile_menu`), an App library to add apps back (`app_library`), and a settings page to reorder apps and pick a
     * wallpaper (`launcher_settings_button`). Without them people can't change the apps on their home screen.
     */
    val EDITABLE_HOME = listOf("tile_menu", "app_library", "launcher_settings_button")

    /** config.json for the cleanyourtv home screen: the dock apps in order, and the editing features switched on. */
    fun dockConfig(dockApps: List<String>): String {
        dockApps.forEach { PackageName.require(it) }
        val flags = EDITABLE_HOME.joinToString(", ") { "\"$it\": true" }
        return "{\"dock_apps\": [" + dockApps.joinToString(", ") { "\"$it\"" } + "], $flags}\n"
    }

    fun startLauncher(component: String, dockApps: List<String>, stamp: Long): String {
        val base = "am start -W -n ${component(component)}"
        if (dockApps.isEmpty()) return base
        dockApps.forEach { PackageName.require(it) }
        return "$base --es stamp $stamp --es apps ${dockApps.joinToString(",")}"
    }

    const val KEY_HOME = "input keyevent KEYCODE_HOME"
    /** Which app is on screen right now ("mFocusedApp=ActivityRecord{... u0 pkg/.Activity t12}"). */
    const val FOCUSED_APP = "dumpsys activity activities | grep mFocusedApp | head -1"
    /** Opens TV Settings: an app that is not a home screen, to press Home from. */
    const val OPEN_SETTINGS = "am start -a android.settings.SETTINGS"

    /** Restarts the TV; the connection drops at once. */
    const val REBOOT = "reboot"

    /** Opens an app's page in the TV's Play Store (the owner then presses Install with the remote). */
    fun playStorePage(pkg: String) = "am start -a android.intent.action.VIEW -d market://details?id=${PackageName.require(pkg)}"

    fun isInstalled(pkg: String) = "pm list packages ${PackageName.require(pkg)}"

    /** Where Send files puts things: a folder the TV's file manager and media apps can see. */
    const val SEND_DIR = "/sdcard/Download/TidyTV"
    const val LIST_SEND_DIR = "mkdir -p $SEND_DIR && ls -1 $SEND_DIR; echo ok"
    private val SEND_PATH = Regex("^/sdcard/[A-Za-z]{3,12}/TidyTV$")
    fun listSendDir(path: String): String { require(SEND_PATH.matches(path)) { "bad folder" }; return "mkdir -p $path && ls -1 $path; echo ok" }
    const val DF_SDCARD = "df /sdcard"

    private val NAMESPACES = setOf("global", "secure", "system")

    /** "pkg/.Cls" or "pkg/pkg.Cls": both halves made only of package-name characters. */
    fun component(c: String): String {
        val pkg = c.substringBefore('/')
        val cls = c.substringAfter('/', "")
        require(PackageName.isValid(pkg) && cls.isNotEmpty() && Regex("^\\.?[A-Za-z_][A-Za-z0-9_$.]*$").matches(cls)) {
            "not a component: ${c.take(80)}"
        }
        return c
    }
}
