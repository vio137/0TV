package dev.tidytv.core.lists

/**
 * Human names and brand icons for packages. Icons are Simple Icons slugs (CC0 artwork); the app
 * draws them from a bundled JSON and falls back to initials when a package has no slug.
 */
object AppLabels {
    private data class Known(val label: String, val icon: String?)

    private val EXACT: Map<String, Known> = mapOf(
        "com.android.vending" to Known("Google Play Store", null),
        "com.google.android.gms" to Known("Google Play services", null),
        "com.google.android.gsf" to Known("Google Services Framework", null),
        "com.google.android.webview" to Known("Android System WebView", null),
        "com.google.android.tts" to Known("Google Text-to-speech", null),
        "com.google.android.inputmethod.latin" to Known("Gboard keyboard", null),
        "com.google.android.tv.assistant" to Known("Google Assistant (TV)", null),
        "com.google.android.tv.remote.service" to Known("Remote control service", null),
        "com.google.android.partnersetup" to Known("Google partner setup", null),
        "com.google.android.onetimeinitializer" to Known("One-time setup", null),
        "com.google.android.syncadapters.calendar" to Known("Calendar sync", null),
        "com.google.android.apps.nbu.smartconnect.tv" to Known("Google TV phone connect", null),
        "com.google.android.packageinstaller" to Known("Package installer", null),
        "com.google.android.permissioncontroller" to Known("Permission controller", null),
        "com.google.android.ext.services" to Known("Android services", null),
        "com.google.android.sss.authbridge" to Known("Sign-in bridge", null),
        "com.google.android.tv.frameworkpackagestubs" to Known("Framework stubs", null),
        "android.autoinstalls.config.google.gtvpai" to Known("Partner app auto-installs", null),
        "com.android.tv.settings" to Known("TV Settings", null),
        "com.android.systemui" to Known("System UI", null),
        "com.android.bluetooth" to Known("Bluetooth", null),
        "com.android.printspooler" to Known("Print services", null),
        "com.android.providers.tv" to Known("TV channels storage", null),
        "com.android.providers.media.module" to Known("Media storage", null),
        "com.android.providers.calendar" to Known("Calendar storage", null),
        "com.android.providers.contacts" to Known("Contacts storage", null),
        "com.android.dreams.basic" to Known("Basic screensaver", null),
        "com.android.htmlviewer" to Known("HTML viewer", null),
        "com.android.captiveportallogin" to Known("Wi-Fi sign-in pages", null),
        "com.android.backupconfirm" to Known("Backup confirmation", null),
        "com.android.sharedstoragebackup" to Known("Shared storage backup", null),
        "com.android.wallpaperbackup" to Known("Wallpaper backup", null),
        "com.android.localtransport" to Known("Local backup transport", null),
        "com.android.cts.ctsshim" to Known("Test shim (CTS)", null),
        "com.android.cts.priv.ctsshim" to Known("Test shim (CTS)", null),
        "com.android.hotspot2.osulogin" to Known("Hotspot 2.0 sign-in", null),
        "com.android.pacprocessor" to Known("Proxy auto-config", null),
        "com.android.proxyhandler" to Known("Proxy handler", null),
        "com.android.statementservice" to Known("App link verifier", null),
        "com.android.vpndialogs" to Known("VPN dialogs", null),
        "com.android.companiondevicemanager" to Known("Companion device manager", null),
        "com.android.soundpicker" to Known("Sound picker", null),
        "com.android.se" to Known("Secure element", null),
        "com.android.settings.intelligence" to Known("Settings search", null),
        "com.dolby.android.audio.service" to Known("Dolby audio", null),
        "com.tcl.guard" to Known("TGuard (device-use tracking)", null),
        "com.tcl.ttvs" to Known("TCL analytics and updater", null),
        "com.tcl.usercenter" to Known("TCL account", null),
        "com.tcl.esticker" to Known("TCL retail sticker", null),
        "com.tcl.messagebox" to Known("TCL messages and promos", null),
        "com.tcl.partnercustomizer" to Known("TCL home rows", null),
        "com.tcl.useragreement" to Known("TCL user agreement", null),
        "com.tcl.waterfall.overseas" to Known("TCL content waterfall", null),
        "com.tcl.hotelmenu" to Known("TCL hotel mode", null),
        "com.tcl.dashboard" to Known("TCL home panel", null),
        "com.tcl.android.webview" to Known("TCL web view", null),
        "com.tcl.t_solo" to Known("T-Solo (Bluetooth listening)", null),
        "com.tcl.ui_mediaCenter" to Known("TCL media player", null),
        "com.tcl.smartalexa" to Known("TCL Alexa", null),
        "com.tcl.copydatatotv" to Known("TCL copy-data-to-TV", null),
        "com.tcl.common.shortcutmenu" to Known("TCL shortcut menu", null),
        "com.tcl.tv" to Known("TCL TV service", null),
        "com.tcl.settings" to Known("TCL settings", null),
        "com.tcl.system.server" to Known("TCL system server", null),
        "com.tcl.tvinput" to Known("TCL TV input", null),
        "com.tcl.inputmethod.international" to Known("TCL keyboard", null),
        "com.tcl.autopair" to Known("TCL remote pairing", null),
        "com.tcl.factory.view" to Known("TCL factory menu", null),
        "com.tcl.initsetup" to Known("TCL first setup", null),
        "com.tcl.keyhelp" to Known("TCL remote key helper", null),
        "com.tcl.micmanager" to Known("TCL mic manager", null),
        "com.tcl.providers.config" to Known("TCL config storage", null),
        "com.tcl.suspension" to Known("TCL floating button", null),
        "com.tcl.tcl_bt_rcu_service" to Known("TCL Bluetooth remote", null),
        "com.tcl.channelplus" to Known("TCL Live channels", null),
        "com.netflix.ninja" to Known("Netflix", "netflix"),
        "com.google.android.youtube.tv" to Known("YouTube", "youtube"),
        "com.google.android.youtube.tvmusic" to Known("YouTube Music", "youtubemusic"),
        "com.google.android.youtube.tvkids" to Known("YouTube Kids", "youtubekids"),
        "com.amazon.amazonvideo.livingroom" to Known("Prime Video", "primevideo"),
        "com.disney.disneyplus" to Known("Disney+", null),
        "com.hulu.livingroomplus" to Known("Hulu", "hulu"),
        "com.wbd.stream" to Known("Max", "hbomax"),
        "in.startv.hotstar" to Known("JioHotstar", "hotstar"),
        "com.spotify.tv.android" to Known("Spotify", "spotify"),
        "tv.twitch.android.app" to Known("Twitch", "twitch"),
        "tv.twitch.android.viewer" to Known("Twitch", "twitch"),
        "com.plexapp.android" to Known("Plex", "plex"),
        "com.peacocktv.peacockandroid" to Known("Peacock", null),
        "com.cbs.ott" to Known("Paramount+", "paramountplus"),
        "com.tubitv" to Known("Tubi", null),
        "tv.pluto.android" to Known("Pluto TV", null),
        "com.crunchyroll.crunchyroid" to Known("Crunchyroll", "crunchyroll"),
        "com.dazn" to Known("DAZN", "dazn"),
        "com.apple.atve.androidtv.appletv" to Known("Apple TV", "appletv"),
        "com.google.android.videos" to Known("Google TV store", "googletv"),
        "com.google.android.play.games" to Known("Play Games", "googleplay"),
        "com.google.android.katniss" to Known("Google Assistant", "googleassistant"),
        "com.google.android.apps.mediashell" to Known("Chromecast built-in", "googlecast"),
        "com.google.android.feedback" to Known("Google Feedback", "google"),
        "com.google.android.marvin.talkback" to Known("TalkBack", "google"),
        "com.google.android.apps.tv.dreamx" to Known("Ambient mode", "google"),
        "com.google.android.tvrecommendations" to Known("Google recommendations", "google"),
        "com.google.android.backdrop" to Known("Backdrop screensaver", "google"),
        "com.amazon.alexa.tv" to Known("Alexa", "amazonalexa"),
        "com.facebook.katana" to Known("Facebook", "facebook"),
        "com.facebook.appmanager" to Known("Facebook App Manager", "facebook"),
        "com.facebook.system" to Known("Facebook services", "facebook"),
        "com.samba.tv" to Known("Samba TV", null),
        "com.google.android.apps.tv.launcherx" to Known("Google TV home screen", "googletv"),
        "com.google.android.tvlauncher" to Known("Android TV home screen", "android"),
        "com.google.android.leanbacklauncher" to Known("Android TV home screen", "android"),
        "com.google.android.tungsten.setupwraith" to Known("Google TV setup", "google"),
    )

