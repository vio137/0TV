package dev.tidytv.app.ui.screens

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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidytv.app.AppsState
import dev.tidytv.app.ui.AppDetailsSheet
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import dev.tidytv.app.ui.InfoSheet
import dev.tidytv.core.lists.AllAppsView
import dev.tidytv.core.lists.AppRow
import dev.tidytv.core.lists.Tier

/** Apps: every app on the TV. Tap one to turn it off or uninstall it right away, with the warning that goes with it. */
@Composable
fun AppsScreen(
    s: AppsState,
    labels: Map<String, String>,
    tvName: String?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onTurnOff: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ScreenFrame(top = { TopBar("My TV", onBack, tvName) }) {
        Title("Apps")
        Body("Tap an app to turn it off or uninstall it on your TV. A backup is saved first and Restore puts things back.", color = Tidy.Muted)
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = Tidy.RedSoft, textColor = Tidy.Red) }
        if (s.loading) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(26.dp); Spacer(Modifier.width(12.dp))
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
        val sections = AllAppsView.sections(AllAppsView.search(s.rows, query), s.facts)
        if (sections.isEmpty()) Body("No apps match \"${query.trim()}\".")
        for (sec in sections) {
            Text("${sec.title} (${sec.size})", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.padding(top = 4.dp))
            for (row in sec.all) AppLine(row, labels[row.pkg] ?: row.label, s.busy == row.pkg) { onOpen(row.pkg) }
        }
    }
    val open = s.open?.let { pkg -> s.rows.firstOrNull { it.pkg == pkg } }
    if (open != null) AppActionSheet(open, labels[open.pkg] ?: open.label, onClose, onTurnOff, onUninstall)
}

@Composable
private fun AppLine(row: AppRow, name: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.pkg, name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.pkg, style = MaterialTheme.typography.labelSmall, color = Tidy.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            busy -> Spinner(22.dp)
            row.locked -> Icon(Icons.Rounded.Lock, "Kept safe", Modifier.size(22.dp), tint = Tidy.Muted)
            else -> Icon(Icons.Rounded.ChevronRight, null, Modifier.size(26.dp), tint = Tidy.Muted)
        }
    }
}

/** The warning for this app, then the two choices. Locked apps only say why they stay. */
@Composable
private fun AppActionSheet(row: AppRow, name: String, onClose: () -> Unit, onTurnOff: (String) -> Unit, onUninstall: (String) -> Unit) {
    if (row.locked) { AppDetailsSheet(name, row.pkg, row.note, onClose); return }
    val yours = row.tier == Tier.YOURS
    InfoSheet(name, onClose) {
        Text(row.pkg, style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
        if (row.note.isNotBlank()) Body(row.note.removePrefix("This TV's list keeps it on. "), color = Tidy.Ink)
        when {
            yours -> Note("You installed this app. Uninstalling is permanent: Restore can't bring it back, but you can install it again from Google Play.")
            row.keptByList || row.tier == Tier.YOUR_CALL -> Note("Think first: this app may be used by your TV or remote. Turning it off is easy to undo.", color = Tidy.Amber, textColor = Tidy.AmberInk)
            else -> Body("Turning it off hides it and stops it from running. One tap brings it back.", color = Tidy.Muted)
        }
        if (!yours) BigButton("Turn off", { onTurnOff(row.pkg) }, color = Tidy.Green)
        BigButton(if (yours) "Uninstall for good" else "Uninstall for this TV", { onUninstall(row.pkg) }, color = if (yours) Tidy.Red else Color.White, textColor = if (yours) Color.White else Tidy.Ink)
    }
}
