package dev.tidytv.core.ops

import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.lists.PackageName
import dev.tidytv.core.lists.Protected

sealed interface PkgResult {
    val pkg: String
    data class Disabled(override val pkg: String) : PkgResult
    /** Uninstalled for the TV's user ([yours]: an app the owner installed, so it can't come back). */
    data class Removed(override val pkg: String, val yours: Boolean = false) : PkgResult
    data class AlreadyOff(override val pkg: String) : PkgResult
    data class NotInstalled(override val pkg: String) : PkgResult
    data class Skipped(override val pkg: String, val reason: String) : PkgResult
    data class Failed(override val pkg: String, val reason: String) : PkgResult
}

/** Turned off or removed by this run: the app is no longer running on the TV. */
val PkgResult.isOff get() = this is PkgResult.Disabled || this is PkgResult.Removed

sealed interface DebloatOutcome {
    val results: List<PkgResult>
    data class Finished(override val results: List<PkgResult>) : DebloatOutcome
    /** Stopped on something unexpected. Nothing after [results] was touched. */
    data class Stopped(override val results: List<PkgResult>, val reason: String) : DebloatOutcome
    /** The TV stopped answering. Running again resumes; the log already has every change. */
    data class Disconnected(override val results: List<PkgResult>) : DebloatOutcome

    /** Apps now off: turned off or removed. */
    val disabledCount get() = results.count { it is PkgResult.Disabled || it is PkgResult.Removed }
}

/**
 * Turns packages off (port of upstream tools/disable.sh and tv-detox turn_off), or, for the ones in
 * `remove`, uninstalls them for the TV's user (`pm uninstall -k --user 0`; Restore brings a
 * preinstalled app back with install-existing). Either way:
 * protected and home-screen apps are refused, PERSISTENT packages skipped, each package goes on the
 * TV's undo list and in the phone log before its own `pm disable-user`, and every batch is checked
 * with `pm list packages -d`. Anything unexpected stops the run instead of guessing.
 */
