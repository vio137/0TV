package dev.tidytv.core.ops

import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.Launchers

sealed interface HandoverOutcome {
    data class Done(val disabledStock: String?) : HandoverOutcome
    /** Home is Android's chooser and it is unclear which launcher is the stock one. */
    data class NeedsChoice(val claimants: Set<String>) : HandoverOutcome
    /** Home belongs to something TidyTV doesn't recognise; nothing was changed. */
    data class UnknownHome(val owner: String?) : HandoverOutcome
    /** A copy of the home screen signed with another key is installed (e.g. from tv-detox's own release). */
    data object SignatureConflict : HandoverOutcome
    /** This TV refuses sideloaded launchers (some China-market sets). Clean-up still works. */
    data class InstallBlocked(val output: String) : HandoverOutcome
    data class InstallFailed(val output: String) : HandoverOutcome
    data class StartFailed(val output: String) : HandoverOutcome
    /**
     * Google TV's setup app also claims Home and would grab it once the stock launcher is off, but turning it
     * off isn't safe on this TV ([check] says why). Nothing about Home was changed.
     */
    data class SetupAppBlocked(val check: WraithCheck) : HandoverOutcome
    /** Home landed on Google TV's setup app once the stock launcher was off. Rolled back; ask the user. */
    data class SetupWraithTookHome(val rolledBack: Boolean) : HandoverOutcome
    data class RolledBack(val homeNow: String?) : HandoverOutcome
    /** Rollback did not stick either. [rescue] are the commands that finish it once ADB answers. */
    data class RollbackFailed(val homeNow: String?, val rescue: List<String>) : HandoverOutcome
    data object Disconnected : HandoverOutcome
    /** A Play Store home screen (Projectivy) isn't installed on the TV yet. Nothing was changed. */
    data object NotInstalled : HandoverOutcome
}

/**
 * Installs one of TidyTV's home screens ([HomeApp]) and hands Home over to it. Order from
 * upstream launcher/build-and-install.sh: install alone, start the new launcher and confirm it came
 * up, only then turn the stock launcher off, set Home after (Android drops the preference when the
 * set of HOME apps changes), then check where Home landed and roll back if it isn't ours.
 */
