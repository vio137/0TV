package dev.tidytv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidytv.app.data.KnownTv
import dev.tidytv.app.data.Store
import dev.tidytv.app.data.Trace
import dev.tidytv.app.net.Backend
import dev.tidytv.app.net.DemoBackend
import dev.tidytv.core.flow.Attempt
import dev.tidytv.core.flow.ConnectEvent
import dev.tidytv.core.flow.ConnectMachine
import dev.tidytv.core.flow.ConnectState
import dev.tidytv.core.flow.FoundTv
import dev.tidytv.core.flow.Mode
import dev.tidytv.core.flow.Step
import dev.tidytv.core.flow.TvKind
import dev.tidytv.core.flow.Wizard
import dev.tidytv.core.flow.WizardEvent
import dev.tidytv.core.flow.WizardReducer
import dev.tidytv.core.lists.AppLabels
import dev.tidytv.core.lists.Candidate
import dev.tidytv.core.lists.DebloatList
import dev.tidytv.core.lists.Group
import dev.tidytv.core.lists.Grouping
import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.lists.ListCatalog
import dev.tidytv.core.lists.ListEntry
import dev.tidytv.core.lists.ModelMatcher
import dev.tidytv.core.lists.Protected
import dev.tidytv.core.ops.ActionLog
import dev.tidytv.core.ops.ActionRecord
import dev.tidytv.core.ops.ApkFetcher
import dev.tidytv.core.ops.DebloatOutcome
import dev.tidytv.core.ops.Debloater
import dev.tidytv.core.ops.Finisher
import dev.tidytv.core.ops.HandoverOutcome
import dev.tidytv.core.ops.LauncherHandover
import dev.tidytv.core.ops.PkgResult
import dev.tidytv.core.ops.isOff
import dev.tidytv.core.ops.removedByUs
import dev.tidytv.core.ops.RestoreOutcome
import dev.tidytv.core.ops.Restorer
import dev.tidytv.core.ops.TvConnectionLost
import dev.tidytv.core.ops.TvReader
import dev.tidytv.core.ops.TvShell
import dev.tidytv.core.ops.TvSnapshot
import dev.tidytv.core.ops.WraithCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class TvSummary(val title: String, val model: String, val host: String, val serial: String, val sdk: Int, val release: String)

sealed interface BackupStatus {
    data object Running : BackupStatus
    data object Saved : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

data class ReviewState(
    val backup: BackupStatus = BackupStatus.Running,
    val listTitle: String? = null,
    /** False: a similar model's list, not tested on this exact TV. */
    val exact: Boolean = false,
    val candidates: List<Candidate> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** Installed apps the list mentions that TidyTV will always leave on. */
    val kept: List<String> = emptyList(),
    /** No list matches this TV: backup only, nothing to turn off. */
    val noList: Boolean = false,
    /** Installed apps other TVs' lists turn off, offered behind "Show more apps" when [candidates] is short. */
    val moreAvailable: List<Candidate> = emptyList(),
    /** The extra apps once "Show more apps" is tapped. All start unselected. */
    val extra: List<Candidate> = emptyList(),
    /** The All apps view, once opened: every app on the TV in tiers. Null while closed. */
    val all: AllAppsState? = null,
    /** How the picked apps go: false turns them off (default), true uninstalls them for the TV's user. */
    val uninstall: Boolean = false,
    /** Apps the owner installed that are picked: uninstalling them is permanent, so it's confirmed first. */
    val confirmPermanent: List<String> = emptyList(),
    /** This TV's own model name, to compare with [listTitle] when the list is for a similar model. */
    val yourTv: String? = null,
    /** Model, brand and Android version (never the serial or address), to send us when no list matches. */
    val tvDetails: String = "",
) {
    /** Few apps from this TV's own list: say so and offer the others instead of looking finished. */
    val fewFound get() = !noList && candidates.size < FEW && extra.isEmpty() && moreAvailable.isNotEmpty()

    companion object { const val FEW = 5 }
}

data class AllAppsState(
    val loading: Boolean = true,
    val rows: List<dev.tidytv.core.lists.AppRow> = emptyList(),
    val facts: Map<String, dev.tidytv.core.ops.AppFacts> = emptyMap(),
)

/** "Free up space": the TV's storage and the apps with the biggest caches. */
data class SpaceState(
    val loading: Boolean = true,
    val storage: dev.tidytv.core.ops.StorageFacts? = null,
    /** Package to cache MB, biggest first. */
    val topCache: List<Pair<String, Int>> = emptyList(),
    val clearing: Boolean = false,
    /** MB freed so far while a clear is running, read from the TV's live free space. */
    val progressMb: Int = 0,
    /** The per-app cache numbers are Android's last daily count, not live; false after a clear sets them to zero. */
    val estimated: Boolean = true,
    val message: RestoreUi.Finished? = null,
)

/** What the My TV hub shows under the TV's name: free storage and free memory, read when the hub opens. */
data class HubFacts(val freeStorageMb: Int? = null, val freeRamMb: Int? = null) {
    val any get() = freeStorageMb != null || freeRamMb != null
}

/** The Apps tile: every app on the TV, with one-tap turn off or uninstall on a single app. */
data class AppsState(
    val loading: Boolean = true,
    val rows: List<dev.tidytv.core.lists.AppRow> = emptyList(),
    val facts: Map<String, dev.tidytv.core.ops.AppFacts> = emptyMap(),
    /** The app whose sheet is open. */
    val open: String? = null,
    /** The app being changed right now. */
    val busy: String? = null,
    val message: RestoreUi.Finished? = null,
)

/** "Block ads": what Private DNS is set to, and what the person is being asked. */
data class AdsState(
    val loading: Boolean = true,
    val dns: dev.tidytv.core.ops.DnsState? = null,
    /** Private DNS needs Android 9 or newer. */
    val supported: Boolean = true,
    val busy: Boolean = false,
    /** Another Private DNS is set; this is the provider the person picked, waiting for "Replace it". */
    val confirmReplace: dev.tidytv.core.ops.AdBlockProvider? = null,
    /** Same, for the owner's own server name waiting for "Replace it". */
    val confirmReplaceHost: String? = null,
    /** The owner's own DNS-over-TLS server this app set before, to pre-fill the field. */
    val lastCustom: String? = null,
    val message: RestoreUi.Finished? = null,
)

data class TidyState(
    val order: List<Candidate> = emptyList(),
    val results: Map<String, PkgResult> = emptyMap(),
    val saving: Boolean = false,
    val reconnecting: Boolean = false,
    val stopReason: String? = null,
    val finished: Boolean = false,
) {
    val disabledCount get() = results.values.count { it.isOff }
}

data class DockApp(val pkg: String, val label: String)

enum class HomeAction { TURN_OFF_SETUP_APP, RETRY }

sealed interface HomeState {
    data class Offer(
        val options: List<DockApp> = emptyList(),
        val chosen: List<String> = emptyList(),
        val app: HomeApp = HomeApp.SIMPLE,
        /** Home screens this TV can run (the styled one needs Android 8). */
        val available: List<HomeApp> = listOf(HomeApp.SIMPLE),
        /** The Apple TV style home screen is already Home, so its look can be changed. */
        val styledIsHome: Boolean = false,
        /** The TV's home app is one TidyTV hasn't tested: no change is offered, only a one-line card. */
        val untested: Boolean = false,
    ) : HomeState
    data object Running : HomeState
    data class Problem(val message: String, val worried: Boolean, val action: HomeAction? = null, val rescue: List<String> = emptyList()) : HomeState
    data class Installed(val app: HomeApp = HomeApp.SIMPLE) : HomeState
    /** Projectivy's Play Store page is open on the TV; waiting for the owner to press Install. */
    data class WaitingForPlayStore(val app: HomeApp) : HomeState
    /** Picking a look for the Apple TV style home screen; [back] is where Back returns. */
    data class Look(val look: LookState, val back: HomeState? = null) : HomeState
}

/** The looks TidyTV ships for the Apple TV style home screen (cleanyourtv themes/, MIT). */
enum class LookId(val id: String, val title: String, val blurb: String) {
    MIDNIGHT("midnight", "Midnight", "Near-black, a quiet wallpaper, a white focus ring and calm motion."),
    CLASSIC("default", "Classic", "Black and simple, the way it comes."),
    AURORA("aurora", "Aurora", "A dark night sky with teal and violet light."),
    GLASS("glass", "Glass", "A frosted dock, soft light and a glow in each app's colour."),
    SCENES("scenes", "Scenes", "14 painted landscapes, a new one every hour."),
}

data class LookState(
    /** A look id, or "photo", while it's being put on the TV. */
    val applying: String? = null,
    /** The look set in this session. */
    val current: String? = null,
    val message: RestoreUi.Finished? = null,
)

/** Check my TV. [rebooting] is the reboot test's progress line, null when it isn't running. */
data class CheckState(
    val running: Boolean = false,
    val report: dev.tidytv.core.ops.CheckReport? = null,
    val retidying: Boolean = false,
    val repairingHome: Boolean = false,
    val rebooting: String? = null,
    val message: RestoreUi.Finished? = null,
)

/** Boot-loop rescue: keep trying a saved TV and put everything back as soon as it answers. */
data class RescueState(
    val tv: dev.tidytv.app.data.KnownTv? = null,
    val attempts: Int = 0,
    val restoring: Boolean = false,
    val result: RestoreUi.Finished? = null,
    val gaveUp: Boolean = false,
)

enum class DebugCheck { UNKNOWN, CHECKING, STILL_ON, OFF }

sealed interface RestoreUi {
    data object Idle : RestoreUi
    data class Running(val count: Int) : RestoreUi
    data class Finished(val message: String, val ok: Boolean) : RestoreUi
}

data class DoneState(
    val freedMb: Int? = null,
    val newHome: Boolean = false,
    val debugging: DebugCheck = DebugCheck.UNKNOWN,
    val records: List<ActionRecord> = emptyList(),
    val restore: RestoreUi = RestoreUi.Idle,
    /** Apps being turned back on one at a time (GitHub #2). */
    val turningOn: Set<String> = emptySet(),
    /** One line about the last single app, e.g. "Netflix is back on." */
    val turnOnNote: RestoreUi.Finished? = null,
    /** The clean-up finished but the TV's home app is untested, so no home screen was offered. */
    val homeUntested: Boolean = false,
)

/** One app on the TV's home screen, for the remote's Apps sheet. */
data class RemoteApp(val pkg: String, val component: String, val label: String)

data class RemoteState(
    val apps: List<RemoteApp> = emptyList(),
    /** The owner's pinned apps for the bar, in order; null until they change it (then the well-known apps lead). */
    val pinned: List<String>? = null,
    val loadingApps: Boolean = false,
    /** The link dropped; the remote is getting it back. */
    val reconnecting: Boolean = false,
    val message: String? = null,
)

/** The apps on the remote's shortcut bar. */
fun RemoteState.bar(): List<RemoteApp> {
    val by = apps.associateBy { it.pkg }
    return dev.tidytv.core.lists.RemoteBar.bar(apps.map { it.pkg }, pinned).mapNotNull { by[it] }
}

/** Every app, pinned ones first. */
fun RemoteState.ordered(): List<RemoteApp> {
    val by = apps.associateBy { it.pkg }
    return dev.tidytv.core.lists.RemoteBar.order(apps.map { it.pkg }, pinned).mapNotNull { by[it] }
}

/** A file picked on the phone, opened lazily so big videos are never loaded into memory. */
class PickedFile(val name: String, val size: Long, val open: () -> java.io.InputStream)

enum class SendStatus { WAITING, SENDING, SENT, NO_SPACE, FAILED, CANCELLED }

data class SendItem(val id: Int, val name: String, val size: Long, val sent: Long = 0, val status: SendStatus = SendStatus.WAITING, val savedAs: String? = null)

data class FilesState(
    val items: List<SendItem> = emptyList(),
    val freeBytes: Long? = null,
    val folder: dev.tidytv.core.ops.SendFolder = dev.tidytv.core.ops.SendFolder.DOWNLOAD,
) {
    val busy get() = items.any { it.status == SendStatus.SENDING || it.status == SendStatus.WAITING }
}

/** One app on the phone that App2TV can offer. */
data class PhoneAppEntry(val pkg: String, val label: String, val system: Boolean = false)

/** App2TV: pick an app on the phone, see whether the TV can take it, send it, read the result. */
data class App2TvState(
    val apps: List<PhoneAppEntry> = emptyList(),
    val loaded: Boolean = false,
    val query: String = "",
    val selected: dev.tidytv.core.ops.PhoneApp? = null,
    /** What the TV said about the selected app before sending, or null while it is being asked. */
    val verdict: dev.tidytv.core.ops.App2Tv.Verdict? = null,
    val stage: dev.tidytv.core.ops.App2Tv.Stage? = null,
    val part: Int = 0,
    val parts: Int = 0,
    val resultText: String? = null,
    val resultOk: Boolean = false,
) {
    val busy get() = stage != null
    val shown get() = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), true) || it.pkg.contains(query.trim(), true) }
}

/** The optional J10TV install, step by step. Every state is something that actually happened. */
sealed interface J10Ui {
    data object Idle : J10Ui
    data class Checking(val what: String) : J10Ui
    data class Ready(val tag: String, val sizeBytes: Long, val abiNote: String, val alreadyInstalled: String?, val removableNow: Boolean = false) : J10Ui
    data class Downloading(val done: Long, val total: Long) : J10Ui
    data class Sending(val done: Long, val total: Long) : J10Ui
    data class Installed(val pkg: String, val tag: String, val launched: Boolean, val integrity: String, val removable: Boolean, val updatedExisting: Boolean) : J10Ui
    /** Nothing installed yet: this TV has no checksum tool, so the only check left is size. Needs the owner's yes. */
    data object WeakCheck : J10Ui
    data class Failed(val message: String, val retry: Boolean = true) : J10Ui
}

/** The optional TizenTube Cobalt install, step by step. Every state is something that actually happened. */
sealed interface CobaltUi {
    data object Idle : CobaltUi
    data class Checking(val what: String) : CobaltUi
    data class Ready(val tag: String, val sizeBytes: Long, val abiNote: String, val bridgeNote: String, val alreadyInstalled: String?, val removableNow: Boolean = false) : CobaltUi
    data class Downloading(val done: Long, val total: Long) : CobaltUi
    data class Sending(val done: Long, val total: Long) : CobaltUi
    data class Installed(val pkg: String, val tag: String, val launched: Boolean, val integrity: String, val removable: Boolean, val updatedExisting: Boolean) : CobaltUi
    /** Nothing installed yet: this TV has no checksum tool, so the only check left is size. Needs the owner's yes. */
    data object WeakCheck : CobaltUi
    data class Failed(val message: String, val retry: Boolean = true) : CobaltUi
}

