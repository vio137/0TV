package dev.tidytv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.tidytv.app.ui.Screen
import dev.tidytv.app.ui.TidyTheme
import dev.tidytv.core.flow.ConnectState
import dev.tidytv.core.flow.FoundTv
import dev.tidytv.core.flow.Mode
import dev.tidytv.core.flow.Step
import dev.tidytv.core.flow.TvKind
import dev.tidytv.core.flow.Wizard
import dev.tidytv.core.lists.Candidate
import dev.tidytv.core.lists.Group
import dev.tidytv.core.ops.ActionRecord
import dev.tidytv.core.ops.Op
import dev.tidytv.core.ops.Phase
import dev.tidytv.core.ops.PkgResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen state, at 360 dp and 412 dp wide and at 360 dp with large fonts.
 * `./gradlew :app:recordRoborazziDebug` writes the images; `verifyRoborazziDebug` compares against them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    // Activity times render in the default zone; pin it so screenshots match on any machine.
    @org.junit.Before fun utc() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")) }

    private data class Size(val name: String, val widthDp: Int, val font: Float)
    private val sizes = listOf(Size("360", 360, 1f), Size("412", 412, 1f), Size("360-large", 360, 1.6f))

    private fun shoot(name: String, step: Step, s: UiState, tall: Int = 2) {
        for (size in sizes) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.widthDp * tall}dp-xxhdpi")
            RuntimeEnvironment.setFontScale(size.font)
            captureRoboImage("src/test/screenshots/${name}_${size.name}.png") {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    TidyTheme { Screen(step, s, vm = null, onShareLogs = {}) }
                }
            }
        }
    }

    private val tv = FoundTv("192.168.1.42", name = "Living room TV", model = "TCL 55C735", legacyOpen = true)
    private val summary = TvSummary("Living room TV", "TCL 55C735", "192.168.1.42", "SERIAL", 30, "11")
    private val candidates = listOf(
        Candidate("com.tcl.tv.tclchannel", "TCL Channel", "", Group.ADS_TRACKING, true, false),
        Candidate("com.google.android.feedback", "Google Feedback", "", Group.ADS_TRACKING, true, false),
        Candidate("com.tcl.guard", "TGuard", "", Group.ADS_TRACKING, true, false),
        Candidate("com.facebook.appmanager", "Facebook App Manager", "", Group.ADS_TRACKING, true, false),
        Candidate("com.google.android.play.games", "Play Games", "", Group.PRELOADED, false, false),
        Candidate("com.google.android.katniss", "Google Assistant", "", Group.VOICE, false, false),
        Candidate("com.google.android.apps.mediashell", "Chromecast built-in", "", Group.CASTING, false, false),
        Candidate("com.spotify.tv.android", "Spotify", "", Group.STREAMING, false, false),
        Candidate("com.disney.disneyplus", "Disney+", "", Group.STREAMING, false, false),
    )
    private val review = ReviewState(BackupStatus.Saved, "TCL Smart TV Pro", true, candidates,
        candidates.filter { it.selected }.map { it.pkg }.toSet(), listOf("TV inputs", "Live TV"))
    private val clean = Wizard(Step.REVIEW, TvKind.ANDROID_TV)


    private fun row(pkg: String, label: String, tier: dev.tidytv.core.lists.Tier, note: String, sel: Boolean = false, g: Group = Group.PRELOADED) =
        dev.tidytv.core.lists.AppRow(pkg, label, tier, note, sel, g)
    private val allRows = listOf(
        row("com.tcl.guard", "TGuard", dev.tidytv.core.lists.Tier.RECOMMENDED, "Tracks what you watch.", true, Group.ADS_TRACKING),
        row("com.tcl.messagebox", "Pushed messages and notifications", dev.tidytv.core.lists.Tier.RECOMMENDED, "Ads and offers on your home screen.", true, Group.ADS_TRACKING),
        row("com.google.android.katniss", "Google Assistant", dev.tidytv.core.lists.Tier.YOUR_CALL, "This TV's list keeps it on. The remote's mic button stops working.", false, Group.VOICE),
        row("com.google.android.apps.mediashell", "Chromecast built-in", dev.tidytv.core.lists.Tier.YOUR_CALL, "Casting to this TV stops working.", false, Group.CASTING),
        row("com.spotify.tv.android", "Spotify", dev.tidytv.core.lists.Tier.OTHER_TVS, "Streaming app from other TVs' lists.", false, Group.STREAMING),
        row("com.stremio.one", "Stremio", dev.tidytv.core.lists.Tier.YOURS, "Uninstalling is permanent. Get it again from Google Play.", false, Group.STREAMING),
        row("in.startv.hotstar", "JioHotstar", dev.tidytv.core.lists.Tier.YOURS, "Uninstalling is permanent. Get it again from Google Play.", false, Group.STREAMING),
        row("com.example.vendor.unknown", "Vendor Service With A Rather Long Name", dev.tidytv.core.lists.Tier.UNLISTED, "Not on any list. Check before turning off.", false, Group.PRELOADED),
        row("com.google.android.gms", "Google Play Services", dev.tidytv.core.lists.Tier.LOCKED, "The TV needs it to work.", false, Group.PRELOADED),
    )
    private val allFacts = mapOf(
        "com.tcl.guard" to dev.tidytv.core.ops.AppFacts(ramMb = 151, startsAtBoot = true, appMb = 30, dataMb = 12, cacheMb = 40),
        "com.tcl.messagebox" to dev.tidytv.core.ops.AppFacts(ramMb = 64, startsAtBoot = true),
        "com.google.android.katniss" to dev.tidytv.core.ops.AppFacts(ramMb = 210, appMb = 180, dataMb = 60, cacheMb = 90),
        "com.stremio.one" to dev.tidytv.core.ops.AppFacts(ramMb = 8, appMb = 120, dataMb = 30, cacheMb = 300, yours = true),
        "com.google.android.gms" to dev.tidytv.core.ops.AppFacts(ramMb = 400, startsAtBoot = true, appMb = 200),
        "in.startv.hotstar" to dev.tidytv.core.ops.AppFacts(ramMb = 95, appMb = 210, dataMb = 80, cacheMb = 120, yours = true),
    )
    private val allApps = AllAppsState(loading = false, rows = allRows, facts = allFacts)
    private val allReview = review.copy(all = allApps, selected = setOf("com.tcl.guard", "com.tcl.messagebox", "com.google.android.katniss"))
    private val allLabels = allRows.associate { it.pkg to it.label }
    @Test fun allApps() = shoot("12_all_apps", Step.REVIEW, UiState(wizard = clean, labels = allLabels, review = allReview))
    @Test fun allAppsLoading() = shoot("12c_all_apps_loading", Step.REVIEW, UiState(wizard = clean, review = review.copy(all = AllAppsState())))
    @Test fun allAppsUninstall() = shoot("12b_all_apps_uninstall", Step.REVIEW, UiState(wizard = clean, labels = allLabels,
        review = allReview.copy(uninstall = true, selected = setOf("com.tcl.guard", "com.stremio.one"), confirmPermanent = listOf("com.stremio.one"))))
    private val spaceStorage = dev.tidytv.core.ops.StorageFacts(5800, 10000, 1500)
    private val topCache = listOf("com.stremio.one" to 612, "com.google.android.katniss" to 380, "com.tcl.guard" to 240, "com.google.android.gms" to 120)
    @Test fun space() = shoot("13_space", Step.SPACE, UiState(tv = summary, labels = allLabels, space = SpaceState(false, spaceStorage, topCache)))
    @Test fun spaceDone() = shoot("13b_space_done", Step.SPACE, UiState(tv = summary, labels = allLabels,
        space = SpaceState(false, dev.tidytv.core.ops.StorageFacts(7200, 10000, 90), emptyList(), message = RestoreUi.Finished("Freed 1,412 MB on your TV.", true))))
    @Test fun apps() = shoot("15_apps", Step.APPS, UiState(tv = summary, labels = allLabels, apps = AppsState(false, allRows.take(8), emptyMap())))
    @Test fun appsLoading() = shoot("15b_apps_loading", Step.APPS, UiState(tv = summary, apps = AppsState()))
    @Test fun adsOff() = shoot("14_ads_off", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, dns = dev.tidytv.core.ops.DnsState(null, null))))
    @Test fun adsOn() = shoot("14b_ads_on", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false,
        dns = dev.tidytv.core.ops.DnsState("hostname", "dns.adguard-dns.com"), message = RestoreUi.Finished("AdGuard DNS is set on the TV (dns.adguard-dns.com) and names resolve through it.", ok = true))))
    @Test fun adsReplaceAsk() = shoot("14c_ads_replace", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false,
        dns = dev.tidytv.core.ops.DnsState("hostname", "dns.quad9.net"), confirmReplace = dev.tidytv.core.ops.AdBlockProvider.ADS)))
    @Test fun adsAutomatic() = shoot("14e_ads_automatic", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, dns = dev.tidytv.core.ops.DnsState("opportunistic", "dns.adguard-dns.com"))))
    @Test fun adsCustom() = shoot("14f_ads_custom", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, lastCustom = "dns.example.com", dns = dev.tidytv.core.ops.DnsState("hostname", "dns.example.com"))))
    @Test fun adsLookupFailed() = shoot("14g_ads_lookup_failed", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, dns = dev.tidytv.core.ops.DnsState("opportunistic", null),
        message = RestoreUi.Finished("The TV took the setting but couldn't look up names through dead.example.com, so it was put back. Check the server name, and that your network allows DNS-over-TLS (port 853).", ok = false))))
    @Test fun adsCustomTall() = shoot("14h_ads_custom_tall", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, lastCustom = "dns.example.com", dns = dev.tidytv.core.ops.DnsState("hostname", "dns.example.com"))), tall = 3)
    @Test fun adsAutomaticTall() = shoot("14i_ads_automatic_tall", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, dns = dev.tidytv.core.ops.DnsState("opportunistic", null))), tall = 3)
    @Test fun adsOldTv() = shoot("14d_ads_old_tv", Step.ADS, UiState(tv = summary, ads = AdsState(loading = false, supported = false)))
    @Test fun spaceLoading() = shoot("13c_space_loading", Step.SPACE, UiState(tv = summary, space = SpaceState()))

    @Test fun welcome() = shoot("1_welcome", Step.WELCOME, UiState())
    @Test fun unsupported() = shoot("1b_unsupported", Step.UNSUPPORTED, UiState())
    @Test fun getReady() = shoot("2_get_ready", Step.GET_READY, UiState(wizard = Wizard(Step.GET_READY, TvKind.ANDROID_TV)))
    @Test fun getReadyFire() = shoot("2b_get_ready_fire", Step.GET_READY, UiState(wizard = Wizard(Step.GET_READY, TvKind.FIRE_TV)))

    @Test fun connectScanning() = shoot("3_scan", Step.CONNECT, UiState(connect = ConnectState.Scanning(listOf(tv, FoundTv("192.168.1.50", legacyOpen = true)), done = true)))
    @Test fun connectPressAllow() = shoot("3b_press_allow", Step.CONNECT, UiState(connect = ConnectState.PressAllow(tv, canRetry = true)))
    @Test fun connectPairing() = shoot("3c_pairing", Step.CONNECT, UiState(connect = ConnectState.Pairing(tv.copy(tlsPort = 37000), error = "Pairing didn't work. Check the code and try again.")))
    @Test fun connectNothingFound() = shoot("3d_nothing_found", Step.CONNECT, UiState(connect = ConnectState.NothingFound(badIp = true)))
    @Test fun connectTurnOnDebugging() = shoot("3e_turn_on_debugging", Step.CONNECT, UiState(connect = ConnectState.TurnOnDebugging(tv)))

    @Test fun reviewBackingUp() = shoot("4a_backup_running", Step.REVIEW, UiState(wizard = clean, review = ReviewState()))
    @Test fun reviewReady() = shoot("4_review", Step.REVIEW, UiState(wizard = clean, review = review))
    @Test fun reviewSimilarModel() = shoot("4b_review_similar", Step.REVIEW, UiState(wizard = clean, review = review.copy(exact = false)))
    private val more = listOf(
        Candidate("com.google.android.youtube.tvmusic", "YouTube Music", "", Group.STREAMING, false, true),
        Candidate("com.google.android.apps.mediashell", "Chromecast built-in", "", Group.CASTING, false, true),
        Candidate("com.tcl.messagebox", "Pushed messages and notifications", "", Group.ADS_TRACKING, false, true),
    )
    private val short = review.copy(candidates = candidates.take(1), selected = setOf(candidates[0].pkg), moreAvailable = more)
    @Test fun reviewFewFound() = shoot("4d_review_few_found", Step.REVIEW, UiState(wizard = clean, review = short))
    @Test fun reviewShowingMore() = shoot("4e_review_more_apps", Step.REVIEW, UiState(wizard = clean, review = short.copy(extra = more)))
    @Test fun homeOfferUntested() = shoot("5c5_home_untested", Step.HOME_SCREEN, UiState(tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        home = HomeState.Offer(untested = true)))
    @Test fun reviewNoList() = shoot("4c_review_no_list", Step.REVIEW, UiState(wizard = clean, review = ReviewState(BackupStatus.Saved, noList = true)))

    private val order = candidates.take(4)
    @Test fun tidying() = shoot("5_tidying", Step.TIDYING, UiState(tidy = TidyState(order, mapOf(
        order[0].pkg to PkgResult.Disabled(order[0].pkg), order[1].pkg to PkgResult.AlreadyOff(order[1].pkg)), reconnecting = true)))
    @Test fun tidyingStopped() = shoot("5b_tidying_stopped", Step.TIDYING, UiState(tidy = TidyState(order, mapOf(
        order[0].pkg to PkgResult.Disabled(order[0].pkg), order[1].pkg to PkgResult.Failed(order[1].pkg, "still on")),
        stopReason = "Google Feedback didn't stay off. Nothing else was changed.")))

    @Test fun homeOffer() = shoot("5c_home_offer", Step.HOME_SCREEN, UiState(
        tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        home = HomeState.Offer(
        listOf(DockApp("com.netflix.ninja", "Netflix"), DockApp("com.google.android.youtube.tv", "YouTube"), DockApp("com.spotify.tv.android", "Spotify")),
        listOf("com.netflix.ninja", "com.google.android.youtube.tv"), available = dev.tidytv.core.lists.HomeApp.entries)))
    @Test fun homeOfferStyled() = shoot("5c2_home_offer_styled", Step.HOME_SCREEN, UiState(
        tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        home = HomeState.Offer(listOf(DockApp("com.netflix.ninja", "Netflix")), listOf("com.netflix.ninja"),
            app = dev.tidytv.core.lists.HomeApp.STYLED, available = dev.tidytv.core.lists.HomeApp.entries)))
    @Test fun homeOfferPremium() = shoot("5c6_home_offer_premium", Step.HOME_SCREEN, UiState(
        home = HomeState.Offer(app = dev.tidytv.core.lists.HomeApp.PREMIUM, available = dev.tidytv.core.lists.HomeApp.entries)))
    @Test fun homeOfferPremiumAllCards() = shoot("5c7_home_offer_premium_tall", Step.HOME_SCREEN, UiState(
        home = HomeState.Offer(app = dev.tidytv.core.lists.HomeApp.PREMIUM, available = dev.tidytv.core.lists.HomeApp.entries)), tall = 3)
    @Test fun homeInstalledStyled() = shoot("5e_home_installed_styled", Step.HOME_SCREEN, UiState(home = HomeState.Installed(dev.tidytv.core.lists.HomeApp.STYLED)))
    @Test fun homeSetupApp() = shoot("5d_home_setup_app", Step.HOME_SCREEN, UiState(home = HomeState.Problem(
        "Google TV's setup app grabbed Home, so TidyTV put your old home screen back. It can turn the setup app off and try again.",
        worried = false, action = HomeAction.TURN_OFF_SETUP_APP)))

    private val records by lazy {
        order.mapIndexed { i, c -> ActionRecord(1_700_000_000_000 + i * 1000L, Op.DISABLE, c.pkg, Phase.DONE) } +
            ActionRecord(1_700_000_060_000, Op.SET_HOME, "com.ansub.tvhome/.MainActivity", Phase.DONE)
    }
    @Test fun done() = shoot("6_done", Step.DONE, UiState(tv = summary, tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        done = DoneState(freedMb = 140, newHome = true, records = records, debugging = DebugCheck.OFF),
        labels = mapOf("com.tcl.tv.tclchannel" to "TCL Channel")))
    @Test fun doneHomeUntested() = shoot("6d_done_home_untested", Step.DONE, UiState(tv = summary, tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        done = DoneState(records = records.dropLast(1), homeUntested = true)))
    // ---- 0.4.0: My TV hub, remote, send files ----
    @Test fun welcomeSaved() = shoot("1d_welcome_saved_tvs", Step.WELCOME, UiState(savedTvs = listOf(
        dev.tidytv.app.data.KnownTv("A1", "Living room TV", "TCL 55C735", "192.168.1.42", 2),
        dev.tidytv.app.data.KnownTv("B2", "Bedroom", "Chromecast with Google TV", "192.168.1.57", 1))))
    @Test fun hub() = shoot("7_hub", Step.HUB, UiState(tv = summary, wizard = dev.tidytv.core.flow.Wizard(Step.HUB, TvKind.ANDROID_TV),
        done = DoneState(records = records)))
    @Test fun hubWithFacts() = shoot("7c_hub_facts", Step.HUB, UiState(tv = summary, wizard = dev.tidytv.core.flow.Wizard(Step.HUB, TvKind.ANDROID_TV),
        done = DoneState(records = records), hubFacts = HubFacts(2_150, 1_180)))
    @Test fun reviewNoListWithOtherTvsApps() = shoot("4f_review_no_list", Step.REVIEW, UiState(tv = summary, wizard = clean, labels = allLabels, review = ReviewState(
        BackupStatus.Saved, noList = true, extra = candidates.take(6), tvDetails = "My TV isn't on a list yet.")))
    @Test fun hubFireTv() = shoot("7b_hub_fire_tv", Step.HUB, UiState(tv = summary, wizard = dev.tidytv.core.flow.Wizard(Step.HUB, TvKind.FIRE_TV)))
    @Test fun remote() = shoot("8_remote", Step.REMOTE, UiState(tv = summary, remote = RemoteState(apps = listOf(
        RemoteApp("com.netflix.ninja", "com.netflix.ninja/.MainActivity", "Netflix"),
        RemoteApp("com.google.android.youtube.tv", "com.google.android.youtube.tv/.Main", "YouTube"),
        RemoteApp("com.google.android.youtube.tvkids", "com.google.android.youtube.tvkids/.Main", "YouTube Kids"),
        RemoteApp("com.amazon.amazonvideo.livingroom", "com.amazon.amazonvideo.livingroom/.Main", "Prime Video"),
        RemoteApp("com.spotify.tv.android", "com.spotify.tv.android/.Main", "Spotify"),
        RemoteApp("com.plexapp.android", "com.plexapp.android/.Main", "Plex"),
        RemoteApp("org.xbmc.kodi", "org.xbmc.kodi/.Main", "Kodi")))))
    @Test fun remoteReconnecting() = shoot("8b_remote_reconnecting", Step.REMOTE, UiState(tv = summary, remote = RemoteState(reconnecting = true)))
    @Test fun filesEmpty() = shoot("9_send_files", Step.SEND_FILES, UiState(tv = summary, files = FilesState(freeBytes = 8L * 1024 * 1024 * 1024)))
    @Test fun filesSending() = shoot("9b_send_files_progress", Step.SEND_FILES, UiState(tv = summary, files = FilesState(freeBytes = 8L * 1024 * 1024 * 1024, items = listOf(
        SendItem(1, "Holiday 2026.mp4", 734_003_200, sent = 734_003_200, status = SendStatus.SENT, savedAs = "Holiday 2026.mp4"),
        SendItem(2, "Birthday photos.zip", 52_428_800, sent = 20_971_520, status = SendStatus.SENDING),
        SendItem(3, "Recipe.pdf", 412_000, status = SendStatus.WAITING),
        SendItem(4, "Old film.mkv", 9_000_000_000, status = SendStatus.NO_SPACE)))))
    @Test fun checkRemoteOff() = shoot("10c_check_remote_off", Step.CHECK, UiState(tv = summary, labels = allLabels,
        check = CheckState(report = dev.tidytv.core.ops.CheckReport(emptyList(), emptyList(), homeChanged = false, stillOff = 24,
            remoteOff = listOf("com.google.android.katniss", "com.tcl.keyhelp")))))
    @Test fun checkHomeWentBack() = shoot("10d_check_home_back", Step.CHECK, UiState(tv = summary, labels = allLabels,
        check = CheckState(report = dev.tidytv.core.ops.CheckReport(emptyList(), emptyList(), homeChanged = true, stillOff = 24,
            repairableHome = dev.tidytv.core.lists.HomeApp.SIMPLE))))
    @Test fun checkAllGood() = shoot("10_check_all_good", Step.CHECK, UiState(tv = summary,
        check = CheckState(report = dev.tidytv.core.ops.CheckReport(emptyList(), emptyList(), homeChanged = false, stillOff = 24))))
    @Test fun checkFound() = shoot("10b_check_found", Step.CHECK, UiState(tv = summary,
        check = CheckState(report = dev.tidytv.core.ops.CheckReport(listOf("com.google.android.youtube.tvmusic", "com.google.android.tvrecommendations"),
            listOf("com.amazon.amazonvideo.livingroom"), homeChanged = true, stillOff = 21))))
    @Test fun checkRebooting() = shoot("10c_check_rebooting", Step.CHECK, UiState(tv = summary,
        check = CheckState(rebooting = "Waiting for your TV to start… 2 min left")))
    private val savedTv = dev.tidytv.app.data.KnownTv("abc", "Living room TV", "Chromecast with Google TV", "192.168.1.40", 0)
    @Test fun rescueTrying() = shoot("11_rescue", Step.RESCUE, UiState(rescue = RescueState(savedTv, attempts = 7)))
    @Test fun rescueDone() = shoot("11b_rescue_done", Step.RESCUE, UiState(rescue = RescueState(savedTv, attempts = 12,
        result = RestoreUi.Finished("Your TV answered and everything TidyTV changed is back on. Restart the TV if it still looks stuck.", ok = true))))
    @Test fun rescueGaveUp() = shoot("11c_rescue_gave_up", Step.RESCUE, UiState(rescue = RescueState(savedTv, attempts = 120, gaveUp = true)))
    @Test fun homeOfferProjectivy() = shoot("5c3_home_offer_projectivy", Step.HOME_SCREEN, UiState(home = HomeState.Offer(
        app = dev.tidytv.core.lists.HomeApp.PROJECTIVY, available = dev.tidytv.core.lists.HomeApp.entries)))
    @Test fun homeWaitingForPlayStore() = shoot("5f_home_waiting_play_store", Step.HOME_SCREEN, UiState(
        home = HomeState.WaitingForPlayStore(dev.tidytv.core.lists.HomeApp.PROJECTIVY)))
    @Test fun lookPicker() = shoot("5g_home_look", Step.HOME_SCREEN, UiState(home = HomeState.Look(LookState(current = "glass",
        message = RestoreUi.Finished("Glass is on your TV.", ok = true)))))
    @Test fun lookSendingPhoto() = shoot("5g2_home_look_photo", Step.HOME_SCREEN, UiState(home = HomeState.Look(LookState(applying = "photo", current = "aurora"))))
    @Test fun offerWhenStyledIsHome() = shoot("5c4_home_offer_change_look", Step.HOME_SCREEN, UiState(home = HomeState.Offer(
        app = dev.tidytv.core.lists.HomeApp.STYLED, available = dev.tidytv.core.lists.HomeApp.entries, styledIsHome = true)))
    @Test fun doneOneBackOn() = shoot("6c_done_one_back_on", Step.DONE, UiState(tv = summary, tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
        done = DoneState(freedMb = 140, newHome = true, debugging = DebugCheck.OFF,
            records = records + ActionRecord(1_700_000_090_000, Op.ENABLE, order.first().pkg, Phase.DONE),
            turningOn = setOf(order[1].pkg),
            turnOnNote = RestoreUi.Finished("${order.first().label} is back on.", ok = true))))
    @Test fun doneRestored() = shoot("6b_restored", Step.DONE, UiState(tv = summary, wizard = Wizard(Step.DONE, mode = Mode.RESTORE),
        done = DoneState(records = records, restore = RestoreUi.Finished("Everything is back the way it was: 4 apps on, old home screen back.", ok = true))))

    @Test fun shareCard() {
        val ctx = RuntimeEnvironment.getApplication()
        val stats = dev.tidytv.app.ui.shareStats(UiState(
            tv = summary, tidy = TidyState(order, order.associate { it.pkg to PkgResult.Disabled(it.pkg) }),
            done = DoneState(freedMb = 140, newHome = true),
        ))
        val bmp = dev.tidytv.app.data.ShareCard(ctx, emptyMap()).render(stats)
        java.io.File("src/test/screenshots/7_share_card.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        org.junit.Assert.assertEquals(1080 to 1350, bmp.width to bmp.height)
    }

    @Test fun aboutSheetContent() {
        for (size in sizes) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.widthDp * 2}dp-xxhdpi")
            RuntimeEnvironment.setFontScale(size.font)
            captureRoboImage("src/test/screenshots/about_${size.name}.png") {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    TidyTheme {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.fillMaxSize().background(dev.tidytv.app.ui.Tidy.Card).padding(22.dp),
                        ) { dev.tidytv.app.ui.AboutContent {} }
                    }
                }
            }
        }
    }
}
