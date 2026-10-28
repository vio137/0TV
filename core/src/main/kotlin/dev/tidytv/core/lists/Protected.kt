package dev.tidytv.core.lists

/**
 * Packages TidyTV never disables, whatever a list says. Upstream PROMPT.md: never disable the TV and
 * input services (HDMI, tuner), the input menu, remote and Bluetooth pairing, the keyboard, Play
 * Store or Play Services. Over-matching is fine: a protected package is simply left on.
 *
 * Home-screen apps are not here: they are never part of the clean-up and only the launcher
 * handover may turn them off (see [Launchers]).
 */
object Protected {
    val EXACT: Set<String> = setOf(
        "android",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gsf.login",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.providers.settings",
        "com.android.providers.tv",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.android.externalstorage",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.bluetooth",
        "com.android.bluetoothmidiservice",
        "com.android.inputdevices",
        "com.android.webview",
        "com.google.android.webview",
        "com.android.location.fused",
        "com.google.android.ext.services",
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.wifi.resources",
        "com.android.provision",
        "com.android.keychain",
        "com.android.certinstaller",
        // Core modules: an original Mi TV Stick never booted again after these were disabled
        // (upstream cleanyourtv #884).
        "com.android.ext.services",
        "com.android.ext.shared",
        "com.google.android.ext.shared",
        "com.android.modulemetadata",
        "com.google.android.modulemetadata",
        "com.android.phone",
    ) + HomeApp.entries.map { it.pkg } // TidyTV's own home screens, once installed.

    /**
     * The voice assistant and the remote's shortcut and mic helpers. Not locked (some owners want them gone), but a
     * list never ticks them for you, and Check my TV warns when they're off: users lost the Netflix/YouTube keys and
     * the mic button after a clean-up (feedback 291492, 6d3eba).
     */
    val REMOTE_ESSENTIALS: Set<String> = setOf(
        "com.google.android.katniss",
        "com.google.android.tv.assistant",
        "com.google.android.tv.remote.service",
        "com.tcl.keyhelp",
        "com.tcl.micmanager",
    )

    /** Substrings that mark TV-input, tuner, keyboard and remote-pairing packages. */
    private val PATTERNS = listOf(
        "tvinput", "tv.input", "tvcenter", "hdmi", "tuner", "tvsystem", "tv.framework", "tvserver",
        "inputmethod", ".ime", "leanback.ime", "keyboard",
        "bluetooth", "bt_rcu", "btrcu", "rcuservice", "autopair", "remotepair",
        "networkstack", "permissioncontroller", "packageinstaller",
        // USB drives and file browsing: a TV whose owner could no longer open a USB stick (feedback ba2806).
        "documentsui", "android.mtp", "mtpservice", "usbstorage", "externalstorage",
    )

    /** Android's own content providers, except the few a TV can live without (upstream PROMPT.md). */
    private val SPARE_PROVIDERS = setOf("calendar", "contacts", "userdictionary", "blockednumber")

    private fun isCore(pkg: String) =
        pkg.startsWith("com.google.android.overlay.modules") ||
            (pkg.startsWith("com.android.providers.") && pkg.removePrefix("com.android.providers.").substringBefore('.') !in SPARE_PROVIDERS)

    fun reason(pkg: String, live: Set<String> = emptySet(), listKeep: Set<String> = emptySet()): String? {
        val lower = pkg.lowercase()
        return when {
            pkg in EXACT || isCore(pkg) -> "needed by Android"
            pkg in live -> "in use by your TV right now"
            pkg in listKeep -> "the list for this TV says to keep it"
            PATTERNS.any { lower.contains(it) } -> "needed for TV inputs, remote or keyboard"
            else -> null
        }
    }

    fun isProtected(pkg: String, live: Set<String> = emptySet(), listKeep: Set<String> = emptySet()) =
        reason(pkg, live, listKeep) != null
}

/** A home screen TidyTV can install. Both are MIT and bundled in the app's assets. */
enum class HomeApp(
    val pkg: String,
    val activity: String,
    val minSdk: Int,
    /** Bundled file under assets/launcher/, or null for one installed from the TV's Play Store. */
    val asset: String?,
    /** How the chosen dock apps reach it: start-intent extras, or a config.json it reads at start. */
    val dockByConfig: Boolean,
) {
    /** tv-detox (Ansub): one calm screen, 37 KB, apps editable on the TV with the remote. The default. */
    SIMPLE("com.ansub.tvhome", ".MainActivity", 21, "tvhome.apk", dockByConfig = false),
    /** cleanyourtv (Gathin): Apple TV style dock with a glass highlight and an inputs menu. Android 8+. */
    STYLED("com.gathin.tvhome", ".MainActivity", 26, "cleanyourtv-home.apk", dockByConfig = true),
    /**
     * TV Tidy Home: a fork of the cleanyourtv launcher (MIT, v3.0.0) built with its own application id
     * so it installs beside the author-signed STYLED launcher and never replaces it. The activity is
     * named in full because the code namespace (com.gathin.tvhome) differs from the application id.
     */
    PREMIUM("app.tvtidy.home", "com.gathin.tvhome.MainActivity", 26, "tvtidy-home.apk", dockByConfig = true),
    /**
     * Projectivy Launcher (free, closed source): never bundled. TidyTV opens its Play Store page on
     * the TV, the owner presses Install, then TidyTV makes it Home. Its activity is looked up on the
     * TV, and Restore never uninstalls it because TidyTV didn't install it.
     */
    PROJECTIVY("com.spocky.projengmenu", "", 22, null, dockByConfig = false);

    val component: String get() = "$pkg/$activity"

    /** Installed from the TV's own Play Store rather than pushed by TidyTV. */
    val fromPlayStore: Boolean get() = asset == null

    companion object {
        fun of(pkg: String?): HomeApp? = entries.firstOrNull { it.pkg == pkg }
    }
}

object Launchers {
    /** The default home screen, kept as constants for older call sites and tests. */
    val TIDY_HOME_PACKAGE = HomeApp.SIMPLE.pkg
    val TIDY_HOME_COMPONENT = HomeApp.SIMPLE.component
    const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
    const val LAUNCHERX = "com.google.android.apps.tv.launcherx"

    /** Stock launchers TidyTV may turn off, and only after its own home screen owns Home. */
    val KNOWN_STOCK: Set<String> = setOf(
        "com.google.android.tvlauncher",
        LAUNCHERX,
        "com.google.android.leanbacklauncher",
        "com.mitv.tvhome",
    )

    /** Never part of the clean-up: Home owners and the Google TV setup app that can take Home over. */
    fun isHomeApp(pkg: String) = pkg in KNOWN_STOCK || pkg == SETUPWRAITH || HomeApp.of(pkg) != null ||
        pkg == "com.amazon.tv.launcher" || pkg.startsWith(CVTE_LAUNCHER)

    /**
     * CVTE boards' own launcher stays on: a JVC set hung on its boot animation with ADB gone after it was
     * disabled (upstream cleanyourtv #741).
     */
    const val CVTE_LAUNCHER = "com.cvte.tv.launcher"
}