data class UiState(
    val cobalt: CobaltUi = CobaltUi.Idle,
    val j10: J10Ui = J10Ui.Idle,
    val wizard: Wizard = Wizard(),
    val connect: ConnectState = ConnectState.Scanning(),
    val tv: TvSummary? = null,
    val tvPreview: TvPreview = TvPreview(),
    val review: ReviewState = ReviewState(),
    val tidy: TidyState = TidyState(),
    val home: HomeState = HomeState.Offer(),
    val done: DoneState = DoneState(),
    /** A plain-language message for something that went wrong outside a specific step. */
    val problem: String? = null,
    /** Friendly names from the list notes, so the activity list reads like the review screen. */
    val labels: Map<String, String> = emptyMap(),
    /** TVs this phone has connected to, newest first, for one-tap reconnect on Welcome. */
    val savedTvs: List<dev.tidytv.app.data.KnownTv> = emptyList(),
    /** Saved TVs that answered a quick check on the network just now, by address. */
    val online: Set<String> = emptySet(),
    val remote: RemoteState = RemoteState(),
    val files: FilesState = FilesState(),
    val check: CheckState = CheckState(),
    val space: SpaceState = SpaceState(),
    val ads: AdsState = AdsState(),
    val app2tv: App2TvState = App2TvState(),
    val apps: AppsState = AppsState(),
    val hubFacts: HubFacts = HubFacts(),
    val rescue: RescueState = RescueState(),
    /** The real app icons read from the TV, as PNG files on the phone, by package. */
    val icons: Map<String, String> = emptyMap(),
)

/**
 * Drives the whole flow. The screens only read [ui] and call the public functions; every TV
 * change goes through core (Debloater, LauncherHandover, Restorer), one at a time ([tvLock]).
 */
