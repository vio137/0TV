package dev.tidytv.app.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidytv.app.BackupStatus
import dev.tidytv.app.ReviewState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.ChipRow
import dev.tidytv.app.ui.IconBadge
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.MoreInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.vector.ImageVector
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.Toggle
import dev.tidytv.core.lists.Candidate
import dev.tidytv.core.lists.Group

private fun Group.badge(): Pair<Color, ImageVector> = when (this) {
    Group.ADS_TRACKING -> Color(0xFFFF4F6D) to Icons.Rounded.Campaign
    Group.PRELOADED -> Tidy.Orange to Icons.Rounded.Apps
    Group.VOICE -> Tidy.Purple to Icons.Rounded.Mic
    Group.CASTING -> Color(0xFF16A3C9) to Icons.Rounded.Cast
    Group.UPDATES -> Color(0xFF6B7A99) to Icons.Rounded.SystemUpdate
    Group.STREAMING -> Tidy.Green to Icons.Rounded.PlayArrow
}

/** What turning the group off changes, in two or three words; the full sentence is behind (i). */
private fun Group.warning(): String? = when (this) {
    Group.VOICE -> "Voice button stops"
    Group.CASTING -> "No casting to TV"
    Group.UPDATES -> "No TV updates"
    Group.STREAMING -> "Only unused ones"
    else -> null
}

private fun Group.help(): String? = when (this) {
    Group.VOICE -> "Off means the remote's voice button stops working."
    Group.CASTING -> "Off means you can't cast from your phone to this TV."
    Group.UPDATES -> "Off stops firmware updates. Turn back on before updating."
    Group.STREAMING -> "Only ones you don't use. Your sign-ins come back if you turn them on again."
    else -> null
}

@Composable
fun ReviewScreen(
    s: ReviewState,
    onToggleApp: (String) -> Unit,
    onToggleGroup: (Group, Boolean) -> Unit,
    onStart: () -> Unit,
    onRetryBackup: () -> Unit,
    onDone: () -> Unit,
    onShowMore: () -> Unit = {},
    onAllApps: () -> Unit = {},
    onUninstall: (Boolean) -> Unit = {},
) {
    val n = s.selected.size
    ScreenFrame(bottom = {
        when {
            s.noList && s.extra.isEmpty() -> BigButton("Finish", onDone, color = Tidy.Blue)
            s.noList -> BigButton(
                if (n == 0) "Pick apps to turn off" else "Turn off $n app${if (n == 1) "" else "s"}", onStart,
                color = Tidy.Green, enabled = s.backup == BackupStatus.Saved && n > 0,
            )
            else -> BigButton(
                if (n == 0) "Pick apps to turn off" else "Turn off $n app${if (n == 1) "" else "s"}", onStart,
                color = Tidy.Green, enabled = s.backup == BackupStatus.Saved && n > 0,
            )
        }
    }) {
        if (s.backup != BackupStatus.Saved) { BackupHeader(s.backup, onRetryBackup); return@ScreenFrame }
        if (s.noList) {
            val actions = dev.tidytv.app.ui.LocalAppActions.current
            BackupHeader(s.backup, onRetryBackup)
            Title("No tested list for this TV yet")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.extra.isEmpty()) "Nothing will be changed." else "Nothing is picked. You choose.", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted, modifier = Modifier.weight(1f))
                MoreInfo("No tested list yet") {
                    Body("Your backup is saved. 0TV only picks apps from a list someone has checked on the same model, and there isn't one for yours yet.", color = Tidy.Ink)
                    Body("Below are apps other TVs' lists turn off that are installed on yours. Look them up, pick only what you know, and Restore everything turns them back on.", color = Tidy.Ink)
                }
            }
            if (s.extra.isNotEmpty()) MoreAppsCard(s.extra, s.selected, onToggleApp)
            TextLink("Send my TV's details so it can be added", {
                actions.feedback(dev.tidytv.app.ui.FeedbackRequest(dev.tidytv.app.data.FeedbackKind.FEATURE, "no-list", s.tvDetails))
            })
            AllAppsCard(onAllApps)
            return@ScreenFrame
        }
        ChipRow {
            BackupHeader(s.backup, onRetryBackup)
            if (!s.exact) Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(Icons.Rounded.Info, "Similar model", color = Tidy.Yellow, textColor = Tidy.Ink)
                MoreInfo("Similar model", color = Tidy.Ink) {
                    Body(
                        "This list is for ${s.listTitle ?: "a similar model"}, not tested on ${s.yourTv?.let { "your $it" } ?: "yours"}. The safe parts are switched on; look through the rest.",
                        color = Tidy.Ink,
                    )
                    Body("Touch and hold any app to see its package ID.", color = Tidy.Muted)
                }
            }
        }
        if (s.fewFound) FewFoundCard(s.candidates.size, onShowMore)
        for (group in Group.entries) {
            val apps = s.candidates.filter { it.group == group }
            if (apps.isNotEmpty()) GroupCard(group, apps, s.selected, onToggleApp, onToggleGroup)
        }
        if (s.extra.isNotEmpty()) MoreAppsCard(s.extra, s.selected, onToggleApp)
        KeptCard(s.kept)
        AllAppsCard(onAllApps)
    }
}

