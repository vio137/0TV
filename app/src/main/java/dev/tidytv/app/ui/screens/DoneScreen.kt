package dev.tidytv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.tidytv.app.DebugCheck
import dev.tidytv.app.DoneState
import dev.tidytv.app.RestoreUi
import dev.tidytv.app.TvSummary
import androidx.compose.ui.graphics.Color
import dev.tidytv.app.data.FeedbackKind
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.ChipRow
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.Title
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.FeedbackRequest
import dev.tidytv.app.ui.LocalAppActions
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.NumberedStep
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.TvCard
import dev.tidytv.core.lists.AppLabels
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.ops.ActionRecord
import dev.tidytv.core.ops.disabledByUs
import dev.tidytv.core.ops.offByUs
import dev.tidytv.core.ops.Finisher
import dev.tidytv.core.ops.Op
import dev.tidytv.core.ops.Phase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DoneScreen(
    tv: TvSummary?,
    s: DoneState,
    disabledCount: Int,
    restoreMode: Boolean,
    labels: Map<String, String>,
    onRestore: () -> Unit,
    onCheckDebugging: () -> Unit,
    onShareLogs: () -> Unit,
    onStartOver: () -> Unit,
    onTurnOn: (String) -> Unit = {},
    onMyTv: () -> Unit = {},
    onCheck: () -> Unit = {},
    preview: dev.tidytv.app.TvPreview = dev.tidytv.app.TvPreview(),
) {
    val restoring = s.restore is RestoreUi.Running
    val actions = LocalAppActions.current
    ScreenFrame(bottom = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Back to My TV", onMyTv, color = Tidy.Blue)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (!restoreMode) {
                    TextLink("Send feedback", { actions.feedback(FeedbackRequest(FeedbackKind.FEATURE, "done")) }, color = Tidy.Muted, small = true)
                    Spacer(Modifier.width(8.dp))
                }
                TextLink("Start over", onStartOver, color = Tidy.Muted, small = true)
            }
        }
    }) {
        val fin = s.restore as? RestoreUi.Finished
        HeroMascot(mood = when {
            fin?.ok == false -> Mood.WORRIED
            fin?.ok == true || (!restoreMode && disabledCount > 0) -> Mood.CHEER
            else -> Mood.STAND
        }, height = 130.dp)
        val restored = (s.restore as? RestoreUi.Finished)?.ok == true
        val result = !restoreMode && !restored && disabledCount > 0
        if (result) {
            Title(if (disabledCount == 1) "1 app off" else "$disabledCount apps off", align = TextAlign.Center)
            ChipRow(center = true) {
                if (s.newHome) InfoChip(Icons.Rounded.Home, "New home screen")
                s.freedMb?.takeIf { it > 0 }?.let { InfoChip(Icons.Rounded.Memory, "$it MB freed") }
                InfoChip(Icons.Rounded.Backup, "Backup saved")
            }
        }
        if (!restoreMode && s.homeUntested) UntestedHomeCard()
        if (tv != null) TvCard(tv.title, listOf(tv.model.takeIf { it != tv.title }, tv.host).filterNotNull().joinToString("\n"), preview = preview)
        when (val r = s.restore) {
            is RestoreUi.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(); Spacer(Modifier.width(10.dp)); Body("Turning things back on… ${r.count}")
            }
            is RestoreUi.Finished -> if (r.ok) SafetyBanner(r.message) else Note(r.message)
            RestoreUi.Idle -> if (restoreMode && s.records.none { it.op == Op.DISABLE }) {
                Body("0TV has no changes saved on this phone for this TV. Restore still checks the list kept on the TV itself.")
            }
        }
        if (!restoring) OffApps(s, labels, onTurnOn)
        if (result) Outline {
            Text("Do they stay off?", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            Text("Some TVs turn apps back on when they restart or update. Check my TV restarts the TV and tells you which came back, then turns them off again.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
            TextLink("Check after a restart", onCheck, icon = Icons.Rounded.Restore)
        }
        BigButton(
            if (restoring) "Restoring…" else "Restore everything", onRestore, color = Tidy.Green, enabled = !restoring,
            leading = if (restoring) null else { { Icon(Icons.Rounded.Restore, null, tint = Color.White) } },
        )
        if (!restoreMode) DebuggingCard(s.debugging, onCheckDebugging)
        Activity(s.records, labels)
        val actions = LocalAppActions.current
        ChipRow {
            if (result) TextLink("Share your result", { actions.shareCard() }, icon = Icons.Rounded.ThumbUp)
            TextLink("Share logs", onShareLogs)
        }
    }
}