class WizardViewModel(
    private val store: Store,
    private val trace: Trace,
    private val real: () -> Backend,
    private val demo: DemoBackend?,
    private val lists: () -> ListCatalog,
    private val launcherApk: (HomeApp) -> ByteArray?,
    /** Turns an APK fetched from the TV into a PNG of its icon; returns the file path. Android only. */
    private val iconFromApk: ((pkg: String, apk: ByteArray) -> String?)? = null,
    /** The app's own name from its APK (the same file as the icon). Android only. */
    private val labelFromApk: ((pkg: String, apk: ByteArray) -> String?)? = null,
    /** A bundled look's files (assets/themes/<id>), or null when it isn't bundled. */
    private val looks: (String) -> dev.tidytv.core.ops.LookFiles? = { null },
    /** Shrinks a photo from the phone into a TV wallpaper. Android only. */
    private val photoWallpaper: (PickedFile) -> dev.tidytv.app.data.WallpaperImage? = { null },
    /** Where photos are decoded and shrunk; tests pass their own. */
    private val imageWork: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    /** Reads package id, version and signing certificates out of a downloaded APK. Android only; null refuses to install. */
    private val apkIdentity: ((java.io.File) -> dev.tidytv.core.ops.Cobalt.ApkIdentity?)? = null,
    /** App2TV: the launchable apps on this phone. Android only. */
    private val phoneApps: () -> List<PhoneAppEntry> = { emptyList() },
    /** App2TV: one phone app's package facts (APK files, versions, processor types). Android only. */
    private val phoneApp: (String) -> dev.tidytv.core.ops.PhoneApp? = { null },
    /** App2TV: opens one APK file of a phone app for sending. Android only. */
    private val phoneApkOpen: (String, dev.tidytv.core.ops.PhoneApk) -> java.io.InputStream = { _, _ -> throw java.io.IOException("not on this phone") },
) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        _ui.update { it.copy(savedTvs = runCatching { store.knownTvs() }.getOrDefault(emptyList()).sortedByDescending { t -> t.lastSeen }) }
        probeSaved()
    }

    private var backend: Backend? = null
    private var shell: TvShell? = null
    private var snapshot: TvSnapshot? = null
    private var list: DebloatList? = null
    private var log: ActionLog? = null
    private var found: FoundTv? = null
    private val tvLock = Mutex()

    private val previewCache = TvPreviewCache(store.tvPreviewsDir)
    private var previewJob: Job? = null
    private var previewSession = 0L
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var allowTimer: Job? = null

    // ---------- Wizard ----------

    private fun wizard(e: WizardEvent) {
        val before = _ui.value.wizard
        val next = WizardReducer.reduce(before, e) ?: return
        if (next == before) return
        trace.line("wizard ${before.step} -> ${next.step} (${e::class.simpleName})")
        _ui.update { it.copy(wizard = next) }
        if (before.step != next.step) onEnter(next.step, before.step)
    }

    private fun onEnter(step: Step, from: Step) {
        when (step) {
            Step.CONNECT -> pendingSaved?.let { saved -> pendingSaved = null; connect(ConnectEvent.Pick(saved)) } ?: connect(ConnectEvent.StartScan)
            Step.WELCOME -> disconnect()
            Step.DONE -> enterDone()
            Step.HOME_SCREEN -> enterHome()
            Step.HUB -> enterHub()
            Step.REVIEW -> if (from == Step.HUB) snapshot?.let(::prepareReview)
            Step.REMOTE -> enterRemote()
            Step.SEND_FILES -> enterFiles()
            Step.COBALT -> enterCobalt()
            Step.J10TV -> enterJ10Tv()
            Step.APP2TV -> enterApp2Tv()
            Step.CHECK -> recheck()
            Step.SPACE -> enterSpace()
            Step.ADS -> enterAds()
            Step.APPS -> loadApps()
            Step.RESCUE -> Unit
            else -> Unit
        }
    }

    fun choose(kind: dev.tidytv.core.flow.TvKind) = wizard(WizardEvent.Choose(kind))

    /**
     * "Connect a TV": an Android TV, straight to the scan once TidyTV has connected before. First time, the
     * get-ready steps show once. Fire TV, Samsung and LG are chosen from "Another kind of TV".
     */
    fun connectNew() {
        choose(dev.tidytv.core.flow.TvKind.ANDROID_TV)
        if (store.onboarded()) ready()
    }

    /** A quick, quiet check of which saved TVs answer on the network now, for the green dots on the first screen. */
    fun probeSaved() {
        val tvs = _ui.value.savedTvs.take(5)
        if (tvs.isEmpty()) return
        viewModelScope.launch {
            val b = runCatching { real() }.getOrNull() ?: return@launch
            val up = tvs.map { tv -> async { if (runCatching { b.portOpen(tv.host) }.getOrDefault(false)) tv.host else null } }.awaitAll().filterNotNull().toSet()
            _ui.update { it.copy(online = up) }
        }
    }
    fun restoreTapped() = wizard(WizardEvent.RestoreTapped)
    fun ready() = wizard(WizardEvent.Ready)
    fun startOver() = wizard(WizardEvent.StartOver)
    fun openTidy() = wizard(WizardEvent.OpenTidy)
    fun openRemote() = wizard(WizardEvent.OpenRemote)
    fun openFiles() = wizard(WizardEvent.OpenFiles)
    fun openApp2Tv() = wizard(WizardEvent.OpenApp2Tv)
    fun openCobalt() = wizard(WizardEvent.OpenCobalt)
    fun openJ10Tv() = wizard(WizardEvent.OpenJ10Tv)
    fun openHomeScreen() = wizard(WizardEvent.OpenHomeScreen)
    fun openRestore() = wizard(WizardEvent.OpenRestore)
    fun toHub() = wizard(WizardEvent.ToHub)
    fun openCheck() = wizard(WizardEvent.OpenCheck)
    fun openSpace() = wizard(WizardEvent.OpenSpace)
    fun openAds() = wizard(WizardEvent.OpenAds)
    fun openApps() = wizard(WizardEvent.OpenApps)

    private var pendingSaved: FoundTv? = null

    /** One tap on a saved TV: straight to connecting, no setup steps. The saved key means no Allow prompt. */
    fun connectSaved(tv: dev.tidytv.app.data.KnownTv) {
        pendingSaved = FoundTv(tv.host, name = tv.title, model = tv.model, legacyOpen = true)
        wizard(WizardEvent.ConnectSaved)
    }
    fun toDone() = wizard(WizardEvent.ToDone)
    fun dismissProblem() = _ui.update { it.copy(problem = null) }

    /** Returns false when Back should close the app. */
    fun back(): Boolean {
        val s = _ui.value
        if (s.wizard.step == Step.CONNECT && s.connect !is ConnectState.Scanning && s.connect !is ConnectState.Connected) {
            connect(ConnectEvent.Back); return true
        }
        if (s.wizard.step == Step.HOME_SCREEN && s.home is HomeState.Look) { closeLook(); return true }
        val next = WizardReducer.reduce(s.wizard, WizardEvent.Back) ?: return false
        wizard(WizardEvent.Back)
        return true
    }

    // ---------- Connect ----------

    fun connect(e: ConnectEvent) {
        val old = _ui.value.connect
        val new = ConnectMachine.reduce(old, e)
        if (new == old && e !is ConnectEvent.StartScan) return
        if (new::class != old::class) trace.line("connect ${old::class.simpleName} -> ${new::class.simpleName}")
        _ui.update { it.copy(connect = new) }

        if (new !is ConnectState.Scanning) scanJob?.cancel()
        if (new is ConnectState.Scanning && (e is ConnectEvent.StartScan || e is ConnectEvent.Back || old !is ConnectState.Scanning)) startScan()
        if (new is ConnectState.Connecting && (old !is ConnectState.Connecting || old.tv != new.tv)) startConnect(new.tv)
        if (new is ConnectState.Pairing && new.busy && !(old is ConnectState.Pairing && old.busy)) startPair(new.tv, (e as ConnectEvent.SubmitCode).code)
        if (new is ConnectState.PressAllow && old is ConnectState.Connecting) {
            allowTimer?.cancel()
            allowTimer = viewModelScope.launch { delay(30_000); connect(ConnectEvent.AllowRetryAvailable) }
        }
        if (new !is ConnectState.PressAllow) allowTimer?.cancel()
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            launch { delay(3_500); connect(ConnectEvent.ScanDone) }
            demo?.let { d -> launch { d.scan().collect { connect(ConnectEvent.Found(it)) } } }
            withTimeoutOrNull(20_000) {
                runCatching { real().scan().collect { connect(ConnectEvent.Found(it)) } }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; trace.line("scan failed: ${it.message}") }
            }
        }
    }

    private fun backendFor(tv: FoundTv): Backend = if (demo != null && tv.host == DemoBackend.DEMO.host) demo else real()

    private fun startConnect(tv: FoundTv) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val b = backendFor(tv)
            val link = b.connect(tv) { connect(ConnectEvent.AwaitingAllow) }
            if (link.attempt == Attempt.CONNECTED && link.shell != null) onLinked(b, tv, link.shell)
            connect(ConnectEvent.Result(link.attempt, link.message))
        }
    }

    private fun startPair(tv: FoundTv, code: String) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val b = backendFor(tv)
            val link = b.pair(tv, code)
            if (link.attempt == Attempt.CONNECTED && link.shell != null) onLinked(b, tv, link.shell)
            connect(ConnectEvent.Result(link.attempt, link.message))
        }
    }

    private suspend fun onLinked(b: Backend, tv: FoundTv, s: TvShell) {
        previewSession++
        previewJob?.cancel()
        _ui.update { it.copy(tvPreview = TvPreview()) }
        backend = b
        shell = s
        found = tv
        // Right after "Allow" some TVs answer very slowly or drop the link once. A watchdog closes a link that says
        // nothing for 25 s (a blocked read can't be cancelled any other way), then it is reconnected and tried again.
        var current = s
        var snap: TvSnapshot? = null
        for (attempt in 1..3) {
            val watchdog = viewModelScope.launch { delay(SNAPSHOT_PATIENCE_MS); trace.line("no answer in ${SNAPSHOT_PATIENCE_MS / 1000} s, closing the link"); b.close(current) }
            snap = try {
                tvLock.withLock { TvReader.snapshot(current) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                trace.line("snapshot try $attempt failed: $e")
                null
            } finally {
                watchdog.cancel()
            }
            if (snap != null || attempt == 3) break
            delay(2_000)
            b.close(current)
            val link = b.connect(tv) {}
            if (link.attempt == Attempt.CONNECTED && link.shell != null) { current = link.shell; shell = current } else trace.line("reconnect for the snapshot: ${link.attempt}")
        }
        if (snap == null) {
            _ui.update { it.copy(problem = "Your TV connected but is slow to answer, so 0TV couldn't read it. Keep the TV on and awake, then tap Connect again. If it keeps happening, tap Share logs.") }
            return
        }
        snapshot = snap
        log = store.actionLog(snap.info.serial)
        val summary = TvSummary(
            title = tv.name ?: snap.info.displayName, model = tv.model ?: snap.info.displayName, host = tv.host,
            serial = snap.info.serial, sdk = snap.info.sdk, release = snap.info.release,
        )
        store.remember(KnownTv(summary.serial, summary.title, summary.model, tv.host, System.currentTimeMillis()))
        store.markOnboarded()
        trace.line("tv ${snap.info.displayName} sdk ${snap.info.sdk} home ${snap.home}")
        _ui.update { it.copy(tv = summary) }
        val fire = snap.info.manufacturer.equals("Amazon", true) || snap.info.brand.equals("Amazon", true)
        loadLabels(snap)
        wizard(WizardEvent.Connected(fireOs = fire))
    }

    /** Restore mode skips the review, but the activity list should still use the list's names. */
    private fun loadLabels(snap: TvSnapshot) {
        val l = ModelMatcher.match(snap.info, lists().lists, installed = snap.installed).firstOrNull()?.list ?: return
        list = l
        val names = (l.entries + l.optional).associate { it.pkg to AppLabels.label(it.pkg, it.note) }
        _ui.update { it.copy(labels = it.labels + names) }
    }

    private fun disconnect() {
        previewSession++
        previewJob?.cancel()
        scanJob?.cancel(); connectJob?.cancel(); allowTimer?.cancel()
        backend?.close(shell)
        shell = null
        snapshot = null
        remote?.close(); remote = null; remoteShell = null
        _ui.update { UiState(wizard = it.wizard, savedTvs = it.savedTvs, online = it.online) }
        probeSaved()
    }

    /**
     * Gets the session back after Wi-Fi blips. Tries the same address, then looks for the TV by
     * name in case the router gave it a new one. The saved key means no new Allow prompt.
     */
    private suspend fun reconnect(): Boolean {
        val b = backend ?: return false
        var tv = found ?: return false
        backend?.close(shell)
        shell = null
        trace.line("reconnecting to ${tv.host}")
        repeat(8) { attempt ->
            val link = b.connect(tv) {}
            if (link.attempt == Attempt.CONNECTED && link.shell != null) {
                shell = link.shell
                trace.line("reconnected after ${attempt + 1} tries")
                return true
            }
            if (attempt == 3 && tv.name != null) {
                val name = tv.name
                val moved = withTimeoutOrNull(6_000) { b.scan().first { f -> f.name == name && f.host != tv.host } }
                if (moved != null) { tv = tv.copy(host = moved.host); found = tv; trace.line("tv moved to ${tv.host}") }
            }
            delay(3_000)
        }
        trace.line("reconnect gave up")
        return false
    }

    // ---------- My TV hub ----------

    /** Reuse the first successful still. Only uncached devices need a capture. [force] (tap on the picture) always takes a fresh still and updates the cache. */
    fun refreshTvPreview(force: Boolean = false) {
        if (previewJob?.isActive == true) return
        if (!force && _ui.value.tvPreview.image != null) return
        val selected = _ui.value.tv ?: return
        val activeShell = shell ?: return
        val session = previewSession
        _ui.update { it.copy(tvPreview = it.tvPreview.copy(loading = true)) }
        previewJob = viewModelScope.launch {
            val cached = if (force) null else withContext(imageWork) { previewCache.read(selected) }
            val image = cached ?: try {
                val bytes = withTimeoutOrNull(12_000) {
                    tvLock.withLock { dev.tidytv.core.ops.ScreenCapture.read(activeShell) }
                }
                bytes?.let { withContext(imageWork) { TvPreview.decode(it) } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (session == previewSession && shell === activeShell && _ui.value.tv == selected) {
                if (image != null && (cached == null || force)) withContext(imageWork) { previewCache.save(selected, image) }
                if (session == previewSession && shell === activeShell && _ui.value.tv == selected)
                    _ui.update { it.copy(tvPreview = TvPreview(image ?: it.tvPreview.image)) }
            }
        }
    }

    private fun enterHub() {
        refreshTvPreview()
        viewModelScope.launch {
            // Two quick reads; anything the TV doesn't answer is simply not shown.
            val facts = withShell { s ->
                val freeKb = runCatching { dev.tidytv.core.ops.Parse.dfAvailableKb(s.run(dev.tidytv.core.ops.Commands.DF_DATA)) }.getOrNull()
                val ramKb = runCatching { dev.tidytv.core.ops.Parse.memAvailableKb(s.run(dev.tidytv.core.ops.Commands.MEMINFO)) }.getOrNull()
                HubFacts(freeKb?.let { (it / 1024).toInt() }, ramKb?.let { (it / 1024).toInt() })
            }
            if (facts != null) _ui.update { it.copy(hubFacts = facts) }
        }
        _ui.update { it.copy(done = it.done.copy(records = log?.records().orEmpty()), remote = RemoteState(apps = it.remote.apps, pinned = it.remote.pinned)) }
        _ui.update { it.copy(savedTvs = runCatching { store.knownTvs() }.getOrDefault(it.savedTvs).sortedByDescending { t -> t.lastSeen }) }
    }

    // ---------- Remote ----------

    private var remote: dev.tidytv.core.ops.Remote? = null
    private var remoteShell: TvShell? = null

    private fun remoteFor(s: TvShell): dev.tidytv.core.ops.Remote {
        if (remoteShell !== s) { remote?.close(); remote = dev.tidytv.core.ops.Remote(s); remoteShell = s }
        return remote!!
    }

    private fun enterRemote() {
        if (_ui.value.remote.apps.isNotEmpty()) return
        _ui.update { it.copy(remote = it.remote.copy(loadingApps = true)) }
        viewModelScope.launch {
            val apps = withRemote { r -> r.apps() }.orEmpty()
            val labels = _ui.value.labels
            val list = apps.map { a -> RemoteApp(a.pkg, a.component, labels[a.pkg] ?: AppLabels.label(a.pkg)) }.sortedBy { it.label.lowercase() }
            val saved = _ui.value.tv?.serial?.let { runCatching { store.pinnedApps(it) }.getOrNull() }
            _ui.update { it.copy(remote = it.remote.copy(apps = list, pinned = saved, loadingApps = false)) }
            fetchIcons(list.map { it.pkg })
        }
    }

    fun press(key: dev.tidytv.core.ops.RemoteKey) {
        viewModelScope.launch { withRemote { it.press(key) } }
    }

    /** Types into the focused field on the TV. Characters `input text` can't send are dropped and the user is told. */
    fun type(text: String) {
        if (dev.tidytv.core.ops.TextInput.unsupported(text)) {
            _ui.update { it.copy(remote = it.remote.copy(message = "Some characters (like emoji or accents) can't be typed on the TV and were left out.")) }
        }
        viewModelScope.launch { withRemote { it.type(text) } }
    }

    private fun setPinned(list: List<String>) {
        _ui.update { it.copy(remote = it.remote.copy(pinned = list)) }
        _ui.value.tv?.serial?.let { runCatching { store.savePinned(it, list) } }
    }

    /** Pin an app to the remote bar, or take it off. */
    fun togglePin(pkg: String) {
        val r = _ui.value.remote
        setPinned(dev.tidytv.core.lists.RemoteBar.toggle(r.apps.map { it.pkg }, r.pinned, pkg))
    }

    /** Move a pinned app earlier (-1) or later (+1) on the bar. */
    fun movePin(pkg: String, delta: Int) {
        val r = _ui.value.remote
        setPinned(dev.tidytv.core.lists.RemoteBar.move(r.apps.map { it.pkg }, r.pinned, pkg, delta))
    }

    fun launchApp(app: RemoteApp) {
        viewModelScope.launch { withRemote { it.launch(dev.tidytv.core.ops.TvApp(app.pkg, app.component)) } }
    }

    fun dismissRemoteMessage() = _ui.update { it.copy(remote = it.remote.copy(message = null)) }

    /** Runs a remote action; on a dropped link, reconnects once (showing it) and tries again. */
    private suspend fun <T> withRemote(block: suspend (dev.tidytv.core.ops.Remote) -> T): T? {
        for (attempt in 0..1) {
            val s = shell ?: run {
                _ui.update { it.copy(remote = it.remote.copy(reconnecting = true)) }
                val ok = reconnect()
                _ui.update { it.copy(remote = it.remote.copy(reconnecting = false)) }
                if (ok) shell else null
            } ?: break
            try {
                return block(remoteFor(s))
            } catch (e: TvConnectionLost) {
                trace.line("remote: ${e.message}")
                remote?.close(); remote = null; remoteShell = null
                shell = null
            }
        }
        _ui.update { it.copy(remote = it.remote.copy(message = "The TV stopped answering. Check it's on and on the same network.")) }
        return null
    }

    // ---------- Check my TV ----------

    private fun runCheck() {
        val lg = log ?: return
        _ui.update { it.copy(check = it.check.copy(running = true)) }
        viewModelScope.launch {
            val report = withShell { s ->
                val snap = snapshot
                val serial = _ui.value.tv?.serial
                val l = list
                val listPkgs = l?.let { it.entries.map { e -> e.pkg } + it.optional.map { e -> e.pkg } }.orEmpty()
                val before = serial?.let { store.latestBackup(it) }?.packages?.toSet()
                dev.tidytv.core.ops.TvCheck(s, lg).run(listPkgs, before, snap?.liveProtected.orEmpty(), l?.keep.orEmpty())
            }
            trace.line("check: $report")
            _ui.update { it.copy(check = it.check.copy(running = false, report = report,
                message = if (report == null) RestoreUi.Finished("The TV stopped answering. Check it's on and on the same network.", ok = false) else it.check.message)) }
        }
    }

    /**
     * Some TVs (Haier and others) turn their own home screen back on at every restart, so Home goes back to Google's.
     * This puts TidyTV's home screen back as Home with the same careful handover as the first time, then checks again.
     */
    fun repairHome() {
        val app = _ui.value.check.report?.repairableHome ?: return
        val apk = launcherApk(app) ?: return
        val lg = log ?: return
        val sdk = _ui.value.tv?.sdk ?: 0
        if (_ui.value.check.repairingHome || _ui.value.check.running) return
        _ui.update { it.copy(check = it.check.copy(repairingHome = true, message = null)) }
        wizard(WizardEvent.Busy(true))
        viewModelScope.launch {
            val outcome = withShell { s -> LauncherHandover(s, lg, sleep = { _ -> delay(backend?.saveWaitMs ?: 0) }, app = app).run(apk, emptyList(), sdk) }
            trace.line("repair home (${app.name}): $outcome")
            val msg = when (outcome) {
                is HandoverOutcome.Done -> RestoreUi.Finished("Your home screen is back. If it goes back again after a restart, open Check my TV and tap Put my home screen back.", ok = true)
                null -> RestoreUi.Finished("The TV stopped answering. Check it's on, then try again.", ok = false)
                else -> RestoreUi.Finished("0TV couldn't put your home screen back this time. Open Home screen on My TV to try again, or share logs.", ok = false)
            }
            _ui.update { it.copy(check = it.check.copy(repairingHome = false, message = msg)) }
            wizard(WizardEvent.Busy(false))
            runCheck()
        }
    }

    /** Turns the voice assistant or a remote helper back on from Check my TV, then checks again. */
    fun turnOnRemoteHelper(pkg: String) {
        val lg = log ?: return
        if (_ui.value.check.running) return
        _ui.update { it.copy(check = it.check.copy(running = true)) }
        viewModelScope.launch {
            val outcome = withShell { s -> Restorer(s, lg).enableOnly(setOf(pkg)) }
            trace.line("turn on remote helper $pkg: $outcome")
            _ui.update { it.copy(check = it.check.copy(running = false)) }
            runCheck()
        }
    }

    /** Turns off again what a firmware update brought back, after a fresh backup, like any clean-up. */
    fun retidy() {
        val report = _ui.value.check.report ?: return
        val lg = log ?: return
        val snap = snapshot ?: return
        if (report.toTidy.isEmpty()) return
        wizard(WizardEvent.Busy(true))
        _ui.update { it.copy(check = it.check.copy(retidying = true, message = null)) }
        viewModelScope.launch {
            val outcome = withShell { s ->
                store.saveBackup(TvReader.backup(s, snap, System.currentTimeMillis()))
                val removed = lg.records().removedByUs()
                Debloater(s, lg, sleep = { delay(backend?.saveWaitMs ?: 0) })
                    .run(report.toTidy, snap.liveProtected, list?.keep.orEmpty(), remove = report.toTidy.filter { it in removed }.toSet())
            }
            trace.line("re-tidy: $outcome")
            val off = outcome?.results?.count { it.isOff } ?: 0
            _ui.update { it.copy(check = it.check.copy(retidying = false, message = when (outcome) {
                is dev.tidytv.core.ops.DebloatOutcome.Finished -> RestoreUi.Finished("Tidy again: $off ${if (off == 1) "app" else "apps"} turned off.", ok = true)
                is dev.tidytv.core.ops.DebloatOutcome.Stopped -> RestoreUi.Finished("Stopped: ${outcome.reason}", ok = false)
                else -> RestoreUi.Finished("The TV stopped answering. Check it's on, then try again.", ok = false)
            })) }
            wizard(WizardEvent.Busy(false))
            runCheck()
        }
    }

    /**
     * The reboot test: restart the TV, wait for it to come back (up to 3 min), then check that
     * everything TidyTV turned off is still off and Home is still right.
     */
    fun rebootTest() {
        val b = backend ?: return
        val tv = found ?: return
        wizard(WizardEvent.Busy(true))
        _ui.update { it.copy(check = it.check.copy(rebooting = "Restarting your TV…", message = null)) }
        viewModelScope.launch {
            shell?.let { s -> runCatching { tvLock.withLock { s.run(dev.tidytv.core.ops.Commands.REBOOT) } } }
            b.close(shell); shell = null
            trace.line("reboot test: restarting")
            delay(15_000)
            var back = false
            val deadline = 36 // 36 x 5 s = 3 minutes
            for (i in 1..deadline) {
                _ui.update { it.copy(check = it.check.copy(rebooting = "Waiting for your TV to start… ${(deadline - i) * 5 / 60 + 1} min left")) }
                val link = runCatching { b.connect(tv) {} }.getOrNull()
                if (link?.attempt == Attempt.CONNECTED && link.shell != null) { shell = link.shell; back = true; break }
                delay(5_000)
            }
            trace.line("reboot test: back=$back")
            if (back) runCatching { tvLock.withLock { shell?.let { s -> snapshot = TvReader.snapshot(s) } } }
            _ui.update { it.copy(check = it.check.copy(rebooting = null, message = if (back) null
                else RestoreUi.Finished("Your TV hasn't come back yet. Check it's on, then reconnect from the first screen.", ok = false))) }
            wizard(WizardEvent.Busy(false))
            if (back) runCheck()
        }
    }

    /** Runs [block] on the TV, reconnecting once if the link dropped. Null when the TV is gone. */
    private suspend fun <T> withShell(block: suspend (TvShell) -> T): T? {
        for (attempt in 0..1) {
            val s = shell ?: if (reconnect()) shell else null
            if (s == null) return null
            try {
                return tvLock.withLock { block(s) }
            } catch (e: TvConnectionLost) {
                trace.line("lost: ${e.message}")
                shell = null
            }
        }
        return null
    }

    // ---------- Rescue ----------

    private var rescueJob: Job? = null

    /**
     * For a TV stuck after a change: keep trying the saved TV every 5 s for up to 10 minutes, and the
     * moment it answers, turn everything TidyTV changed back on (the undo list on the TV is enough).
     */
    fun startRescue(saved: dev.tidytv.app.data.KnownTv) {
        wizard(WizardEvent.OpenRescue)
        rescueJob?.cancel()
        _ui.update { it.copy(rescue = RescueState(tv = saved)) }
        rescueJob = viewModelScope.launch {
            val ftv = FoundTv(saved.host, name = saved.title, model = saved.model, legacyOpen = true)
            val b = backendFor(ftv)
            for (i in 1..120) {
                _ui.update { it.copy(rescue = it.rescue.copy(attempts = i)) }
                val link = runCatching { b.connect(ftv) {} }.getOrNull()
                if (link?.attempt == Attempt.CONNECTED && link.shell != null) {
                    backend = b; shell = link.shell; found = ftv
                    val lg = store.actionLog(saved.serial)
                    log = lg
                    _ui.update { it.copy(rescue = it.rescue.copy(restoring = true)) }
                    val out = runCatching { tvLock.withLock { Restorer(link.shell, lg).run() } }.getOrNull()
                    trace.line("rescue: $out")
                    val msg = when (out) {
                        is RestoreOutcome.Done -> RestoreUi.Finished("Your TV answered and everything 0TV changed is back on. Restart the TV if it still looks stuck.", ok = true)
                        is RestoreOutcome.Partial -> RestoreUi.Finished("${out.stillOff.size} apps wouldn't turn back on. Tap Try again.", ok = false)
                        RestoreOutcome.WrongTv -> RestoreUi.Finished(WRONG_TV_MESSAGE, ok = false)
                        else -> RestoreUi.Finished("The TV dropped off again. Tap Try again.", ok = false)
                    }
                    _ui.update { it.copy(rescue = it.rescue.copy(restoring = false, result = msg)) }
                    return@launch
                }
                delay(5_000)
            }
            _ui.update { it.copy(rescue = it.rescue.copy(gaveUp = true)) }
        }
    }

    /** Re-runs Check my TV (after the user fixed something on the TV). */
    fun recheck() {
        _ui.update { it.copy(check = it.check.copy(message = null)) }
        runCheck()
    }

    fun stopRescue() {
        rescueJob?.cancel()
        wizard(WizardEvent.Back)
    }

    // ---------- App2TV ----------

    private var a2tJob: Job? = null

    private fun enterApp2Tv() {
        _ui.update { it.copy(app2tv = App2TvState()) }
        viewModelScope.launch {
            val list = withContext(imageWork) { runCatching { phoneApps() }.getOrDefault(emptyList()) }
            _ui.update { it.copy(app2tv = it.app2tv.copy(apps = list.sortedWith(compareBy({ a -> a.system }, { a -> a.label.lowercase() })), loaded = true)) }
        }
    }

    fun setApp2TvQuery(q: String) = _ui.update { it.copy(app2tv = it.app2tv.copy(query = q)) }

    /** Opens one phone app and asks the TV, before anything is sent, whether it can take it. */
    fun pickPhoneApp(pkg: String) {
        if (_ui.value.app2tv.busy) return
        _ui.update { it.copy(app2tv = it.app2tv.copy(selected = null, verdict = null, resultText = null)) }
        viewModelScope.launch {
            val app = withContext(imageWork) { runCatching { phoneApp(pkg) }.getOrNull() }
            if (app == null) {
                _ui.update { it.copy(app2tv = it.app2tv.copy(resultText = "This app's files can't be read from the phone, so it can't be sent.", resultOk = false)) }
                return@launch
            }
            _ui.update { it.copy(app2tv = it.app2tv.copy(selected = app)) }
            val verdict = runCatching {
                val s = shell ?: if (reconnect()) shell else null
                if (s == null) null else tvLock.withLock {
                    dev.tidytv.core.ops.App2Tv.check(app, dev.tidytv.core.ops.App2Tv.readTarget(s), dev.tidytv.core.ops.App2Tv.readTvCopy(s, app.pkg))
                }
            }.getOrNull() ?: dev.tidytv.core.ops.App2Tv.Verdict.Blocked("The TV isn't answering. Check it's on and connected, then pick the app again.")
            if (_ui.value.app2tv.selected?.pkg == app.pkg) _ui.update { it.copy(app2tv = it.app2tv.copy(verdict = verdict)) }
        }
    }

    fun closePhoneApp() {
        if (_ui.value.app2tv.busy) return
        _ui.update { it.copy(app2tv = it.app2tv.copy(selected = null, verdict = null, resultText = null)) }
    }

    fun sendPhoneApp() {
        val app = _ui.value.app2tv.selected ?: return
        if (_ui.value.app2tv.busy) return
        _ui.update { it.copy(app2tv = it.app2tv.copy(stage = dev.tidytv.core.ops.App2Tv.Stage.CHECKING, part = 0, parts = app.apks.size, resultText = null)) }
        a2tJob = viewModelScope.launch {
            var out: dev.tidytv.core.ops.App2Tv.Outcome = dev.tidytv.core.ops.App2Tv.Outcome.Disconnected
            for (attempt in 0..1) {
                val s = shell ?: if (reconnect()) shell else null
                if (s == null) break
                out = tvLock.withLock {
                    dev.tidytv.core.ops.App2Tv.send(s, app, { apk -> phoneApkOpen(app.pkg, apk) }, (log ?: _ui.value.tv?.serial?.let { store.actionLog(it) } ?: dev.tidytv.core.ops.MemoryActionLog()),
                        onStage = { st, n, total -> _ui.update { it.copy(app2tv = it.app2tv.copy(stage = st, part = n, parts = total)) } })
                }
                if (out !is dev.tidytv.core.ops.App2Tv.Outcome.Disconnected) break
                shell = null
            }
            trace.line("app2tv ${app.pkg}: ${out::class.simpleName}")
            val (text, ok) = when (val o = out) {
                is dev.tidytv.core.ops.App2Tv.Outcome.Installed ->
                    ((if (o.updated) "Updated. " else "Installed. ") + dev.tidytv.core.ops.App2Tv.opensText(o.opens, o.label)) to true
                is dev.tidytv.core.ops.App2Tv.Outcome.Blocked -> o.reason to false
                is dev.tidytv.core.ops.App2Tv.Outcome.Failed -> o.why to false
                is dev.tidytv.core.ops.App2Tv.Outcome.TransferMismatch -> "The copy of ${o.file} on the TV didn't match the phone's, so nothing was installed. Try again." to false
                dev.tidytv.core.ops.App2Tv.Outcome.Unconfirmed -> "The TV's installer said it worked, but ${app.label} doesn't show up on the TV. Nothing else was changed. Try again." to false
                dev.tidytv.core.ops.App2Tv.Outcome.Disconnected -> "The TV stopped answering. Check it's on and connected, then try again." to false
            }
            _ui.update { it.copy(app2tv = it.app2tv.copy(stage = null, resultText = text, resultOk = ok)) }
        }
    }

    // ---------- Send files ----------

    private var sendJob: Job? = null
    private var nextFileId = 0

    private fun enterFiles() {
        if (_ui.value.files.busy) return
        val saved = _ui.value.tv?.serial?.let { runCatching { store.sendFolder(it) }.getOrNull() }
        _ui.update { it.copy(files = it.files.copy(folder = dev.tidytv.core.ops.SendFolder.of(saved))) }
        viewModelScope.launch {
            val s = shell ?: return@launch
            val free = runCatching { tvLock.withLock { dev.tidytv.core.ops.FileSender(s).freeBytes() } }.getOrNull()
            _ui.update { it.copy(files = it.files.copy(freeBytes = free)) }
        }
    }

    /** Where files go on the TV. Not while a send is running; remembered for this TV. */
    fun setSendFolder(folder: dev.tidytv.core.ops.SendFolder) {
        if (_ui.value.files.busy) return
        _ui.update { it.copy(files = it.files.copy(folder = folder)) }
        _ui.value.tv?.serial?.let { runCatching { store.saveSendFolder(it, folder.name) } }
    }

    /** Queues picked files and sends them one at a time into the chosen folder's TidyTV folder on the TV. */
    fun sendFiles(picked: List<PickedFile>) {
        if (picked.isEmpty()) return
        val items = picked.map { p -> SendItem(nextFileId++, p.name, p.size) }
        val byId = items.zip(picked).associate { (i, p) -> i.id to p }
        _ui.update { it.copy(files = it.files.copy(items = it.files.items + items)) }
        if (sendJob?.isActive == true) { queued += byId; retryable += byId; return }
        queued += byId
        retryable += byId
        sendJob = viewModelScope.launch { drainQueue() }
    }

    private val queued = linkedMapOf<Int, PickedFile>()

    private suspend fun drainQueue() {
        while (queued.isNotEmpty()) {
            val (id, p) = queued.entries.first()
            queued.remove(id)
            if (_ui.value.files.items.firstOrNull { it.id == id }?.status == SendStatus.CANCELLED) continue
            setItem(id) { it.copy(status = SendStatus.SENDING) }
            var result: dev.tidytv.core.ops.SendResult = dev.tidytv.core.ops.SendResult.Disconnected
            for (attempt in 0..1) {
                val s = shell ?: if (reconnect()) shell else null
                if (s == null) break
                result = tvLock.withLock {
                    val sender = dev.tidytv.core.ops.FileSender(s, _ui.value.files.folder)
                    val taken = runCatching { sender.takenNames() }.getOrDefault(emptySet()).toMutableSet()
                    sender.send(p.name, p.size, p.open, taken) { sent -> setItem(id) { it.copy(sent = sent) } }
                }
                if (result !is dev.tidytv.core.ops.SendResult.Disconnected) break
                shell = null
            }
            trace.line("send ${p.size} bytes: $result")
            setItem(id) {
                when (val r = result) {
                    is dev.tidytv.core.ops.SendResult.Sent -> it.copy(status = SendStatus.SENT, sent = it.size, savedAs = r.remotePath.substringAfterLast('/'))
                    is dev.tidytv.core.ops.SendResult.NoSpace -> it.copy(status = SendStatus.NO_SPACE)
                    dev.tidytv.core.ops.SendResult.Disconnected -> it.copy(status = SendStatus.FAILED)
                }
            }
        }
        enterFiles()
    }

    /** Skips a file that hasn't started yet. */
    fun cancelFile(id: Int) {
        queued.remove(id)
        setItem(id) { if (it.status == SendStatus.WAITING) it.copy(status = SendStatus.CANCELLED) else it }
    }

    fun retryFile(id: Int, file: PickedFile? = null) {
        val item = _ui.value.files.items.firstOrNull { it.id == id } ?: return
        val p = file ?: retryable[id] ?: return
        setItem(id) { item.copy(status = SendStatus.WAITING, sent = 0) }
        queued[id] = p
        if (sendJob?.isActive != true) sendJob = viewModelScope.launch { drainQueue() }
    }

    /** Picked files are kept so a failed one can be retried without picking it again. */
    private val retryable = mutableMapOf<Int, PickedFile>()

    private fun setItem(id: Int, f: (SendItem) -> SendItem) = _ui.update { u ->
        u.copy(files = u.files.copy(items = u.files.items.map { if (it.id == id) f(it) else it }))
    }

    // ---------- Optional TizenTube Cobalt ----------

    private var cobaltPlan: dev.tidytv.core.ops.Cobalt.Plan? = null
    private var cobaltJob: kotlinx.coroutines.Job? = null

    private fun http(url: String): java.net.HttpURLConnection {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "TidyTV"); c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    /** Looks at the TV and at the project's latest release. Changes nothing. */
    fun refreshCobalt() = enterCobalt()

    private fun enterCobalt() {
        if (_ui.value.cobalt is CobaltUi.Downloading || _ui.value.cobalt is CobaltUi.Sending) return
        cobaltJob?.cancel()
        cobaltJob = viewModelScope.launch {
            _ui.update { it.copy(cobalt = CobaltUi.Checking("Checking your TV and the latest release")) }
            val s = shell ?: return@launch
            val pre = try { tvLock.withLock { dev.tidytv.core.ops.Cobalt.preflight(s) } } catch (e: TvConnectionLost) {
                _ui.update { it.copy(cobalt = CobaltUi.Failed("The TV stopped answering. Check it is on, then try again.")) }; return@launch
            }
            val body = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { http(dev.tidytv.core.ops.Cobalt.RELEASE_API).run { if (responseCode == 200) inputStream.bufferedReader().readText() else null } }.getOrNull()
            }
            val rel = body?.let(dev.tidytv.core.ops.Cobalt::parseRelease)
            if (rel == null) { _ui.update { it.copy(cobalt = CobaltUi.Failed("Couldn't read the TizenTube Cobalt releases from GitHub. Check the phone's internet and try again.")) }; return@launch }
            when (val p = dev.tidytv.core.ops.Cobalt.pick(rel, pre.abis)) {
                is dev.tidytv.core.ops.Cobalt.Pick.UnsupportedAbi -> _ui.update { it.copy(cobalt = CobaltUi.Failed("This TV reports ${p.abis.joinToString().ifEmpty { "no known processor type" }}. TizenTube Cobalt only ships ARM builds, so it can't run here.", retry = false)) }
                is dev.tidytv.core.ops.Cobalt.Pick.Unverifiable -> _ui.update { it.copy(cobalt = CobaltUi.Failed("Won't install: ${p.why}. Nothing was changed.", retry = true)) }
                is dev.tidytv.core.ops.Cobalt.Pick.Ok -> {
                    cobaltPlan = p.plan
                    val log = _ui.value.tv?.serial?.let { sn -> store.cobaltLog(sn) }
                    val there = runCatching { tvLock.withLock { dev.tidytv.core.ops.Parse.packages(s.run(dev.tidytv.core.ops.Commands.isInstalled(dev.tidytv.core.ops.Cobalt.PACKAGE))).contains(dev.tidytv.core.ops.Cobalt.PACKAGE) } }.getOrDefault(false)
                    val rm = there && log != null && dev.tidytv.core.ops.Cobalt.removable(log, _ui.value.tv?.serial ?: "")
                    val note = if (!there) null else if (rm) "Installed earlier by 0TV" else "Already on this TV (not installed by 0TV, so 0TV will update it but never remove it)"
                    _ui.update { it.copy(cobalt = CobaltUi.Ready(p.plan.tag, p.plan.size, if (p.plan.is64Bit) "64-bit ARM build for this TV" else "32-bit ARM build for this TV", pre.bridgeNote, note, rm)) }
                }
            }
        }
    }

    /** Downloads from the project's own release page, verifies size and SHA-256, then installs next to stock YouTube. */
    fun installCobalt(allowSizeOnly: Boolean = false) {
        val plan = cobaltPlan ?: return
        if (cobaltJob?.isActive == true && (_ui.value.cobalt is CobaltUi.Downloading || _ui.value.cobalt is CobaltUi.Sending)) return
        val serial = _ui.value.tv?.serial ?: return
        cobaltJob = viewModelScope.launch {
            val file = java.io.File(store.cobaltDir, plan.assetName)
            try {
                val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (file.length() != plan.size || !dev.tidytv.core.ops.Cobalt.verify(plan) { file.inputStream() }) {
                        file.delete()
                        val c = http(plan.url); c.instanceFollowRedirects = true
                        if (c.responseCode != 200) return@withContext false
                        var done = 0L
                        file.outputStream().use { out -> c.inputStream.use { ins ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (done % (1 shl 20) < buf.size) _ui.update { it.copy(cobalt = CobaltUi.Downloading(done, plan.size)) }
                            }
                        } }
                        dev.tidytv.core.ops.Cobalt.verify(plan) { file.inputStream() }
                    } else true
                }
                if (!ok) { file.delete(); _ui.update { it.copy(cobalt = CobaltUi.Failed("The download didn't match the checksum GitHub publishes for it, so it was thrown away and nothing was sent to the TV.")) }; return@launch }
                _ui.update { it.copy(cobalt = CobaltUi.Sending(0, plan.size)) }
                val s = shell ?: (if (reconnect()) shell else null) ?: run { _ui.update { it.copy(cobalt = CobaltUi.Failed("The TV isn't connected.")) }; return@launch }
                val log = store.cobaltLog(serial)
                val identity = apkIdentity?.invoke(file)
                if (identity == null) { file.delete(); _ui.update { it.copy(cobalt = CobaltUi.Failed("Couldn't read the app's identity from the downloaded file, so it was not installed.")) }; return@launch }
                val progress = object : TvShell by s {
                    override suspend fun pushStream(source: java.io.InputStream, size: Long, remotePath: String, onProgress: (Long) -> Unit) =
                        s.pushStream(source, size, remotePath) { n -> _ui.update { it.copy(cobalt = CobaltUi.Sending(n, size)) }; onProgress(n) }
                }
                val out = tvLock.withLock { dev.tidytv.core.ops.Cobalt.install(progress, plan, identity, { file.inputStream() }, log, serial, allowSizeOnly) }
                trace.line("cobalt install: ${out::class.simpleName}")
                _ui.update { it.copy(cobalt = when (out) {
                    is dev.tidytv.core.ops.Cobalt.Outcome.Installed -> CobaltUi.Installed(out.pkg, plan.tag, out.launched, out.integrity.text, out.fresh, !out.fresh)
                    is dev.tidytv.core.ops.Cobalt.Outcome.IdentityMismatch -> CobaltUi.Failed("Won't install: the downloaded file is not the TizenTube Cobalt app (${out.detail}). Nothing was sent to the TV.", retry = false)
                    is dev.tidytv.core.ops.Cobalt.Outcome.NeedsWeakCheckConsent -> CobaltUi.WeakCheck
                    is dev.tidytv.core.ops.Cobalt.Outcome.NoSpace -> CobaltUi.Failed("Not enough space on the TV (${out.freeMb ?: "?"} MB free). Free up space first.")
                    is dev.tidytv.core.ops.Cobalt.Outcome.TransferMismatch -> CobaltUi.Failed("The file didn't arrive intact (${out.detail}). Nothing was installed.")
                    is dev.tidytv.core.ops.Cobalt.Outcome.Refused -> CobaltUi.Failed(out.reason + if (out.needsTvApproval) " Then tap Install again." else "")
                    dev.tidytv.core.ops.Cobalt.Outcome.Unconfirmed -> CobaltUi.Failed("Android said it installed, but 0TV couldn't confirm which app it is, so it isn't claiming success.")
                    dev.tidytv.core.ops.Cobalt.Outcome.Disconnected -> CobaltUi.Failed("The TV stopped answering. Check what is on the TV before trying again.")
                } ) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                trace.line("cobalt failed: $e")
                _ui.update { it.copy(cobalt = CobaltUi.Failed("Something went wrong: ${e.message ?: e::class.simpleName}")) }
            }
        }
    }

    /** Removes Cobalt, only if this feature installed it. Stock YouTube is never touched. */
    fun removeCobalt() {
        val st = _ui.value.cobalt as? CobaltUi.Installed ?: return
        val serial = _ui.value.tv?.serial ?: return
        viewModelScope.launch {
            val s = shell ?: return@launch
            val r = runCatching { tvLock.withLock { dev.tidytv.core.ops.Cobalt.uninstall(s, store.cobaltLog(serial), serial) } }.getOrDefault(dev.tidytv.core.ops.Cobalt.Removal.FAILED)
            _ui.update { it.copy(cobalt = when (r) {
                dev.tidytv.core.ops.Cobalt.Removal.REMOVED -> CobaltUi.Idle
                dev.tidytv.core.ops.Cobalt.Removal.NOT_OURS -> CobaltUi.Failed("0TV only removes a copy it installed onto a TV that had none. Remove this one on the TV under Settings > Apps.", retry = false)
                dev.tidytv.core.ops.Cobalt.Removal.FAILED -> CobaltUi.Failed("Couldn't remove it. You can uninstall it on the TV under Settings > Apps.")
            }) }
            if (r == dev.tidytv.core.ops.Cobalt.Removal.REMOVED) enterCobalt()
        }
    }

    // ---------- Optional J10TV ----------

    private var j10Plan: dev.tidytv.core.ops.J10Tv.Plan? = null
    private var j10Job: kotlinx.coroutines.Job? = null

    /** Looks at the TV and at the project's latest release. Changes nothing. */
    fun refreshJ10Tv() = enterJ10Tv()

    private fun enterJ10Tv() {
        if (_ui.value.j10 is J10Ui.Downloading || _ui.value.j10 is J10Ui.Sending) return
        j10Job?.cancel()
        j10Job = viewModelScope.launch {
            _ui.update { it.copy(j10 = J10Ui.Checking("Checking your TV and the latest release")) }
            val s = shell ?: return@launch
            val pre = try { tvLock.withLock { dev.tidytv.core.ops.J10Tv.preflight(s) } } catch (e: TvConnectionLost) {
                _ui.update { it.copy(j10 = J10Ui.Failed("The TV stopped answering. Check it is on, then try again.")) }; return@launch
            }
            val body = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { http(dev.tidytv.core.ops.J10Tv.RELEASE_API).run { if (responseCode == 200) inputStream.bufferedReader().readText() else null } }.getOrNull()
            }
            val rel = body?.let(dev.tidytv.core.ops.J10Tv::parseRelease)
            if (rel == null) { _ui.update { it.copy(j10 = J10Ui.Failed("Couldn't read the J10TV releases from GitHub. Check the phone's internet and try again.")) }; return@launch }
            when (val p = dev.tidytv.core.ops.J10Tv.pick(rel, pre.abis)) {
                is dev.tidytv.core.ops.J10Tv.Pick.UnsupportedAbi -> _ui.update { it.copy(j10 = J10Ui.Failed("This TV reports ${p.abis.joinToString().ifEmpty { "no known processor type" }}. J10TV only ships ARM builds, so it can't run here.", retry = false)) }
                is dev.tidytv.core.ops.J10Tv.Pick.Unverifiable -> _ui.update { it.copy(j10 = J10Ui.Failed("Won't install: ${p.why}. Nothing was changed.", retry = true)) }
                is dev.tidytv.core.ops.J10Tv.Pick.Ok -> {
                    j10Plan = p.plan
                    val log = _ui.value.tv?.serial?.let { sn -> store.j10Log(sn) }
                    val there = runCatching { tvLock.withLock { dev.tidytv.core.ops.Parse.packages(s.run(dev.tidytv.core.ops.Commands.isInstalled(dev.tidytv.core.ops.J10Tv.PACKAGE))).contains(dev.tidytv.core.ops.J10Tv.PACKAGE) } }.getOrDefault(false)
                    val rm = there && log != null && dev.tidytv.core.ops.J10Tv.removable(log, _ui.value.tv?.serial ?: "")
                    val note = if (!there) null else if (rm) "Installed earlier by 0TV" else "Already on this TV (not installed by 0TV, so 0TV will update it but never remove it)"
                    _ui.update { it.copy(j10 = J10Ui.Ready(p.plan.tag, p.plan.size, if (pre.abis.any { a -> a == "arm64-v8a" }) "One build for this 64-bit ARM TV" else "One build for this 32-bit ARM TV", note, rm)) }
                }
            }
        }
    }

    /** Downloads from the project's own release page, verifies size and SHA-256, then installs as its own app. */
    fun installJ10Tv(allowSizeOnly: Boolean = false) {
        val plan = j10Plan ?: return
        if (j10Job?.isActive == true && (_ui.value.j10 is J10Ui.Downloading || _ui.value.j10 is J10Ui.Sending)) return
        val serial = _ui.value.tv?.serial ?: return
        j10Job = viewModelScope.launch {
            val file = java.io.File(store.j10Dir, plan.assetName)
            try {
                val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (file.length() != plan.size || !dev.tidytv.core.ops.J10Tv.verify(plan) { file.inputStream() }) {
                        file.delete()
                        val c = http(plan.url); c.instanceFollowRedirects = true
                        if (c.responseCode != 200) return@withContext false
                        var done = 0L
                        file.outputStream().use { out -> c.inputStream.use { ins ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (done % (1 shl 20) < buf.size) _ui.update { it.copy(j10 = J10Ui.Downloading(done, plan.size)) }
                            }
                        } }
                        dev.tidytv.core.ops.J10Tv.verify(plan) { file.inputStream() }
                    } else true
                }
                if (!ok) { file.delete(); _ui.update { it.copy(j10 = J10Ui.Failed("The download didn't match the checksum GitHub publishes for it, so it was thrown away and nothing was sent to the TV.")) }; return@launch }
                _ui.update { it.copy(j10 = J10Ui.Sending(0, plan.size)) }
                val s = shell ?: (if (reconnect()) shell else null) ?: run { _ui.update { it.copy(j10 = J10Ui.Failed("The TV isn't connected.")) }; return@launch }
                val log = store.j10Log(serial)
                val identity = apkIdentity?.invoke(file)?.let { dev.tidytv.core.ops.J10Tv.ApkIdentity(it.pkg, it.versionName, it.signerSha256) }
                if (identity == null) { file.delete(); _ui.update { it.copy(j10 = J10Ui.Failed("Couldn't read the app's identity from the downloaded file, so it was not installed.")) }; return@launch }
                val progress = object : TvShell by s {
                    override suspend fun pushStream(source: java.io.InputStream, size: Long, remotePath: String, onProgress: (Long) -> Unit) =
                        s.pushStream(source, size, remotePath) { n -> _ui.update { it.copy(j10 = J10Ui.Sending(n, size)) }; onProgress(n) }
                }
                val out = tvLock.withLock { dev.tidytv.core.ops.J10Tv.install(progress, plan, identity, { file.inputStream() }, log, serial, allowSizeOnly) }
                trace.line("j10tv install: ${out::class.simpleName}")
                _ui.update { it.copy(j10 = when (out) {
                    is dev.tidytv.core.ops.J10Tv.Outcome.Installed -> J10Ui.Installed(out.pkg, plan.tag, out.launched, out.integrity.text, out.fresh, !out.fresh)
                    is dev.tidytv.core.ops.J10Tv.Outcome.IdentityMismatch -> J10Ui.Failed("Won't install: the downloaded file is not the J10TV app (${out.detail}). Nothing was sent to the TV.", retry = false)
                    is dev.tidytv.core.ops.J10Tv.Outcome.NeedsWeakCheckConsent -> J10Ui.WeakCheck
                    is dev.tidytv.core.ops.J10Tv.Outcome.NoSpace -> J10Ui.Failed("Not enough space on the TV (${out.freeMb ?: "?"} MB free). Free up space first.")
                    is dev.tidytv.core.ops.J10Tv.Outcome.TransferMismatch -> J10Ui.Failed("The file didn't arrive intact (${out.detail}). Nothing was installed.")
                    is dev.tidytv.core.ops.J10Tv.Outcome.Refused -> J10Ui.Failed(out.reason + if (out.needsTvApproval) " Then tap Install again." else "")
                    dev.tidytv.core.ops.J10Tv.Outcome.Unconfirmed -> J10Ui.Failed("Android said it installed, but 0TV couldn't confirm which app it is, so it isn't claiming success.")
                    dev.tidytv.core.ops.J10Tv.Outcome.Disconnected -> J10Ui.Failed("The TV stopped answering. Check what is on the TV before trying again.")
                } ) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                trace.line("j10tv failed: $e")
                _ui.update { it.copy(j10 = J10Ui.Failed("Something went wrong: ${e.message ?: e::class.simpleName}")) }
            }
        }
    }

    /** Removes J10TV, only if this feature installed it. Nothing else on the TV is touched. */
    fun removeJ10Tv() {
        val st = _ui.value.j10 as? J10Ui.Installed ?: return
        val serial = _ui.value.tv?.serial ?: return
        viewModelScope.launch {
            val s = shell ?: return@launch
            val r = runCatching { tvLock.withLock { dev.tidytv.core.ops.J10Tv.uninstall(s, store.j10Log(serial), serial) } }.getOrDefault(dev.tidytv.core.ops.J10Tv.Removal.FAILED)
            _ui.update { it.copy(j10 = when (r) {
                dev.tidytv.core.ops.J10Tv.Removal.REMOVED -> J10Ui.Idle
                dev.tidytv.core.ops.J10Tv.Removal.NOT_OURS -> J10Ui.Failed("0TV only removes a copy it installed onto a TV that had none. Remove this one on the TV under Settings > Apps.", retry = false)
                dev.tidytv.core.ops.J10Tv.Removal.FAILED -> J10Ui.Failed("Couldn't remove it. You can uninstall it on the TV under Settings > Apps.")
            }) }
            if (r == dev.tidytv.core.ops.J10Tv.Removal.REMOVED) enterJ10Tv()
        }
    }

    // ---------- Review ----------

    private fun prepareReview(snap: TvSnapshot) {
        viewModelScope.launch {
            _ui.update { it.copy(review = ReviewState(backup = BackupStatus.Running)) }
            val s = shell ?: return@launch
            try {
                val backup = tvLock.withLock { TvReader.backup(s, snap, System.currentTimeMillis()) }
                store.saveBackup(backup)
                trace.line("backup saved: ${backup.packages.size} packages, ${backup.disabled.size} already off")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                trace.line("backup failed: $e")
                _ui.update { it.copy(review = it.review.copy(backup = BackupStatus.Failed(
                    if (e is TvConnectionLost) "The TV stopped answering while saving the backup." else "The backup couldn't be saved.",
                ))) }
                return@launch
            }
            val match = ModelMatcher.match(snap.info, lists().lists, installed = snap.installed).firstOrNull()
            list = match?.list
            if (match == null) {
                trace.line("no list for ${snap.info.displayName}")
                // No list for this TV: offer what other TVs' lists turn off (all unticked, protections kept) and let the
                // owner tell us the model so a list can be added.
                val info = snap.info
                val details = "My TV isn't on a list yet.\nModel: ${info.model}\nBrand: ${info.brand}\nDevice: ${info.device}\nAndroid: ${info.release} (SDK ${info.sdk})\n"
                val more = Grouping.moreCandidates(lists().lists, snap.installed - snap.disabled, snap.liveProtected, emptySet(), emptySet())
                trace.line("no list: ${more.size} apps from other lists offered")
                fetchIcons(more.map { c -> c.pkg })
                _ui.update { it.copy(labels = it.labels + more.associate { c -> c.pkg to c.label },
                    review = it.review.copy(backup = BackupStatus.Saved, noList = true, extra = more, tvDetails = details)) }
                return@launch
            }
            trace.line("list ${match.list.id} score ${match.score} overlap ${match.overlap}")
            val candidates = Grouping.candidates(match.list, snap.installed - snap.disabled, snap.liveProtected)
            val kept = (match.list.entries + match.list.optional).asSequence()
                .filter { it.pkg in snap.installed }
                .filter { Protected.isProtected(it.pkg, snap.liveProtected, match.list.keep) || Launchers.isHomeApp(it.pkg) }
                .map { AppLabels.label(it.pkg, it.note) }.distinct().sorted().toList()
            val more = if (candidates.size >= ReviewState.FEW) emptyList() else Grouping.moreCandidates(
                lists().lists, snap.installed - snap.disabled, snap.liveProtected, match.list.keep,
                offered = (match.list.entries + match.list.optional).map { e -> e.pkg }.toSet(),
            )
            if (more.isNotEmpty()) trace.line("only ${candidates.size} on the list; ${more.size} more from other lists")
            fetchIcons(candidates.map { c -> c.pkg })
            _ui.update {
                it.copy(labels = it.labels + candidates.associate { c -> c.pkg to c.label }, review = ReviewState(
                    backup = BackupStatus.Saved, listTitle = match.list.title, yourTv = it.tv?.model?.takeIf { m -> m.isNotBlank() }, exact = match.exact,
                    candidates = candidates, selected = candidates.filter { c -> c.selected }.map { c -> c.pkg }.toSet(), kept = kept,
                    moreAvailable = more,
                ))
            }
        }
    }

    fun retryBackup() { snapshot?.let { prepareReview(it) } }

    private var iconJob: Job? = null

    /**
     * Reads each app's real icon from its APK on the TV, small APKs first-come, while the user reviews.
     * Runs between other TV work (one pull at a time under [tvLock]) and is simply skipped where it fails.
     */
    private fun fetchIcons(pkgs: List<String>) {
        val toIcon = iconFromApk ?: return
        iconJob?.cancel()
        iconJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (pkg in pkgs.distinct()) {
                if (pkg in _ui.value.icons) continue
                store.cachedIcon(pkg)?.let { path -> _ui.update { it.copy(icons = it.icons + (pkg to path)) }; continue }
                val s = shell ?: return@launch
                val apk = tvLock.withLock { ApkFetcher(s).fetch(pkg) }
                if (apk == null) { trace.line("icon $pkg: no small APK on the TV"); continue }
                // The app's own name, when TidyTV has no better one: "TGuard" beats "Tcl guard".
                if (!dev.tidytv.core.lists.AppLabels.known(pkg)) labelFromApk?.let { read ->
                    runCatching { read(pkg, apk) }.getOrNull()?.takeIf { n -> n.isNotBlank() && !n.contains('.') && n.length <= 40 }
                        ?.let { name -> _ui.update { it.copy(labels = it.labels + (pkg to name)) } }
                }
                val path = runCatching { toIcon(pkg, apk) }.onFailure { trace.line("icon $pkg: ${it.javaClass.simpleName}") }.getOrNull()
                if (path == null) { trace.line("icon $pkg: no icon in the APK (${apk.size} bytes)"); continue }
                _ui.update { it.copy(icons = it.icons + (pkg to path)) }
            }
            trace.line("icons: ${_ui.value.icons.size} read from the TV")
        }
    }

    fun toggleApp(pkg: String) = _ui.update {
        val sel = it.review.selected
        it.copy(review = it.review.copy(selected = if (pkg in sel) sel - pkg else sel + pkg))
    }

    /** "Show more apps": the other lists' apps join the review, every one unselected. */
    fun showMoreApps() {
        val more = _ui.value.review.moreAvailable
        if (more.isEmpty()) return
        trace.line("show more apps: ${more.size}")
        _ui.update { it.copy(labels = it.labels + more.associate { c -> c.pkg to c.label }, review = it.review.copy(extra = more)) }
        fetchIcons(more.map { it.pkg })
    }

    fun toggleGroup(group: Group, on: Boolean) = _ui.update {
        val pkgs = it.review.candidates.filter { c -> c.group == group }.map { c -> c.pkg }.toSet()
        it.copy(review = it.review.copy(selected = if (on) it.review.selected + pkgs else it.review.selected - pkgs))
    }

    // ---------- Tidying ----------

    fun startTidy() = startTidy(confirmed = false)

    /** "Uninstall for good" on the confirmation for apps the owner installed. */
    fun confirmPermanent() = startTidy(confirmed = true)

    fun cancelPermanent() = _ui.update { it.copy(review = it.review.copy(confirmPermanent = emptyList())) }

    private fun startTidy(confirmed: Boolean) {
        // A second tap before the screen changes must not start a second clean-up.
        if (_ui.value.wizard.step != Step.REVIEW || _ui.value.wizard.busy) return
        val r = _ui.value.review
        if (r.backup != BackupStatus.Saved || r.selected.isEmpty()) return
        val rows = r.all?.rows.orEmpty()
        val yours = rows.filter { it.tier == dev.tidytv.core.lists.Tier.YOURS && it.pkg in r.selected }.map { it.pkg }
        if (yours.isNotEmpty() && !confirmed) {
            _ui.update { it.copy(review = it.review.copy(confirmPermanent = yours)) }
            return
        }
        iconJob?.cancel() // the clean-up comes first; missing icons keep their fallback
        val fromAll = rows.filterNot { it.locked }.map { Candidate(it.pkg, it.label, it.note, it.group, it.selected, optional = !it.selected) }
        val order = (r.candidates + r.extra + fromAll).distinctBy { it.pkg }.filter { it.pkg in r.selected }
        // Apps the owner installed can only be uninstalled; the rest follow the chosen method.
        val remove = (if (r.uninstall) r.selected else emptySet()) + yours
        // Apps this TV's list keeps on, which the owner picked in All apps ("Your call").
        val allowKept = rows.filter { it.keptByList && it.pkg in r.selected }.map { it.pkg }.toSet()
        trace.line("tidy: ${order.size} apps, uninstall ${remove.size}, kept-by-list picked ${allowKept.size}")
        _ui.update { it.copy(tidy = TidyState(order = order), done = it.done.copy(homeUntested = false), review = it.review.copy(confirmPermanent = emptyList())) }
        wizard(WizardEvent.StartTidy)
        viewModelScope.launch { runTidy(order.map { it.pkg }, remove, allowKept) }
    }

    // ---------- All apps ----------

    /** Every app on the TV in tiers, with what each costs (memory, boot, storage). Read-only. */
    fun openAllApps() {
        val snap = snapshot ?: return
        if (_ui.value.review.all != null) return
        _ui.update { it.copy(review = it.review.copy(all = AllAppsState())) }
        viewModelScope.launch {
            val facts = withShell { s -> dev.tidytv.core.ops.TvFacts.read(s) }
            val yours = facts?.apps?.filterValues { f -> f.yours }?.keys.orEmpty()
            val rows = dev.tidytv.core.lists.AllApps.build(list, lists().lists, snap.installed, snap.disabled, yours, snap.liveProtected)
            trace.line("all apps: ${rows.size}, ${rows.groupingBy { r -> r.tier }.eachCount()}")
            _ui.update {
                val names = rows.associate { r -> r.pkg to r.label } + it.labels
                it.copy(labels = names, review = it.review.copy(all = AllAppsState(false, rows, facts?.apps.orEmpty())))
            }
            fetchIcons(rows.filterNot { r -> r.locked }.map { r -> r.pkg })
        }
    }

    fun closeAllApps() = _ui.update { it.copy(review = it.review.copy(all = null)) }

    fun setUninstall(on: Boolean) = _ui.update { it.copy(review = it.review.copy(uninstall = on)) }

    // ---------- Apps tile ----------

    private fun loadApps(message: RestoreUi.Finished? = null) {
        val snap = snapshot ?: return
        _ui.update { it.copy(apps = AppsState(message = message)) }
        viewModelScope.launch {
            val fresh = withShell { s -> TvReader.snapshot(s) } ?: snap
            snapshot = fresh
            val facts = withShell { s -> dev.tidytv.core.ops.TvFacts.read(s) }
            val yours = facts?.apps?.filterValues { f -> f.yours }?.keys.orEmpty()
            val rows = dev.tidytv.core.lists.AllApps.build(list, lists().lists, fresh.installed, fresh.disabled, yours, fresh.liveProtected)
            _ui.update {
                val names = rows.associate { r -> r.pkg to r.label } + it.labels
                it.copy(labels = names, apps = AppsState(false, rows, facts?.apps.orEmpty(), message = message))
            }
            fetchIcons(rows.filterNot { r -> r.locked }.map { r -> r.pkg })
        }
    }

    fun openApp(pkg: String) = _ui.update { it.copy(apps = it.apps.copy(open = pkg, message = null)) }
    fun closeApp() = _ui.update { it.copy(apps = it.apps.copy(open = null)) }

    /**
     * Turns one app off, or uninstalls it for the TV's user, right away: a fresh backup first, the same
     * checks as the clean-up, and a log entry so Restore and Check my TV know about it.
     */
    fun changeApp(pkg: String, uninstall: Boolean) {
        val snap = snapshot ?: return
        val lg = log ?: return
        val row = _ui.value.apps.rows.firstOrNull { it.pkg == pkg } ?: return
        if (row.locked || _ui.value.apps.busy != null) return
        val remove = uninstall || row.tier == dev.tidytv.core.lists.Tier.YOURS
        val allowKept = if (row.keptByList) setOf(pkg) else emptySet()
        _ui.update { it.copy(apps = it.apps.copy(open = null, busy = pkg, message = null)) }
        viewModelScope.launch {
            val outcome = withShell { s ->
                store.saveBackup(TvReader.backup(s, snap, System.currentTimeMillis()))
                Debloater(s, lg, sleep = { _ -> delay(backend?.saveWaitMs ?: 0) })
                    .run(listOf(pkg), snap.liveProtected, list?.keep.orEmpty() - allowKept, remove = if (remove) setOf(pkg) else emptySet())
            }
            trace.line("app $pkg uninstall=$remove: $outcome")
            val name = _ui.value.labels[pkg] ?: row.label
            val ok = (outcome as? dev.tidytv.core.ops.DebloatOutcome.Finished)?.results?.firstOrNull()?.isOff == true
            val msg = when {
                outcome == null -> RestoreUi.Finished("The TV stopped answering. Check it's on and on the same network, then try again.", ok = false)
                ok && remove && row.tier == dev.tidytv.core.lists.Tier.YOURS -> RestoreUi.Finished("$name is uninstalled.", ok = true)
                ok && remove -> RestoreUi.Finished("$name is uninstalled for this TV. Restore brings it back.", ok = true)
                ok -> RestoreUi.Finished("$name is off. Turn it back on from Restore or the clean-up summary.", ok = true)
                else -> RestoreUi.Finished("$name wouldn't turn off, so it was left as it was.", ok = false)
            }
            loadApps(msg)
        }
    }

    // ---------- Block ads ----------

    private fun enterAds() {
        val sdk = _ui.value.tv?.sdk ?: 0
        if (sdk in 1 until dev.tidytv.core.ops.PrivateDns.MIN_SDK) {
            _ui.update { it.copy(ads = AdsState(loading = false, supported = false)) }
            return
        }
        _ui.update { it.copy(ads = AdsState()) }
        viewModelScope.launch {
            val lg = log
            var last: String? = null
            val dns = if (lg == null) null else withShell { s -> dev.tidytv.core.ops.PrivateDns(s, lg).also { last = it.lastCustomHost() }.read() }
            trace.line("ads: read $dns")
            _ui.update { it.copy(ads = AdsState(loading = false, dns = dns, lastCustom = last,
                message = if (dns == null) RestoreUi.Finished("The TV stopped answering. Check it's on, then try again.", ok = false) else null)) }
        }
    }

    /** Reads the TV's Private DNS again, so the screen shows what the TV is doing now. */
    fun refreshAds() { if (!_ui.value.ads.busy) enterAds() }

    /** Turns on ad blocking. If a different server is already set, the first tap only asks (see [AdsState.confirmReplace]). */
    fun blockAds(provider: dev.tidytv.core.ops.AdBlockProvider, confirmed: Boolean = false) {
        val a = _ui.value.ads
        if (a.busy) return
        if (a.dns?.pinned == true && a.dns.provider != provider && !confirmed && !weSetIt(a.dns)) {
            _ui.update { it.copy(ads = it.ads.copy(confirmReplace = provider, confirmReplaceHost = null)) }
            return
        }
        changeAds { dns -> dns.turnOn(provider) to provider.host }
    }

    /** Uses the owner's own DNS-over-TLS server. Returns the reason when the name is refused, null when it went ahead. */
    fun useCustomDns(input: String, confirmed: Boolean = false): String? {
        val a = _ui.value.ads
        if (a.busy) return null
        val host = when (val r = dev.tidytv.core.ops.DotHost.check(input)) {
            is dev.tidytv.core.ops.DotHost.Result.Bad -> return r.reason
            is dev.tidytv.core.ops.DotHost.Result.Ok -> r.host
        }
        if (a.dns?.pinned == true && !a.dns.host.equals(host, true) && !confirmed && !weSetIt(a.dns)) {
            _ui.update { it.copy(ads = it.ads.copy(confirmReplaceHost = host, confirmReplace = null)) }
            return null
        }
        changeAds { dns -> dns.useCustom(host) to host }
        return null
    }

    /** True when this app set the server now in use, so changing it needs no "replace" question. */
    private fun weSetIt(dns: dev.tidytv.core.ops.DnsState): Boolean {
        val lg = log ?: return false
        return dev.tidytv.core.ops.PrivateDns.hostsSetIn(lg.records()).any { it.equals(dns.host, true) }
    }

    fun cancelReplaceDns() = _ui.update { it.copy(ads = it.ads.copy(confirmReplace = null, confirmReplaceHost = null)) }

    fun unblockAds() = changeAds { dns -> dns.turnOff() to null }

    private fun changeAds(block: suspend (dev.tidytv.core.ops.PrivateDns) -> Pair<dev.tidytv.core.ops.DnsState, String?>) {
        val lg = log ?: return
        _ui.update { it.copy(ads = it.ads.copy(busy = true, confirmReplace = null, confirmReplaceHost = null, message = null)) }
        viewModelScope.launch {
            var wrongTv = false
            var lookupFailed = false
            val r = withShell { s ->
                val pd = dev.tidytv.core.ops.PrivateDns(s, lg)
                try { block(pd).also { lookupFailed = pd.lookupFailed } } catch (e: dev.tidytv.core.ops.WrongTvException) { wrongTv = true; null }
            }
            trace.line("ads: $r")
            val (state, wanted) = r ?: (null to null)
            val msg = when {
                wrongTv -> RestoreUi.Finished("This record belongs to a different TV, or this TV can't be identified, so nothing was changed.", ok = false)
                state == null -> RestoreUi.Finished("The TV stopped answering. Check it's on, then try again.", ok = false)
                wanted != null && lookupFailed -> RestoreUi.Finished("The TV took the setting but couldn't look up names through $wanted, so it was put back. Check the server name, and that your network allows DNS-over-TLS (port 853).", ok = false)
                wanted != null && !(state.mode == "hostname" && state.host.equals(wanted, true)) -> RestoreUi.Finished("This TV didn't accept the change, so it was left as it was.", ok = false)
                wanted != null && state.provider != null -> RestoreUi.Finished("AdGuard DNS is set on the TV (${state.host}) and names resolve through it.", ok = true)
                wanted != null -> RestoreUi.Finished("Your DNS-over-TLS server is set on the TV (${state.host}) and names resolve through it. Whether it blocks ads depends on that server.", ok = true)
                state.pinned -> RestoreUi.Finished("Your TV's DNS is back as it was before: ${state.host}.", ok = true)
                else -> RestoreUi.Finished("Ad blocking is off. The TV now reports: ${state.headline()}", ok = true)
            }
            _ui.update { it.copy(ads = it.ads.copy(busy = false, dns = state ?: it.ads.dns, lastCustom = if (wanted != null && state?.custom == true) wanted else it.ads.lastCustom, message = msg)) }
        }
    }

    // ---------- Free up space ----------

    private fun enterSpace() {
        _ui.update { it.copy(space = SpaceState()) }
        viewModelScope.launch { readSpace(message = null) }
    }

    private suspend fun readSpace(message: RestoreUi.Finished?, cleared: Boolean = false) {
        val facts = withShell { s -> dev.tidytv.core.ops.TvFacts.read(s) }
        // Android's per-app cache counts only refresh about once a day, so right after a clear they still show the
        // old numbers: show zero then, and mark them as estimates otherwise.
        val top = if (cleared) emptyList() else facts?.apps.orEmpty().filterValues { f -> f.cacheMb > 0 }.entries
            .sortedByDescending { e -> e.value.cacheMb }.take(6).map { e -> e.key to e.value.cacheMb }
        fetchIcons(top.map { p -> p.first })
        val storage = facts?.storage?.let { st -> if (cleared) st.copy(cacheMb = 0) else st }
        _ui.update { it.copy(space = SpaceState(loading = false, storage = storage, topCache = top, estimated = !cleared, message = message ?: if (facts == null)
            RestoreUi.Finished("The TV stopped answering. Check it's on, then try again.", ok = false) else null)) }
    }

    /** Runs each command under the TV lock on its own, so a long clear doesn't block the remote or anything else. */
    private inner class LockedShell(private val s: TvShell) : TvShell by s {
        override suspend fun run(command: String): String = tvLock.withLock { s.run(command) }
    }

    /**
     * Clears every app's cache on the TV (`pm trim-caches`): always safe, apps rebuild them. It runs in the
     * background on the TV; this shows what's been freed so far from the TV's live free space.
     */
    fun clearCaches() {
        if (_ui.value.space.clearing) return
        _ui.update { it.copy(space = it.space.copy(clearing = true, progressMb = 0, message = null)) }
        val freeAtStart = _ui.value.space.storage?.freeMb
        viewModelScope.launch {
            val s = shell ?: if (reconnect()) shell else null
            val r = s?.let {
                try {
                    dev.tidytv.core.ops.TvFacts.freeCaches(LockedShell(it), onProgress = { mb ->
                        _ui.update { u -> u.copy(space = u.space.copy(progressMb = mb,
                            storage = u.space.storage?.let { st -> if (freeAtStart != null) st.copy(freeMb = freeAtStart + mb) else st })) }
                    })
                } catch (e: TvConnectionLost) { null }
            }
            trace.line("free up space: $r")
            val msg = when {
                r == null -> RestoreUi.Finished("The TV stopped answering. Android may still be clearing in the background; check again in a minute.", ok = false)
                !r.finished -> RestoreUi.Finished("Still clearing in the background. Freed ${r.freedMb} MB so far.", ok = true)
                r.freedMb > 0 -> RestoreUi.Finished("Freed ${mbText(r.freedMb)} on your TV.", ok = true)
                else -> RestoreUi.Finished("Caches are already clear. Nothing more to free right now.", ok = true)
            }
            readSpace(msg, cleared = r?.finished == true)
        }
    }

    private fun mbText(mb: Int) = if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"

    private suspend fun runTidy(pkgs: List<String>, remove: Set<String> = emptySet(), allowKept: Set<String> = emptySet()) {
        val snap = snapshot ?: return
        val lg = log ?: return
        val b = backend ?: return
        while (true) {
            val s = shell ?: if (reconnect()) shell!! else null
            if (s == null) {
                _ui.update { it.copy(tidy = it.tidy.copy(reconnecting = false, stopReason = "0TV lost the connection to your TV and couldn't get it back. Everything done so far is saved, and Restore puts it back.")) }
                wizard(WizardEvent.TidyEnded(stopped = true, launcherPossible = false))
                return
            }
            _ui.update { it.copy(tidy = it.tidy.copy(reconnecting = false)) }
            val debloater = Debloater(s, lg, sleep = { _ ->
                _ui.update { it.copy(tidy = it.tidy.copy(saving = true)) }
                delay(b.saveWaitMs)
            })
            val outcome = tvLock.withLock {
                debloater.run(pkgs, snap.liveProtected, list?.keep.orEmpty() - allowKept, onResult = { r -> record(r) }, remove = remove)
            }
            trace.line("clean-up: ${outcome::class.simpleName}, ${outcome.disabledCount} off")
            when (outcome) {
                is DebloatOutcome.Finished -> {
                    val possible = launcherPossible()
                    val untested = !possible && _ui.value.wizard.kind != TvKind.FIRE_TV && !homeTested()
                    if (untested) trace.line("home ${snapshot?.home} untested: no home screen offered")
                    _ui.update { it.copy(tidy = it.tidy.copy(saving = false, finished = true), done = it.done.copy(homeUntested = untested)) }
                    wizard(WizardEvent.TidyEnded(stopped = false, launcherPossible = possible))
                    return
                }
                is DebloatOutcome.Stopped -> {
                    _ui.update { it.copy(tidy = it.tidy.copy(saving = false, stopReason = outcome.reason)) }
                    wizard(WizardEvent.TidyEnded(stopped = true, launcherPossible = false))
                    return
                }
                is DebloatOutcome.Disconnected -> {
                    // Resume: the Debloater re-reads the TV, so finished packages come back as AlreadyOff.
                    _ui.update { it.copy(tidy = it.tidy.copy(reconnecting = true)) }
                    shell = null
                }
            }
        }
    }

    /** A package that was turned off before a reconnect stays "turned off", not "was already off". */
    private fun record(r: PkgResult) = _ui.update {
        val prev = it.tidy.results[r.pkg]
        val keep = prev != null && prev.isOff && r is PkgResult.AlreadyOff
        it.copy(tidy = it.tidy.copy(results = if (keep) it.tidy.results else it.tidy.results + (r.pkg to r)))
    }

    private fun launcherPossible(): Boolean {
        val snap = snapshot ?: return false
        if (launcherApk(HomeApp.SIMPLE) == null) return false
        val homePkg = snap.home?.substringBefore('/')
        return homePkg in Launchers.KNOWN_STOCK || homePkg == "android"
    }

    /** Home is a stock launcher TidyTV has tested, or already one of its own home screens. */
    private fun homeTested(): Boolean {
        val homePkg = snapshot?.home?.substringBefore('/') ?: return false
        return homePkg in Launchers.KNOWN_STOCK || homePkg == "android" || HomeApp.of(homePkg) != null
    }

    // ---------- Home screen ----------

    private fun enterHome() {
        val snap = snapshot ?: return
        val options = (snap.installed - snap.disabled)
            .filter { Grouping.groupOf(ListEntry(it, "", "")) == Group.STREAMING }
            .filter { it !in _ui.value.tidy.results.filterValues { r -> r.isOff }.keys }
            .map { DockApp(it, AppLabels.label(it)) }
            .sortedBy { it.label.lowercase() }
        val available = HomeApp.entries.filter { a -> snap.info.sdk >= a.minSdk && (a.fromPlayStore || launcherApk(a) != null) }
        _ui.update { it.copy(home = HomeState.Offer(options, options.take(8).map { o -> o.pkg }, if (HomeApp.PREMIUM in available) HomeApp.PREMIUM else HomeApp.SIMPLE, available.sortedBy { if (it == HomeApp.PREMIUM) 0 else 1 }, untested = !homeTested())) }
        fetchIcons(options.map { it.pkg })
        viewModelScope.launch {
            val home = withShell { s -> dev.tidytv.core.ops.Parse.component(s.run(dev.tidytv.core.ops.Commands.RESOLVE_HOME)) }
            if (home?.substringBefore('/') in setOf(HomeApp.STYLED.pkg, HomeApp.PREMIUM.pkg)) _ui.update { u ->
                (u.home as? HomeState.Offer)?.let { u.copy(home = it.copy(styledIsHome = true)) } ?: u
            }
        }
    }

    // ---------- Looks for the Apple TV style home screen ----------

    fun openLook() = _ui.update { it.copy(home = HomeState.Look(LookState(), back = it.home)) }

    fun closeLook() {
        val l = _ui.value.home as? HomeState.Look ?: return
        if (l.look.applying != null) return
        if (l.back != null) _ui.update { it.copy(home = l.back) } else enterHome()
    }

    fun applyLook(id: LookId) {
        val files = if (id == LookId.CLASSIC) dev.tidytv.core.ops.LookFiles("default", "{}") else looks(id.id)
        if (files == null) return setLook { it.copy(message = RestoreUi.Finished("That look isn't in this copy of 0TV.", ok = false)) }
        putLook(id.id, "${id.title} is on your TV.") { files }
    }

    /** The owner's own photo, made small on the phone first (see Wallpaper). */
    fun applyPhoto(file: PickedFile) = putLook("photo", null) {
        val img = photoWallpaper(file) ?: return@putLook null
        lastPhoto = img
        dev.tidytv.core.ops.LookFiles("photo", PHOTO_CONFIG, wallpaper = img.jpeg)
    }

    private var lastPhoto: dev.tidytv.app.data.WallpaperImage? = null

    private fun putLook(id: String, done: String?, make: suspend () -> dev.tidytv.core.ops.LookFiles?) {
        if ((_ui.value.home as? HomeState.Look)?.look?.applying != null) return
        setLook { it.copy(applying = id, message = null) }
        wizard(WizardEvent.Busy(true))
        viewModelScope.launch {
            val files = withContext(imageWork) { runCatching { make() }.getOrNull() }
            val ok = files != null && withShell { s -> run {
                val cur = HomeApp.of(dev.tidytv.core.ops.Parse.component(s.run(dev.tidytv.core.ops.Commands.RESOLVE_HOME))?.substringBefore('/'))
                dev.tidytv.core.ops.LauncherLook(s, home = if (cur == HomeApp.PREMIUM) HomeApp.PREMIUM else HomeApp.STYLED).apply(files)
            } } == true
            trace.line("look $id: ok=$ok")
            val msg = when {
                files == null -> RestoreUi.Finished("That photo couldn't be opened. Try another one.", ok = false)
                !ok -> RestoreUi.Finished("The TV didn't take the new look. Check it's on, then try again.", ok = false)
                done != null -> RestoreUi.Finished(done, ok = true)
                else -> RestoreUi.Finished(photoMessage(lastPhoto), ok = true)
            }
            setLook { it.copy(applying = null, current = if (ok) id else it.current, message = msg) }
            wizard(WizardEvent.Busy(false))
        }
    }

    private fun photoMessage(img: dev.tidytv.app.data.WallpaperImage?): String {
        if (img == null) return "Your photo is on your TV."
        fun size(b: Long) = if (b >= 1024 * 1024) "%.1f MB".format(b / 1048576.0) else "${(b + 1023) / 1024} KB"
        val was = if (img.originalBytes > 0) ", from ${size(img.originalBytes)}" else ""
        return "Your photo is on your TV. Sent at ${img.width}×${img.height}, ${size(img.jpeg.size.toLong())}$was."
    }

    private fun setLook(f: (LookState) -> LookState) = _ui.update { u ->
        (u.home as? HomeState.Look)?.let { u.copy(home = it.copy(look = f(it.look))) } ?: u
    }

    fun chooseHome(app: HomeApp) = _ui.update {
        val h = it.home as? HomeState.Offer ?: return@update it
        if (app in h.available) it.copy(home = h.copy(app = app)) else it
    }

    fun toggleDock(pkg: String) = _ui.update {
        val h = it.home as? HomeState.Offer ?: return@update it
        it.copy(home = h.copy(chosen = if (pkg in h.chosen) h.chosen - pkg else h.chosen + pkg))
    }

    fun skipHome() = wizard(WizardEvent.HomeScreenDone)

    fun installHome() {
        val offer = _ui.value.home as? HomeState.Offer
        if (offer?.untested == true) return // never change Home on a launcher TidyTV hasn't tested
        lastDock = offer?.chosen ?: lastDock
        lastApp = offer?.app ?: lastApp
        val dock = lastDock
        if (lastApp.fromPlayStore) installFromPlayStore(lastApp) else runHome { h, apk, sdk -> h.run(apk, dock, sdk) }
    }

    private var playStoreJob: Job? = null

    /**
     * Projectivy: if it isn't on the TV yet, open its Play Store page there and wait (up to 5 min)
     * for the owner to press Install with the remote; then hand Home over as usual.
     */
    private fun installFromPlayStore(app: HomeApp) {
        playStoreJob?.cancel()
        playStoreJob = viewModelScope.launch {
            val s = shell ?: return@launch
            if (!isOnTv(app.pkg)) {
                runCatching { tvLock.withLock { s.run(dev.tidytv.core.ops.Commands.playStorePage(app.pkg)) } }
                _ui.update { it.copy(home = HomeState.WaitingForPlayStore(app)) }
                trace.line("waiting for ${app.pkg} from the Play Store")
                var ok = false
                for (i in 1..100) {
                    delay(3_000)
                    if (isOnTv(app.pkg)) { ok = true; break }
                }
                if (!ok) {
                    _ui.update { it.copy(home = HomeState.Problem("Projectivy isn't installed yet. Install it from the Play Store on your TV, then try again.", worried = false, action = HomeAction.RETRY)) }
                    return@launch
                }
            }
            runHome { h, apk, sdk -> h.run(apk, emptyList(), sdk) }
        }
    }

    /** True when [pkg] is installed on the TV; false on any error, including a dropped link. */
    private suspend fun isOnTv(pkg: String): Boolean = withShell { s ->
        pkg in dev.tidytv.core.ops.Parse.packages(s.run(dev.tidytv.core.ops.Commands.isInstalled(pkg)))
    } == true

    /** "Not now" while waiting on the Play Store: back to the choice. */
    fun cancelPlayStore() {
        playStoreJob?.cancel()
        enterHome()
    }

    private var lastDock: List<String> = emptyList()

    private companion object {
        const val SNAPSHOT_PATIENCE_MS = 25_000L
        /** A photo look: the photo full screen, with the frosted dock so the apps stay readable. */
        const val PHOTO_CONFIG = "{\"wallpaper\": true, \"background_color\": \"#000000\", \"dock_glass\": true}"
    }
    private var lastApp: HomeApp = HomeApp.SIMPLE

    fun homeAction(action: HomeAction, onEthernet: Boolean = false) {
        when (action) {
            HomeAction.RETRY -> if (lastApp.fromPlayStore) installFromPlayStore(lastApp) else runHome { h, apk, sdk -> h.run(apk, lastDock, sdk) }
            // The handover now turns the setup app off itself when that's safe; this only adds "my TV is on a cable".
            HomeAction.TURN_OFF_SETUP_APP -> runHome { h, apk, sdk -> h.run(apk, lastDock, sdk, onEthernet = onEthernet) }
        }
    }

    private fun setupAppMessage(c: WraithCheck): String = when (c) {
        WraithCheck.OwnsWifi -> "Your TV's Wi-Fi was saved by Google TV's setup app, so 0TV can't turn that app off without disconnecting the TV. Your old home screen stays. To fix it, on the TV open Settings > Network, choose your Wi-Fi, select Forget, then join it again with your password. Then tap Try again. (If your TV uses a network cable instead of Wi-Fi, tick the box below.)"
        WraithCheck.SetupIncomplete -> "Your TV hasn't finished its first setup, so Google TV's setup app has to stay on. Your old home screen stays."
        WraithCheck.Persistent -> "Google TV's setup app can't be turned off on this TV, so the Home button would keep going to it. Your old home screen stays."
        is WraithCheck.Unknown -> "0TV couldn't check Google TV's setup app safely (${c.why}), so it changed nothing. Your old home screen stays."
        WraithCheck.Ok -> ""
    }

    private fun runHome(block: suspend (LauncherHandover, ByteArray, Int) -> HandoverOutcome) {
        // Double taps: one handover at a time.
        if (_ui.value.home is HomeState.Running || _ui.value.wizard.busy) return
        val app = lastApp
        val apk = if (app.fromPlayStore) ByteArray(0) else launcherApk(app) ?: return
        val lg = log ?: return
        val b = backend ?: return
        val sdk = _ui.value.tv?.sdk ?: 0
        wizard(WizardEvent.Busy(true))
        _ui.update { it.copy(home = HomeState.Running) }
        viewModelScope.launch {
            val outcome = try {
                var result: HandoverOutcome
                while (true) {
                    val s = shell ?: if (reconnect()) shell!! else null
                    if (s == null) { result = HandoverOutcome.Disconnected; break }
                    val h = LauncherHandover(s, lg, sleep = { delay(b.saveWaitMs) }, app = app)
                    result = tvLock.withLock { block(h, apk, sdk) }
                    if (result != HandoverOutcome.Disconnected) break
                    shell = null
                    if (!reconnect()) break
                }
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
            trace.line("home screen: $outcome")
            _ui.update { it.copy(home = homeState(outcome, app)) }
            wizard(WizardEvent.Busy(false))
        }
    }

    private fun homeState(o: HandoverOutcome, app: HomeApp): HomeState = when (o) {
        is HandoverOutcome.Done -> HomeState.Installed(app)
        HandoverOutcome.NotInstalled -> HomeState.Problem("Projectivy isn't installed yet. Install it from the Play Store on your TV, then try again.", worried = false, action = HomeAction.RETRY)
        is HandoverOutcome.NeedsChoice -> HomeState.Problem(
            "Your TV has another home screen app installed (${o.claimants.filterNot { it in Launchers.KNOWN_STOCK || it == Launchers.SETUPWRAITH || it == "com.android.tv.settings" }.joinToString { c -> _ui.value.labels[c] ?: dev.tidytv.core.lists.AppLabels.label(c) }.ifBlank { "more than one" }}). " +
                "Android would ask which to use every time you press Home, so 0TV changed nothing. Remove the other one first, or keep your current home screen.",
            worried = false,
        )
        is HandoverOutcome.SetupAppBlocked -> HomeState.Problem(
            setupAppMessage(o.check), worried = false,
            action = if (o.check == WraithCheck.OwnsWifi) HomeAction.TURN_OFF_SETUP_APP else null,
        )
        is HandoverOutcome.UnknownHome -> HomeState.Problem("Your TV uses a home screen 0TV doesn't know, so it left Home alone.", worried = false)
        HandoverOutcome.SignatureConflict -> HomeState.Problem(
            "A differently signed copy of this home screen is already installed on your TV. 0TV left it and your Home screen untouched. Keeping your current Home is the safe choice. Removing the other copy yourself in the TV's Settings > Apps would erase that launcher's settings, so only do that if you are sure you want to, then try again.", worried = false, action = HomeAction.RETRY,
        )
        is HandoverOutcome.InstallBlocked -> HomeState.Problem("This TV doesn't allow extra home screens. The clean-up still worked.", worried = false)
        is HandoverOutcome.InstallFailed -> HomeState.Problem("The home screen didn't install, so nothing about Home changed.", worried = false, action = HomeAction.RETRY)
        is HandoverOutcome.StartFailed -> HomeState.Problem("The new home screen didn't open, so 0TV left your old one on.", worried = false, action = HomeAction.RETRY)
        is HandoverOutcome.SetupWraithTookHome -> HomeState.Problem(
            if (o.rolledBack) "Google TV's setup app took the Home button once the old home screen was off, so 0TV put everything back. Tap try again: it turns the setup app off first this time."
            else "Google TV's setup app grabbed Home and putting your old home screen back didn't finish. Tap Restore everything.",
            worried = !o.rolledBack, action = if (o.rolledBack) HomeAction.TURN_OFF_SETUP_APP else null,
        )
        is HandoverOutcome.RolledBack -> HomeState.Problem(
            "The new home screen opened, but the Home button still went to ${o.homeNow?.substringBefore('/')?.let { _ui.value.labels[it] ?: dev.tidytv.core.lists.AppLabels.label(it) } ?: "another app"}, so 0TV put your old home screen back. Share logs if this keeps happening.",
            worried = false, action = HomeAction.RETRY,
        )
        is HandoverOutcome.RollbackFailed -> HomeState.Problem(
            "Home didn't switch over and putting the old one back didn't stick. Your TV still works; tap Restore everything, or share logs with us.",
            worried = true, rescue = o.rescue,
        )
        HandoverOutcome.Disconnected -> HomeState.Problem("The TV stopped answering. Check it's on, then try again.", worried = true, action = HomeAction.RETRY)
    }

    fun homeContinue() = wizard(WizardEvent.HomeScreenDone)

    // ---------- Done / My TVs ----------

    private fun enterDone() {
        val lg = log
        _ui.update { it.copy(done = it.done.copy(records = lg?.records().orEmpty(), newHome = it.home is HomeState.Installed)) }
        if (_ui.value.wizard.mode == Mode.CLEAN) viewModelScope.launch {
            val s = shell ?: return@launch
            val report = runCatching { tvLock.withLock { Finisher.measure(s, snapshot?.memAvailableKb) } }.getOrNull()
            _ui.update { it.copy(done = it.done.copy(freedMb = report?.freedMb)) }
        }
    }

    fun checkDebuggingOff() {
        val b = backend ?: return
        val host = found?.host ?: return
        _ui.update { it.copy(done = it.done.copy(debugging = DebugCheck.CHECKING)) }
        viewModelScope.launch {
            b.close(shell); shell = null
            val off = Finisher.debuggingOff { b.portOpen(host) }
            trace.line("debugging off: $off")
            _ui.update { it.copy(done = it.done.copy(debugging = if (off) DebugCheck.OFF else DebugCheck.STILL_ON)) }
        }
    }

    fun restoreEverything() {
        val lg = log ?: return
        if (_ui.value.done.restore is RestoreUi.Running) return
        wizard(WizardEvent.Busy(true))
        _ui.update { it.copy(done = it.done.copy(restore = RestoreUi.Running(0), turnOnNote = null)) }
        viewModelScope.launch {
            var count = 0
            var outcome: RestoreOutcome? = null
            for (attempt in 0..1) {
                val s = shell ?: if (reconnect()) shell!! else null
                if (s == null) break
                outcome = tvLock.withLock {
                    Restorer(s, lg).run { count++; _ui.update { it.copy(done = it.done.copy(restore = RestoreUi.Running(count))) } }
                }
                if (outcome !is RestoreOutcome.Disconnected) break
                shell = null
            }
            trace.line("restore: $outcome")
            val ui = when (outcome) {
                is RestoreOutcome.Done -> {
                    // The old launcher coming back is "old home screen back", not one more app.
                    val apps = outcome.enabled.count { !dev.tidytv.core.lists.Launchers.isHomeApp(it) }
                    val home = outcome.homeRestored
                    RestoreUi.Finished(
                        when {
                            apps == 0 -> "Nothing to put back: every app is already on" + if (home) " and your old home screen is set." else "."
                            else -> "Everything is back the way it was: $apps ${if (apps == 1) "app" else "apps"} on" + if (home) ", old home screen back." else "."
                        } + if (outcome.gone.isEmpty()) "" else {
                            val names = outcome.gone.joinToString(", ") { p -> _ui.value.labels[p] ?: dev.tidytv.core.lists.AppLabels.label(p) }
                            " You uninstalled $names yourself; get ${if (outcome.gone.size == 1) "it" else "them"} again from Google Play."
                        },
                        ok = true,
                    )
                }
                is RestoreOutcome.Partial -> RestoreUi.Finished(
                    "${outcome.stillOff.size} apps wouldn't turn back on, so 0TV kept your current home screen. Tap Restore everything again.", ok = false,
                )
                RestoreOutcome.WrongTv -> RestoreUi.Finished(WRONG_TV_MESSAGE, ok = false)
                else -> RestoreUi.Finished("The TV stopped answering. Check it's on and on the same network, then tap Restore everything again.", ok = false)
            }
            _ui.update { it.copy(done = it.done.copy(restore = ui, records = lg.records(), debugging = DebugCheck.UNKNOWN)) }
            wizard(WizardEvent.Busy(false))
        }
    }

    /** Turns one app TidyTV turned off back on, leaving everything else as it is (GitHub #2). */
    fun turnOn(pkg: String) {
        val lg = log ?: return
        val d = _ui.value.done
        if (pkg in d.turningOn || d.restore is RestoreUi.Running) return
        _ui.update { it.copy(done = it.done.copy(turningOn = it.done.turningOn + pkg, turnOnNote = null)) }
        viewModelScope.launch {
            var outcome: RestoreOutcome? = null
            for (attempt in 0..1) {
                val s = shell ?: if (reconnect()) shell!! else null
                if (s == null) break
                outcome = tvLock.withLock { Restorer(s, lg).enableOnly(setOf(pkg)) }
                if (outcome !is RestoreOutcome.Disconnected) break
                shell = null
            }
            trace.line("turn on $pkg: $outcome")
            val name = _ui.value.labels[pkg] ?: _ui.value.tidy.order.firstOrNull { it.pkg == pkg }?.label ?: dev.tidytv.core.lists.AppLabels.label(pkg)
            val note = when (outcome) {
                is RestoreOutcome.Done -> RestoreUi.Finished("$name is back on.", ok = true)
                is RestoreOutcome.Partial -> RestoreUi.Finished("$name wouldn't turn back on. Try again, or use Restore everything.", ok = false)
                RestoreOutcome.WrongTv -> RestoreUi.Finished(WRONG_TV_MESSAGE, ok = false)
                else -> RestoreUi.Finished("The TV stopped answering. Check it's on and on the same network, then try again.", ok = false)
            }
            _ui.update { it.copy(done = it.done.copy(turningOn = it.done.turningOn - pkg, turnOnNote = note, records = lg.records())) }
        }
    }

    // ---------- Logs ----------

    /** A redacted text file for Share logs. */
    fun exportLogs(dir: File, appVersion: String): File {
        val tv = _ui.value.tv
        val header = buildString {
            appendLine("0TV $appVersion on Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            if (tv != null) appendLine("TV: ${tv.model}, Android ${tv.release} (SDK ${tv.sdk}), list: ${list?.id ?: "none"}")
            appendLine("step: ${_ui.value.wizard.step}, connect: ${_ui.value.connect::class.simpleName}")
            _ui.value.problem?.let { appendLine("problem: $it") }
            (_ui.value.home as? HomeState.Problem)?.let { appendLine("home problem: ${it.message}") }
            val r = _ui.value.review
            if (r.backup != BackupStatus.Running) appendLine("review: list ${r.listTitle ?: "none"}, exact ${r.exact}, candidates ${r.candidates.size}, picked ${r.selected.size}")
        }
        val extra = linkedMapOf<String, String>()
        log?.records()?.let { r -> extra["actions"] = r.joinToString("\n") { "${it.ts} ${it.op} ${it.phase} ${it.target} ${it.detail.orEmpty()}" } }
        val text = trace.export(header, extra, secrets = listOfNotNull(tv?.serial, snapshot?.info?.serial))
        dir.mkdirs()
        return File(dir, "tidytv-log.txt").apply { writeText(text) }
    }

    override fun onCleared() {
        backend?.close(shell)
    }
}


/** Shown whenever a record cannot be matched to the connected TV. States the limits of the check. */
internal const val WRONG_TV_MESSAGE =
    "Nothing was changed. This record belongs to a different TV, or this TV can't be identified. " +
    "0TV matches a TV by its Android user (it must be user 0) and an ID that a factory reset or some " +
    "firmware updates replace, so the right TV can also be refused after a reset, and a TV running a " +
    "secondary profile is always refused. This is a safety check, not a hardware guarantee. " +
    "If this TV was reset, its apps are back to stock and there is nothing to restore; to undo a change " +
    "yourself, use the TV's Settings > Apps."