    /** Brand by package prefix, for the many vendor services with no friendly name. */
    private val PREFIX_ICON: List<Pair<String, String>> = listOf(
        "com.google." to "google",
        "com.android." to "android",
        "com.tcl." to "tcl",
        "com.sony." to "sony",
        "com.xiaomi." to "xiaomi",
        "com.mitv." to "xiaomi",
        "com.miui." to "xiaomi",
        "com.amazon." to "amazon",
        "com.nvidia." to "nvidia",
        "com.philips." to "philips",
        "org.droidtv." to "philips",
        "com.hisense." to "hisense",
        "com.mediatek." to "mediatek",
        "com.realtek." to "realtek",
        "com.oneplus." to "oneplus",
        "com.netflix." to "netflix",
        "com.spotify." to "spotify",
        "com.samsung." to "samsung",
        "com.huawei." to "huawei",
    )

    /** Every Simple Icons slug the app may ask for; tools/gen-icons.py bundles exactly these. */
    val ALL_SLUGS: Set<String> =
        (EXACT.values.mapNotNull { it.icon } + PREFIX_ICON.map { it.second }).toSet()

    /** True when TidyTV has a hand-picked name for [pkg]. */
    fun known(pkg: String) = pkg in EXACT

    fun iconSlug(pkg: String): String? =
        EXACT[pkg]?.icon ?: PREFIX_ICON.firstOrNull { pkg.startsWith(it.first) }?.second

    /** A short readable name: known name, else the list's note, else the package's last words. */
    fun label(pkg: String, note: String = ""): String {
        EXACT[pkg]?.let { return it.label }
        val fromNote = note.split(Regex("[,;(]| - | -- ")).first().trim()
        if (fromNote.length in 3..40 && !fromNote.contains("re-enable", ignoreCase = true)) {
            return fromNote.replaceFirstChar { it.uppercase() }
        }
        val parts = pkg.split('.').filterNot { it in GENERIC }
        val tail = parts.takeLast(2).joinToString(" ") { it.replace('_', ' ') }
        return tail.replaceFirstChar { it.uppercase() }.ifBlank { pkg }
    }

    /** Two letters for the fallback chip. */
    fun initials(label: String): String =
        label.split(Regex("[^\\p{L}\\p{N}+]+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    private val GENERIC = setOf("com", "org", "net", "tv", "android", "app", "google", "apps", "service", "services")
}
