package dev.tidytv.core.flow

enum class TvKind { ANDROID_TV, FIRE_TV, SAMSUNG_LG, NOT_SURE }

enum class Step { WELCOME, UNSUPPORTED, GET_READY, CONNECT, HUB, REVIEW, TIDYING, HOME_SCREEN, DONE, REMOTE, SEND_FILES, CHECK, RESCUE, SPACE, ADS, APPS, COBALT }

enum class Mode { CLEAN, RESTORE }

data class Wizard(
    val step: Step = Step.WELCOME,
    val kind: TvKind? = null,
    val mode: Mode = Mode.CLEAN,
    /** A change is running on the TV: no navigation away until it ends. */
    val busy: Boolean = false,
)

sealed interface WizardEvent {
    data class Choose(val kind: TvKind) : WizardEvent
    data object RestoreTapped : WizardEvent
    data object Ready : WizardEvent
    /** [fireOs] comes from the TV itself and overrides what the user picked. */
    data class Connected(val fireOs: Boolean) : WizardEvent
    data object StartTidy : WizardEvent
    data class Busy(val busy: Boolean) : WizardEvent
    /** Clean-up ended. [stopped] keeps the user on the tidying screen with the stop message. */
    data class TidyEnded(val stopped: Boolean, val launcherPossible: Boolean) : WizardEvent
    data object HomeScreenDone : WizardEvent
    data object ToDone : WizardEvent
    data object Back : WizardEvent
    data object StartOver : WizardEvent
    /** A saved TV on the Welcome screen: straight to Connect, no setup steps. */
    data object ConnectSaved : WizardEvent
    // Tiles on the My TV hub.
    data object OpenTidy : WizardEvent
    data object OpenRemote : WizardEvent
    data object OpenFiles : WizardEvent
    data object OpenCobalt : WizardEvent
    data object OpenHomeScreen : WizardEvent
    data object OpenRestore : WizardEvent
    data object ToHub : WizardEvent
    data object OpenCheck : WizardEvent
    /** "Free up space" on the hub. */
    data object OpenSpace : WizardEvent
    data object OpenAds : WizardEvent
    data object OpenApps : WizardEvent
    /** "TV stuck after a change?" on Welcome: keep trying a saved TV and restore as soon as it answers. */
    data object OpenRescue : WizardEvent
}

/**
 * The app's navigation as a pure reducer. Connecting lands on the My TV hub; each tile opens one
 * feature and Back returns to the hub. [reduce] returns null when Back should leave the app.
 */
object WizardReducer {
    fun reduce(s: Wizard, e: WizardEvent): Wizard? = when (e) {
        is WizardEvent.Choose -> if (s.step != Step.WELCOME) s else when (e.kind) {
            TvKind.SAMSUNG_LG -> s.copy(step = Step.UNSUPPORTED, kind = e.kind, mode = Mode.CLEAN)
            else -> s.copy(step = Step.GET_READY, kind = e.kind, mode = Mode.CLEAN)
        }
        WizardEvent.RestoreTapped -> if (s.busy) s else Wizard(step = Step.GET_READY, mode = Mode.RESTORE)
        WizardEvent.Ready -> if (s.step == Step.GET_READY) s.copy(step = Step.CONNECT) else s
        is WizardEvent.Connected -> if (s.step != Step.CONNECT) s else {
            val kind = if (e.fireOs) TvKind.FIRE_TV else if (s.kind == TvKind.FIRE_TV) TvKind.ANDROID_TV else s.kind ?: TvKind.ANDROID_TV
            s.copy(step = Step.HUB, kind = kind, mode = Mode.CLEAN)
        }
        WizardEvent.ConnectSaved -> if (s.step == Step.WELCOME) Wizard(step = Step.CONNECT) else s
        WizardEvent.OpenTidy -> if (s.step == Step.HUB) s.copy(step = Step.REVIEW, mode = Mode.CLEAN) else s
        WizardEvent.OpenRemote -> if (s.step == Step.HUB) s.copy(step = Step.REMOTE) else s
        WizardEvent.OpenCobalt -> if (s.step == Step.HUB) s.copy(step = Step.COBALT) else s
        WizardEvent.OpenFiles -> if (s.step == Step.HUB) s.copy(step = Step.SEND_FILES) else s
        // Fire OS won't let another home screen take over.
        WizardEvent.OpenHomeScreen -> if (s.step == Step.HUB && s.kind != TvKind.FIRE_TV) s.copy(step = Step.HOME_SCREEN, mode = Mode.CLEAN) else s
        WizardEvent.OpenCheck -> if (s.step == Step.HUB) s.copy(step = Step.CHECK) else s
        WizardEvent.OpenSpace -> if (s.step == Step.HUB) s.copy(step = Step.SPACE) else s
        WizardEvent.OpenAds -> if (s.step == Step.HUB) s.copy(step = Step.ADS) else s
        WizardEvent.OpenApps -> if (s.step == Step.HUB) s.copy(step = Step.APPS) else s
        WizardEvent.OpenRescue -> if (s.step == Step.WELCOME) s.copy(step = Step.RESCUE) else s
        WizardEvent.OpenRestore -> if (s.step == Step.HUB) s.copy(step = Step.DONE, mode = Mode.RESTORE) else s
        WizardEvent.ToHub -> if (s.busy || s.step in setOf(Step.WELCOME, Step.UNSUPPORTED, Step.GET_READY, Step.CONNECT, Step.RESCUE)) s else s.copy(step = Step.HUB)
        WizardEvent.StartTidy -> if (s.step == Step.REVIEW) s.copy(step = Step.TIDYING, busy = true) else s
        is WizardEvent.Busy -> s.copy(busy = e.busy)
        is WizardEvent.TidyEnded -> when {
            s.step != Step.TIDYING -> s
            e.stopped -> s.copy(busy = false)
            // Fire TV: clean-up only, no home screen.
            e.launcherPossible && s.kind != TvKind.FIRE_TV -> s.copy(step = Step.HOME_SCREEN, busy = false)
            else -> s.copy(step = Step.DONE, busy = false)
        }
        WizardEvent.HomeScreenDone -> if (s.step == Step.HOME_SCREEN && !s.busy) s.copy(step = Step.DONE) else s
        WizardEvent.ToDone -> if (s.busy) s else s.copy(step = Step.DONE)
        WizardEvent.StartOver -> if (s.busy) s else Wizard()
        WizardEvent.Back -> back(s)
    }

    private fun back(s: Wizard): Wizard? {
        if (s.busy) return s
        return when (s.step) {
            Step.WELCOME -> null
            Step.UNSUPPORTED, Step.GET_READY -> Wizard()
            // A saved TV skipped Get ready, so Back goes home instead.
            Step.CONNECT -> if (s.kind == null && s.mode == Mode.CLEAN) Wizard() else s.copy(step = Step.GET_READY)
            Step.HUB -> Wizard()
            Step.REVIEW, Step.REMOTE, Step.SEND_FILES, Step.CHECK, Step.SPACE, Step.ADS, Step.APPS, Step.COBALT -> s.copy(step = Step.HUB)
            Step.RESCUE -> Wizard()
            // Changes are made: going back can't undo them, so Back goes to the summary.
            Step.TIDYING, Step.HOME_SCREEN -> s.copy(step = Step.DONE)
            Step.DONE -> s.copy(step = Step.HUB)
        }
    }
}