@Composable
private fun BackupHeader(b: BackupStatus, onRetry: () -> Unit) {
    when (b) {
        BackupStatus.Running -> Bubble("Saving a backup…") { Spinner(26.dp) }
        BackupStatus.Saved -> InfoChip(Icons.Rounded.CheckCircle, "Backup saved")
        is BackupStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Note("${b.message} 0TV won't change anything without a backup.", color = Tidy.RedSoft, textColor = Tidy.Red)
            TextLink("Try the backup again", onRetry)
        }
    }
}

/** This TV's list found only a few apps: say so rather than look finished, and offer the rest. */
@Composable
private fun FewFoundCard(count: Int, onShowMore: () -> Unit) {
    CardBox(color = Tidy.BlueSoft) {
        Text(
            if (count == 1) "We found 1 app we're sure about" else "We found $count apps we're sure about",
            style = MaterialTheme.typography.titleMedium, color = Tidy.Ink,
        )
        Spacer(Modifier.heightIn(min = 10.dp))
        BigButton("Show more apps", onShowMore, color = Color.White, textColor = Tidy.Ink)
    }
}

/** Apps other TVs' lists turn off, all unselected; the risks are behind (i). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreAppsCard(apps: List<Candidate>, selected: Set<String>, onToggleApp: (String) -> Unit) {
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AddCircle, Tidy.Blue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("More apps (${apps.size})", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                Text("From other TVs' lists", style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
            }
            MoreInfo("More apps") {
                Body("These come from lists for other TVs, not yours. 0TV found them installed on your TV, but nobody has tested turning them off on this model.", color = Tidy.Ink)
                Body("None are picked to start with. Only pick apps you know and don't use. Apps 0TV protects, like the Play Store, keyboard and TV inputs, are never shown.", color = Tidy.Ink)
                Body("Your backup is saved, so Restore everything turns them back on.", color = Tidy.Ink)
            }
        }
        for (group in Group.entries) {
            val inGroup = apps.filter { it.group == group }
            if (inGroup.isEmpty()) continue
            ChipRow(Modifier.padding(top = 12.dp)) {
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
                group.warning()?.let { WarningChip(it) }
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in inGroup) AppChip(a, a.pkg in selected) { onToggleApp(a.pkg) }
            }
        }
    }
}

@Composable
private fun WarningChip(text: String) =
    InfoChip(Icons.Rounded.WarningAmber, text, color = Tidy.Amber, textColor = Tidy.AmberInk)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(group: Group, apps: List<Candidate>, selected: Set<String>, onToggleApp: (String) -> Unit, onToggleGroup: (Group, Boolean) -> Unit) {
    val on = apps.count { it.pkg in selected }
    val (color, icon) = group.badge()
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
            Text("${group.title} (${apps.size})", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f))
            Toggle(on > 0, { onToggleGroup(group, it) }, group.title)
        }
        group.warning()?.let { w ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WarningChip(w)
                MoreInfo(group.title, color = Tidy.AmberInk) { Body(group.help()!!, color = Tidy.Ink) }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in apps) AppChip(a, a.pkg in selected) { onToggleApp(a.pkg) }
        }
    }
}

/** An app to pick: its real icon and its name on two lines. Long-press shows its package ID and what we know about it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppChipLayout(pkg: String, label: String, picked: Boolean, note: String = "", onClick: () -> Unit) {
    var details by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (details) dev.tidytv.app.ui.AppDetailsSheet(label, pkg, note) { details = false }
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Checkbox, onLongClickLabel = "Show package ID", onLongClick = { details = true }, onClick = onClick)
            .semantics { stateDescription = if (picked) "will be turned off" else "stays on" }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.alpha(if (picked) 1f else 0.4f)) { AppIcon(pkg, label, size = 46.dp) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (picked) Tidy.Ink else Tidy.Muted, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppChip(c: Candidate, picked: Boolean, onClick: () -> Unit) = AppChipLayout(c.pkg, c.label, picked, c.note, onClick)

@Composable
private fun AllAppsCard(onClick: () -> Unit) {
    Outline(Modifier.clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Apps, Tidy.Blue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("All apps on this TV", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                Text("See every app with what it costs your TV, and turn off or uninstall any of them.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Tidy.Muted)
        }
    }
}

@Composable
private fun KeptCard(kept: List<String>) {
    Outline {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Lock, Color(0xFF9AA3B8))
            Spacer(Modifier.width(12.dp))
            Text("Always kept", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        }
        Text(
            (listOf("Play Store", "Google Play Services", "keyboard", "remote and Bluetooth", "TV inputs and Live TV") + kept.take(12)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
