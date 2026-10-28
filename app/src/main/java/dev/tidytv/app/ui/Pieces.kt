package dev.tidytv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Status in two or three words: an icon and a label in a pill ("Backup saved", "Never deletes"). */
@Composable
fun InfoChip(icon: ImageVector, text: String, color: Color = Tidy.GreenSoft, textColor: Color = Tidy.GreenInk) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(if (color.luminance() > 0.8f) Tidy.BlueSoft else color).heightIn(min = 32.dp)
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tc = if (color.luminance() > 0.8f) Tidy.Ink else textColor
        Icon(icon, null, Modifier.size(18.dp), tint = tc)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tc)
    }
}

/** A row of chips that wraps on narrow phones and large text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, center: Boolean = false, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (center) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A drawn icon on a coloured rounded square, for group headers and step cards. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 36.dp, tint: Color = Color.White) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.58f), tint = tint)
    }
}

/**
 * The long explanation that stays available, one tap away: a small (i) (or a text link with one)
 * that opens a bottom sheet. Keeps screens to a title and one line.
 */
@Composable
fun MoreInfo(title: String, link: String? = null, color: Color = Tidy.Blue, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (link == null) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "More about $title" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = color) }
    } else {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = true }
                .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(link, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
    if (open) InfoSheet(title, { open = false }, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tidy.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Title(title)
            content()
            BigButton("Got it", onDismiss)
        }
    }
}

/**
 * One choice among a few: a big preview (or icon), a short name, one tag and a check when selected.
 * The whole card is the tap target.
 */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    line: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .border(if (selected) 3.dp else 2.dp, if (selected) Tidy.Blue else Tidy.Line, shape)
            .background(if (selected) Tidy.BlueSoft else Tidy.Card)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (preview != null) preview()
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconBadge(icon, Tidy.Blue, 40.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tag != null) Text(
                    tag, style = MaterialTheme.typography.labelMedium,
                    color = if (tag.startsWith("Not tested")) Tidy.AmberInk else Tidy.GreenInk,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(99.dp))
                        .background(if (tag.startsWith("Not tested")) androidx.compose.ui.graphics.Color(0x33FFC98A) else Tidy.GreenSoft)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "Selected", Modifier.size(28.dp), tint = Tidy.Blue)
        }
    }
}


/** What an app is, for the person deciding: its name, the package ID to look up (tap to copy) and what we know about it. */
@Composable
fun AppDetailsSheet(label: String, pkg: String, note: String, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    InfoSheet(label, onDismiss) {
        Text("Package ID", style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
        Text(pkg, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        TextLink(if (copied) "Copied" else "Copy package ID", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(pkg)); copied = true })
        if (note.isNotBlank()) Body(note, color = Tidy.Ink)
        Body("Search the package ID online to see what the app does before you turn it off.", color = Tidy.Muted)
    }
}