class LauncherHandover(
    private val tv: TvShell,
    private val log: ActionLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val app: HomeApp = HomeApp.SIMPLE,
    /** Short waits while checking the Home key (an app opening takes a moment). Not the long "Android saves" wait. */
    private val settle: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    /** True once this run turned Google TV's setup app off, so a rollback turns it back on. */
    private var wraithOffByUs = false

    private var ours = app.component
    private val ourPkg = app.pkg

    /** [onEthernet]: the owner says the TV is on a network cable, so Wi-Fi owned by the setup app isn't a risk. */
    suspend fun run(apk: ByteArray, dockApps: List<String>, sdk: Int, onEthernet: Boolean = false): HandoverOutcome = try {
        this.onEthernet = onEthernet
        if (!TvBinding.ensure(tv, log, clock)) HandoverOutcome.InstallFailed("This TV can't be matched to its record, so nothing was changed.")
        else handover(apk, dockApps, sdk)
    } catch (e: TvConnectionLost) {
        HandoverOutcome.Disconnected
    }

    // There is deliberately no "replace the other copy" action: a signature conflict means a copy that
    // TidyTV did not install is on the TV, and TidyTV never uninstalls a launcher it cannot prove it installed.

    /**
     * After [HandoverOutcome.SetupWraithTookHome] and the user's OK: turn Google TV's setup app off
     * (guarded), so the next [run] can hand Home over. Returns the guard's verdict; Ok means it is off.
     */
    suspend fun disableSetupWraith(onEthernet: Boolean): WraithCheck {
        val check = SetupWraithGuard.check(tv, onEthernet)
        if (check != WraithCheck.Ok) return check
        return try {
            if (!TvBinding.ensure(tv, log, clock)) return WraithCheck.Unknown("this TV can't be matched to its record")
            val pkg = Launchers.SETUPWRAITH
            if (!Parse.lines(tv.run(Commands.mirror(listOf(pkg)))).contains("ok")) {
                return WraithCheck.Unknown("couldn't save the undo list on the TV")
            }
            log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.INTENT))
            tv.run(Commands.disable(pkg))
            val off = pkg in Parse.packages(tv.run(Commands.LIST_DISABLED))
            log.append(ActionRecord(clock(), Op.DISABLE, pkg, if (off) Phase.DONE else Phase.FAILED))
            if (off) WraithCheck.Ok else WraithCheck.Unknown("it didn't stay off")
        } catch (e: TvConnectionLost) {
            WraithCheck.Unknown("the TV stopped answering")
        }
    }

    private var onEthernet = false

    private suspend fun handover(apk: ByteArray, dockApps: List<String>, sdk: Int): HandoverOutcome {
        val homeBefore = Parse.component(tv.run(Commands.RESOLVE_HOME))
        val homePkg = homeBefore?.substringBefore('/')
        val stock: String? = when {
            // Already one of TidyTV's home screens: switch between them without touching stock.
            HomeApp.of(homePkg) != null -> null
            homePkg in Launchers.KNOWN_STOCK -> homePkg
            homePkg == "android" -> {
                val claimants = Parse.componentsIn(tv.run(Commands.QUERY_HOME))
                val known = claimants.filter { it in Launchers.KNOWN_STOCK }
                if (known.size != 1) return HandoverOutcome.NeedsChoice(claimants)
                known.single()
            }
            else -> return HandoverOutcome.UnknownHome(homePkg)
        }

        if (app.fromPlayStore) {
            // Installed by the owner from the TV's Play Store: check it's there and find its Home activity.
            val listed = Parse.packages(tv.run(Commands.isInstalled(ourPkg)))
            if (ourPkg !in listed) return HandoverOutcome.NotInstalled
            ours = Parse.component(tv.run(Commands.resolveHomeOf(ourPkg)))?.takeIf { it.startsWith("$ourPkg/") }
                ?: return HandoverOutcome.NotInstalled
        } else if (!install(apk)) {
            return lastInstallFailure!!
        }
        return afterInstall(homeBefore, stock, dockApps, sdk)
    }

    private var lastInstallFailure: HandoverOutcome? = null

    /** Step 1. Install on its own: a failed install must not reach the steps that touch Home. */
    private suspend fun install(apk: ByteArray): Boolean {
        tv.push(apk, Commands.APK_TMP)
        // Persist what the TV looked like BEFORE the attempt. "absent" needs proof: a control query for a package
        // every device has must work, otherwise an empty answer could be a broken query, so the state is "unknown".
        val control = runCatching { tv.run("pm list packages android").lines().any { it.trim() == "package:android" } }.getOrDefault(false)
        val listed = runCatching { Parse.packages(tv.run(Commands.isInstalled(ourPkg))) }.getOrNull()
        val token = TvBinding.token(tv)
        val prestate = when {
            token == null || listed == null -> "unknown"
            ourPkg in listed -> if (log.records().launcherInstalledByUs(ourPkg, token)) "ours" else "present"
            !control -> "unknown"
            else -> "absent"
        }
        log.append(ActionRecord(clock(), Op.INSTALL, ourPkg, Phase.INTENT, detail = "prestate=$prestate" + (token?.let { ";tv=$it" } ?: "")))
        val install = tv.run(Commands.INSTALL_TMP)
        tv.run(Commands.REMOVE_TMP)
        if (install.contains("Success")) {
            if (prestate == "absent" || prestate == "ours") log.append(ActionRecord(clock(), Op.INSTALL, ourPkg, Phase.DONE, detail = "tv=$token"))
            return true
        }
        log.append(ActionRecord(clock(), Op.INSTALL, ourPkg, Phase.FAILED, detail = install.take(300)))
        lastInstallFailure = when {
            install.contains("UPDATE_INCOMPATIBLE") -> HandoverOutcome.SignatureConflict
            install.contains("VERIFICATION_FAILURE") || install.contains("INSTALL_FAILED_ABORTED") ->
                HandoverOutcome.InstallBlocked(install.trim())
            else -> HandoverOutcome.InstallFailed(install.trim())
        }
        return false
    }

    private suspend fun afterInstall(homeBefore: String?, stock: String?, dockApps: List<String>, sdk: Int): HandoverOutcome {

        // 2. tv-detox takes the dock apps as start extras (below); cleanyourtv reads config.json, written after its first start.
        if (app.dockByConfig) tv.run(Commands.grantLocation(ourPkg)) // shows the Wi-Fi name; harmless if refused

        // 3. Start it before anything else changes; "Status: ok" and no "Error" line, both required.
        val extras = if (app.dockByConfig) emptyList() else dockApps
        val start = tv.run(Commands.startLauncher(ours, extras, clock() / 1000))
        if (!start.contains("Status: ok") || Parse.lines(start).any { it.startsWith("Error") }) {
            return HandoverOutcome.StartFailed(start.trim())
        }
        if (app.dockByConfig && dockApps.isNotEmpty()) {
            // Only now: the first start makes the launcher's own folder, and a folder the shell made before that is wiped
            // when the app first runs (seen on a TCL, Android 11), which silently dropped the owner's dock. Written through
            // the launcher's provider (a shell-made file can't be read on Android 14), then it restarts to read it.
            LauncherLook(tv, app, clock).writeDock(dockApps)
            tv.run(Commands.restartLook(ourPkg))
        }

        // 4. Google TV's setup app claims Home at priority 1 and takes it as soon as the stock launcher is off
        // (a recovery screen on TCL). Turn it off first, while the stock launcher still owns Home, like upstream's
        // tested order, but only when that's safe: it must not own the saved Wi-Fi, and setup must be finished.
        if (stock != null) prepareSetupApp()?.let { return it }

        // 5. Make it Home, then turn the stock launcher off, then set Home again.
        log.append(ActionRecord(clock(), Op.SET_HOME, ours, Phase.INTENT, before = homeBefore))
        setOurs(sdk)
        if (stock != null && !isPersistent(stock)) {
            if (!Parse.lines(tv.run(Commands.mirror(listOf(stock)))).contains("ok")) {
                return rollback(stock, sdk)
            }
            log.append(ActionRecord(clock(), Op.DISABLE, stock, Phase.INTENT))
            tv.run(Commands.disable(stock))
            val off = stock in Parse.packages(tv.run(Commands.LIST_DISABLED))
            log.append(ActionRecord(clock(), Op.DISABLE, stock, if (off) Phase.DONE else Phase.FAILED))
            sleep(Debloater.SAVE_WAIT_MS)
            setOurs(sdk)
        }

        // 6. Check where Home landed: Android's answer, then a real Home press from another app.
        val homeNow = Parse.component(tv.run(Commands.RESOLVE_HOME))
        val resolvesToUs = homeNow == ours || homeNow?.substringBefore('/') == ourPkg
        val pressOpens = if (resolvesToUs) homeKeyOpens() else null
        if (resolvesToUs && (pressOpens == null || pressOpens == ourPkg)) {
            log.append(ActionRecord(clock(), Op.SET_HOME, ours, Phase.DONE, before = homeBefore))
            return HandoverOutcome.Done(stock)
        }
        val landed = if (resolvesToUs) pressOpens else homeNow
        log.append(ActionRecord(clock(), Op.SET_HOME, ours, Phase.FAILED, before = homeBefore, detail = "home is $landed"))
        val result = rollback(stock, sdk)
        return when {
            landed?.substringBefore('/') == Launchers.SETUPWRAITH ->
                HandoverOutcome.SetupWraithTookHome(rolledBack = result is HandoverOutcome.RolledBack)
            // Two home screens at the same priority (another launcher the owner installed): Android would ask
            // which to use every time Home is pressed, so say that instead of "didn't switch".
            landed?.substringBefore('/') == "android" && result is HandoverOutcome.RolledBack ->
                HandoverOutcome.NeedsChoice(Parse.componentsIn(tv.run(Commands.QUERY_HOME)))
            else -> result
        }
    }

    /** Null: go on. Otherwise the outcome that stops the handover before Home changes. */
    private suspend fun prepareSetupApp(): HandoverOutcome? {
        val claimants = Parse.componentsIn(tv.run(Commands.QUERY_HOME))
        if (Launchers.SETUPWRAITH !in claimants) return null
        val check = SetupWraithGuard.check(tv, onEthernet)
        if (check != WraithCheck.Ok) return HandoverOutcome.SetupAppBlocked(check)
        val pkg = Launchers.SETUPWRAITH
        if (!Parse.lines(tv.run(Commands.mirror(listOf(pkg)))).contains("ok")) {
            return HandoverOutcome.SetupAppBlocked(WraithCheck.Unknown("couldn't save the undo list on the TV"))
        }
        log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.INTENT))
        tv.run(Commands.disable(pkg))
        val off = pkg in Parse.packages(tv.run(Commands.LIST_DISABLED))
        log.append(ActionRecord(clock(), Op.DISABLE, pkg, if (off) Phase.DONE else Phase.FAILED))
        if (!off) return HandoverOutcome.SetupAppBlocked(WraithCheck.Unknown("it didn't stay off"))
        wraithOffByUs = true
        settle(2_000)
        return null
    }

    /**
     * What the Home key really opens, the way a person would test it: leave to TV Settings, press Home, read
     * which app is on screen. Null when the TV won't say, so the caller trusts Android's own answer.
     */
    private suspend fun homeKeyOpens(): String? {
        tv.run(Commands.OPEN_SETTINGS)
        settle(1_500)
        tv.run(Commands.KEY_HOME)
        settle(2_500)
        return Parse.focusedPackage(tv.run(Commands.FOCUSED_APP))
    }

    private suspend fun setOurs(sdk: Int) {
        if (sdk >= 29) tv.run(Commands.addHomeRole(ourPkg))
        tv.run(Commands.setHome(ours))
    }

    private suspend fun isPersistent(pkg: String): Boolean {
        val flags = Parse.flags(tv.run(Commands.flags(listOf(pkg))))[pkg].orEmpty()
        // Can't read the flags: treat as always-running and leave it on (tv-detox).
        return flags.isEmpty() || flags.contains("PERSISTENT")
    }

    private suspend fun rollback(stock: String?, sdk: Int): HandoverOutcome {
        if (stock == null) return HandoverOutcome.RolledBack(Parse.component(tv.run(Commands.RESOLVE_HOME)))
        tv.run(Commands.enable(stock))
        val component = Parse.component(tv.run(Commands.resolveHomeOf(stock)))
        if (stock !in Parse.packages(tv.run(Commands.LIST_DISABLED))) {
            log.append(ActionRecord(clock(), Op.ENABLE, stock, Phase.DONE, detail = "rollback"))
        }
        if (wraithOffByUs) {
            // This run turned the setup app off; put it back too, so the TV is exactly as it was.
            val wraith = Launchers.SETUPWRAITH
            tv.run(Commands.enable(wraith))
            if (wraith !in Parse.packages(tv.run(Commands.LIST_DISABLED))) {
                log.append(ActionRecord(clock(), Op.ENABLE, wraith, Phase.DONE, detail = "rollback"))
                tv.run(Commands.unmirror(wraith))
                wraithOffByUs = false
            }
        }
        if (component != null) {
            if (sdk >= 29) tv.run(Commands.addHomeRole(stock))
            tv.run(Commands.setHome(component))
        }
        val homeNow = Parse.component(tv.run(Commands.RESOLVE_HOME))
        if (homeNow?.substringBefore('/') == stock) {
            log.append(ActionRecord(clock(), Op.SET_HOME, homeNow, Phase.DONE, detail = "rollback"))
            return HandoverOutcome.RolledBack(homeNow)
        }
        return HandoverOutcome.RollbackFailed(
            homeNow,
            listOf(Commands.enable(stock), "${Commands.RESOLVE_HOME} $stock", "cmd package set-home-activity --user 0 <component printed above>"),
        )
    }
}