class Debloater(
    private val tv: TvShell,
    private val log: ActionLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val batchSize: Int = 5,
) {
    companion object {
        /** Android writes package state lazily; restarting sooner can undo the change (tv-detox). */
        const val SAVE_WAIT_MS = 15_000L
    }

    suspend fun run(
        pkgs: List<String>,
        liveProtected: Set<String> = emptySet(),
        listKeep: Set<String> = emptySet(),
        onResult: (PkgResult) -> Unit = {},
        remove: Set<String> = emptySet(),
    ): DebloatOutcome {
        val results = mutableListOf<PkgResult>()
        fun emit(r: PkgResult) { results += r; onResult(r) }

        try {
            val todo = mutableListOf<String>()
            for (pkg in pkgs.distinct()) {
                val refused = when {
                    !PackageName.isValid(pkg) -> "not a package name"
                    Launchers.isHomeApp(pkg) -> "home screen app, handled by the home screen step"
                    else -> Protected.reason(pkg, liveProtected, listKeep)
                }
                if (refused != null) emit(PkgResult.Skipped(pkg, refused)) else todo += pkg
            }
            if (todo.isEmpty()) return DebloatOutcome.Finished(results)
            if (!TvBinding.ensure(tv, log, clock)) return DebloatOutcome.Stopped(results, "This TV can't be matched to its record, so nothing was changed.")

            val installed = Parse.packages(tv.run(Commands.LIST_PACKAGES))
            var disabled = Parse.packages(tv.run(Commands.LIST_DISABLED))
            val flags = Parse.flags(tv.run(Commands.flags(todo.filter { it in installed })))
            val yours = if (remove.any { it in todo }) Parse.packages(tv.run(Commands.LIST_USER_APPS)) else emptySet()

            // After a dropped connection: commands that were sent but never checked.
            val records = log.records()
            val unconfirmed = records.pendingDisables()
            val unconfirmedRemoves = records.pendingRemoves()

            val work = mutableListOf<String>()
            for (pkg in todo) when {
                pkg in remove && pkg !in installed && pkg in unconfirmedRemoves -> {
                    log.append(ActionRecord(clock(), Op.REMOVE, pkg, Phase.DONE, detail = "confirmed after reconnect"))
                    emit(PkgResult.Removed(pkg))
                }
                pkg !in installed -> emit(PkgResult.NotInstalled(pkg))
                pkg in remove && flags[pkg].orEmpty().contains("PERSISTENT") -> emit(PkgResult.Skipped(pkg, "always-running system app"))
                pkg in remove -> work += pkg
                pkg in disabled && pkg in unconfirmed -> {
                    log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.DONE, detail = "confirmed after reconnect"))
                    emit(PkgResult.Disabled(pkg))
                }
                pkg in disabled -> emit(PkgResult.AlreadyOff(pkg))
                flags[pkg].orEmpty().contains("PERSISTENT") -> emit(PkgResult.Skipped(pkg, "always-running system app"))
                else -> work += pkg
            }

            for (batch in work.chunked(batchSize)) {
                val toDisable = batch.filter { it !in remove }
                val toRemove = batch.filter { it in remove }
                // Owner apps can't be brought back, so they don't go on the TV's undo list.
                val undoable = toRemove.filter { it !in yours }
                if ((toDisable.isNotEmpty() && !Parse.lines(tv.run(Commands.mirror(toDisable))).contains("ok")) ||
                    (undoable.isNotEmpty() && !Parse.lines(tv.run(Commands.mirrorRemoved(undoable))).contains("ok"))
                ) {
                    return DebloatOutcome.Stopped(results, "Couldn't save the undo list on the TV, so nothing more was changed.")
                }
                for (pkg in toRemove) {
                    val detail = if (pkg in yours) OWNER_APP else null
                    log.append(ActionRecord(clock(), Op.REMOVE, pkg, Phase.INTENT, detail = detail))
                    val out = tv.run(Commands.removeForUser(pkg))
                    if (!out.contains("Success")) {
                        log.append(ActionRecord(clock(), Op.REMOVE, pkg, Phase.FAILED, detail = detail ?: out.take(300)))
                        emit(PkgResult.Failed(pkg, Parse.lines(out).firstOrNull() ?: "no answer"))
                        return DebloatOutcome.Stopped(results, "The TV refused to uninstall ${pkg}. Nothing else was changed.")
                    }
                }
                if (toRemove.isNotEmpty()) {
                    val left = Parse.packages(tv.run(Commands.LIST_PACKAGES))
                    for (pkg in toRemove) {
                        val detail = if (pkg in yours) OWNER_APP else null
                        if (pkg !in left) {
                            log.append(ActionRecord(clock(), Op.REMOVE, pkg, Phase.DONE, detail = detail))
                            emit(PkgResult.Removed(pkg, yours = pkg in yours))
                        } else {
                            log.append(ActionRecord(clock(), Op.REMOVE, pkg, Phase.FAILED, detail = detail ?: "still installed"))
                            emit(PkgResult.Failed(pkg, "still installed after the command"))
                            return DebloatOutcome.Stopped(results, "$pkg is still there. Nothing else was changed.")
                        }
                    }
                }
                if (toDisable.isEmpty()) continue
                for (pkg in toDisable) {
                    log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.INTENT))
                    val out = tv.run(Commands.disable(pkg))
                    when {
                        out.contains("new state: disabled") -> Unit
                        out.contains("Unknown package") || out.contains("not found") -> {
                            log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.FAILED, detail = "not installed"))
                            emit(PkgResult.NotInstalled(pkg))
                        }
                        else -> {
                            log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.FAILED, detail = out.take(300)))
                            emit(PkgResult.Failed(pkg, Parse.lines(out).firstOrNull() ?: "no answer"))
                            return DebloatOutcome.Stopped(results, "The TV refused to turn off ${pkg}. Nothing else was changed.")
                        }
                    }
                }
                disabled = Parse.packages(tv.run(Commands.LIST_DISABLED))
                for (pkg in toDisable) {
                    if (results.any { it.pkg == pkg }) continue // already reported as not installed
                    if (pkg in disabled) {
                        log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.DONE))
                        emit(PkgResult.Disabled(pkg))
                    } else {
                        log.append(ActionRecord(clock(), Op.DISABLE, pkg, Phase.FAILED, detail = "still enabled"))
                        emit(PkgResult.Failed(pkg, "still on after the command"))
                        return DebloatOutcome.Stopped(results, "$pkg didn't stay off. Nothing else was changed.")
                    }
                }
            }
            if (results.any { it is PkgResult.Disabled || it is PkgResult.Removed }) sleep(SAVE_WAIT_MS)
            return DebloatOutcome.Finished(results)
        } catch (e: TvConnectionLost) {
            return DebloatOutcome.Disconnected(results)
        } catch (e: UnexpectedOutput) {
            return DebloatOutcome.Stopped(results, "The TV gave an answer TidyTV didn't expect, so it stopped. (${e.command})")
        }
    }
}