/** Every app TidyTV turned off and that is still off, each with its own Turn on (GitHub #2). */
@Composable
private fun OffApps(s: DoneState, labels: Map<String, String>, onTurnOn: (String) -> Unit) {
    val off = s.records.offByUs().filterNot { Launchers.isHomeApp(it) }
    s.turnOnNote?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message) }
    if (off.isEmpty()) return
    var showAll by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Outline {
        Text("Apps turned off", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        Spacer(Modifier.heightIn(min = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (pkg in if (showAll) off else off.take(6)) {
                val name = labels[pkg] ?: AppLabels.label(pkg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, name, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f), maxLines = 2)
                    if (pkg in s.turningOn) Spinner() else TextLink("Turn on", { onTurnOn(pkg) })
                }
            }
        }
        if (off.size > 6 && !showAll) TextLink("Show all ${off.size}", { showAll = true })
    }
}

@Composable
private fun DebuggingCard(d: DebugCheck, onCheck: () -> Unit) {
    Outline {
        Text("Turn off debugging", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        Spacer(Modifier.heightIn(min = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Finisher.TURN_OFF_STEPS.forEachIndexed { i, step -> NumberedStep(i + 1, step) }
        }
        Spacer(Modifier.heightIn(min = 10.dp))
        when (d) {
            DebugCheck.OFF -> Row(verticalAlignment = Alignment.CenterVertically) {
                Check(Tidy.Green, 24.dp); Spacer(Modifier.width(8.dp))
                Text("Debugging is off. All done!", style = MaterialTheme.typography.titleMedium, color = Tidy.GreenInk)
            }
            DebugCheck.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) { Spinner(); Spacer(Modifier.width(8.dp)); Body("Checking…") }
            DebugCheck.STILL_ON -> Column {
                Note("Your TV still answers on the debugging port. Turn Network debugging off, then check again.")
                TextLink("Check it's off", onCheck)
            }
            DebugCheck.UNKNOWN -> TextLink("Check it's off", onCheck)
        }
    }
}

@Composable
private fun Activity(records: List<ActionRecord>, labels: Map<String, String>) {
    val items = records.filter { it.phase == Phase.DONE && it.detail != "was not off" }.mapNotNull { r -> describe(r, labels)?.let { r to it } }
        .asReversed().take(40)
    if (items.isEmpty()) return
    Text("Recent activity", style = MaterialTheme.typography.titleMedium, color = Tidy.Muted)
    Outline {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((r, text) in items) Row(verticalAlignment = Alignment.CenterVertically) {
                val (bg, icon) = when (r.op) {
                    Op.DISABLE -> Tidy.RedSoft to Icons.Rounded.Block
                    Op.ENABLE -> Tidy.GreenSoft to Icons.Rounded.Restore
                    else -> Tidy.BlueSoft to Icons.Rounded.Home
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = Tidy.Ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                    Text(whenText(r.ts), style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
                }
            }
        }
    }
}

private fun describe(r: ActionRecord, labels: Map<String, String>): String? {
    val name = { labels[r.target] ?: AppLabels.label(r.target) }
    return when (r.op) {
        Op.DISABLE -> "Turned off ${name()}"
        Op.ENABLE -> "Turned on ${name()}"
        Op.INSTALL -> "Installed the clean home screen"
        Op.UNINSTALL -> "Removed the clean home screen"
        Op.SET_HOME -> if (dev.tidytv.core.lists.HomeApp.of(r.target.substringBefore('/')) != null) "Set new home screen" else "Put the old home screen back"
        Op.SETTING -> "Restored a setting"
        Op.REMOVE -> "Uninstalled ${name()}"
        Op.REINSTALL -> "Brought back ${name()}"
        Op.TV_BIND -> null
    }
}

private fun whenText(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val sameDay = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "Today at $time" else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts)) + " at $time"
}

