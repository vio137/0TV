package dev.tidytv.app.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidytv.app.ReviewState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.ChipRow
import dev.tidytv.app.ui.ChoiceCard
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.MoreInfo
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import dev.tidytv.core.lists.AppRow
import dev.tidytv.core.lists.AllAppsView
import dev.tidytv.core.lists.Origin
import dev.tidytv.core.lists.Tier
import dev.tidytv.core.ops.AppFacts

/** Every app on the TV in tiers, with what each costs, so the owner can turn off or uninstall any of them. */
@Composable
fun AllAppsScreen(
    s: ReviewState,
    labels: Map<String, String>,
    onToggle: (String) -> Unit,
    onUninstall: (Boolean) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    onConfirmPermanent: () -> Unit,
    onCancelPermanent: () -> Unit,
) {
    val all = s.all ?: return
    val n = s.selected.size
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<Origin>()) }
    ScreenFrame(
        top = { TopBar("Picked apps", onClose, null) },
        bottom = {
            val verb = if (s.uninstall) "Uninstall" else "Turn off"
            BigButton(
                if (n == 0) "Pick apps" else "$verb $n app${if (n == 1) "" else "s"}", onStart,
                color = if (s.uninstall) Tidy.Red else Tidy.Green, enabled = n > 0,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("All apps on this TV", Modifier.weight(1f))
            if (!all.loading) InfoChip(Icons.Rounded.Memory, "${all.rows.size}", color = Tidy.BlueSoft, textColor = Tidy.Ink)
        }
        if (all.loading) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(26.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading every app on your TV…", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted)
            }
            return@ScreenFrame
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search apps", color = Tidy.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Tidy.Muted) },
            trailingIcon = if (query.isEmpty()) null else ({
                IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = Tidy.Muted) }
            }),
            shape = RoundedCornerShape(16.dp),
        )
        MethodChoice(s.uninstall, onUninstall)
        if (n > 0) {
            val save = AllAppsView.savings(s.selected, all.facts)
            ChipRow {
                InfoChip(Icons.Rounded.CheckCircle, "$n picked", color = Tidy.GreenSoft, textColor = Tidy.GreenInk)
                if (save.ramMb > 0) InfoChip(Icons.Rounded.Memory, "~${save.ramMb} MB RAM", color = Tidy.BlueSoft, textColor = Tidy.Ink)
                if (save.storageMb > 0) InfoChip(Icons.Rounded.Storage, "${save.storageMb} MB", color = Tidy.Grey, textColor = Tidy.Ink)
            }
        }
        val searching = query.isNotBlank()
        val sections = AllAppsView.sections(AllAppsView.search(all.rows, query), all.facts)
        if (sections.isEmpty()) Body("No apps match \"${query.trim()}\".")
        for (sec in sections) {
            val open = searching || sec.origin in expanded
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (sec.origin == Origin.YOURS) Icons.Rounded.Person else Icons.Rounded.Tv, null, Modifier.size(20.dp), tint = Tidy.Muted)
                Spacer(Modifier.width(8.dp))
                Text("${sec.title} (${sec.size})", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            }
            sec.removable.forEach { AppRowItem(it, labels, all.facts[it.pkg], it.pkg in s.selected, onToggle) }
            if (sec.more.isNotEmpty()) {
                if (!searching) TextLink(
                    if (open) "Show fewer" else "Show ${sec.more.size} more",
                    { expanded = if (open) expanded - sec.origin else expanded + sec.origin },
                    icon = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                )
                if (open) sec.more.forEach { AppRowItem(it, labels, all.facts[it.pkg], it.pkg in s.selected, onToggle) }
            }
        }
    }
    if (s.confirmPermanent.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onCancelPermanent,
            containerColor = Tidy.Card,
            title = { Text("Uninstall apps you installed?", style = MaterialTheme.typography.titleLarge, color = Tidy.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        s.confirmPermanent.joinToString(", ") { labels[it] ?: all.rows.firstOrNull { r -> r.pkg == it }?.label ?: it },
                        style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 6, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "These came from Google Play or a file. Uninstalling is permanent: Restore can't bring them back, but you can install them again.",
                        style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted,
                    )
                }
            },
            confirmButton = { TextButton(onConfirmPermanent) { Text("Uninstall for good", color = Tidy.Red, style = MaterialTheme.typography.titleMedium) } },
            dismissButton = { TextButton(onCancelPermanent) { Text("Cancel", color = Tidy.Blue, style = MaterialTheme.typography.titleMedium) } },
        )
    }
}

@Composable
private fun MethodChoice(uninstall: Boolean, onUninstall: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f))
            MoreInfo("Turn off or uninstall") {
                Body("Turn off hides the app and stops it. One tap brings it back.", color = Tidy.Ink)
                Body("Uninstall frees storage. Apps that came with the TV stay on its system part, so Restore reinstalls them.", color = Tidy.Ink)
                Body("Apps you installed always uninstall: that's the only way to remove them, and it's permanent.", color = Tidy.Ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard("Turn off", !uninstall, { onUninstall(false) }, Modifier.weight(1f))
            ChoiceCard("Uninstall", uninstall, { onUninstall(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun AppRowItem(row: AppRow, labels: Map<String, String>, facts: AppFacts?, picked: Boolean, onToggle: (String) -> Unit) {
    val name = labels[row.pkg] ?: row.label
    var details by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (details) dev.tidytv.app.ui.AppDetailsSheet(name, row.pkg, row.note) { details = false }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(onLongClickLabel = "Show package ID", onLongClick = { details = true }, onClick = { if (!row.locked) onToggle(row.pkg) })
            .heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.pkg, name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.note.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                if (row.keptByList) {
                    Icon(Icons.Rounded.WarningAmber, null, Modifier.padding(top = 1.dp).size(16.dp), tint = Tidy.AmberInk)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    row.note.removePrefix(if (row.keptByList) "This TV's list keeps it on. " else ""),
                    style = MaterialTheme.typography.labelMedium, color = if (row.keptByList) Tidy.AmberInk else Tidy.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(row.pkg, style = MaterialTheme.typography.labelSmall, color = Tidy.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!row.locked) Badges(row, facts)
        }
        Spacer(Modifier.width(8.dp))
        if (row.locked) Icon(Icons.Rounded.Lock, "Kept safe", Modifier.size(24.dp).padding(end = 0.dp), tint = Tidy.Muted)
        else Checkbox(
            checked = picked, onCheckedChange = { onToggle(row.pkg) },
            colors = CheckboxDefaults.colors(checkedColor = Tidy.Blue),
        )
    }
}

@Composable
private fun Badges(row: AppRow, f: AppFacts?) {
    val storage = f?.let { it.appMb + it.dataMb + it.cacheMb } ?: 0
    val ram = f?.ramMb ?: 0
    val boot = f?.startsAtBoot == true
    val yours = row.origin == Origin.YOURS
    if (ram < 10 && !boot && storage < 20 && !yours) return
    ChipRow(Modifier.padding(top = 4.dp)) {
        if (ram >= 10) InfoChip(Icons.Rounded.Memory, "$ram MB RAM", color = Tidy.BlueSoft, textColor = Tidy.Ink)
        if (boot) InfoChip(Icons.Rounded.PlayArrow, "Starts at boot", color = Tidy.Amber, textColor = Tidy.AmberInk)
        if (storage >= 20) InfoChip(Icons.Rounded.Storage, "$storage MB", color = Tidy.Grey, textColor = Tidy.Ink)
        if (yours) InfoChip(Icons.Rounded.Person, "Yours", color = Tidy.GreenSoft, textColor = Tidy.GreenInk)
    }
}
