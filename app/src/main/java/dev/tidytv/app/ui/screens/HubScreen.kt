package dev.tidytv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tidytv.app.ui.enterRise
import dev.tidytv.app.ui.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import dev.tidytv.app.TvSummary
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.TvCard

private class HubItem(val icon: ImageVector, val title: String, val bg: Color, val fg: Color, val badge: String? = null, val onClick: () -> Unit)

/** The My TV hub: which TV and a tile per feature. How tidy it is sits on the Tidy tile. */
@Composable
fun HubScreen(
    tv: TvSummary?,
    appsOff: Int,
    fireTv: Boolean,
    onTidy: () -> Unit,
    onRemote: () -> Unit,
    onFiles: () -> Unit,
    onHomeScreen: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    onCheck: () -> Unit = {},
    onSpace: () -> Unit = {},
    onAds: () -> Unit = {},
    onApps: () -> Unit = {},
    onCobalt: () -> Unit = {},
    onApp2Tv: () -> Unit = {},
    facts: dev.tidytv.app.HubFacts = dev.tidytv.app.HubFacts(),
    preview: dev.tidytv.app.TvPreview = dev.tidytv.app.TvPreview(),
) {
    val items = buildList {
        add(HubItem(Icons.Rounded.CleaningServices, "Tidy up", Tidy.Blue, Color.White, if (appsOff > 0) (if (appsOff == 1) "1 app switched off so far" else "$appsOff apps switched off so far") else null, onTidy))
        add(HubItem(Icons.Rounded.SettingsRemote, "Remote", Tidy.Purple, Color.White, onClick = onRemote))
        add(HubItem(Icons.Rounded.Upload, "Send files", Tidy.Orange, Color.White, onClick = onFiles))
        if (!fireTv) add(HubItem(Icons.Rounded.Home, "Home screen", Tidy.Green, Color.White, onClick = onHomeScreen))
        if (!fireTv) add(HubItem(Icons.Rounded.PlayCircle, "YouTube+", Tidy.Orange, Color.White, onClick = onCobalt))
        add(HubItem(Icons.Rounded.PhoneAndroid, "App2TV", Tidy.Purple, Color.White, onClick = onApp2Tv))
        add(HubItem(Icons.Rounded.Apps, "Apps", Tidy.Blue, Color.White, onClick = onApps))
        add(HubItem(Icons.Rounded.TaskAlt, "Check my TV", Tidy.Yellow, Tidy.Ink, onClick = onCheck))
        add(HubItem(Icons.Rounded.Storage, "Free up space", Tidy.Green, Color.White, onClick = onSpace))
        add(HubItem(Icons.Rounded.Shield, "Block ads", Tidy.Purple, Color.White, onClick = onAds))
        add(HubItem(Icons.Rounded.Restore, "Restore", Tidy.Grey, Tidy.Ink, onClick = onRestore))
    }
    val featured = items.first()
    val rest = items.drop(1)
    ScreenFrame(top = { Row(Modifier.fillMaxWidth()) { TextLink("Change TV", onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack) } }) {
        if (tv != null) TvCard(tv.title, tv.model, preview = preview)
        if (facts.any) dev.tidytv.app.ui.ChipRow(center = false) {
            facts.freeStorageMb?.let { dev.tidytv.app.ui.InfoChip(Icons.Rounded.Storage, "${gbOrMb(it)} free", color = Tidy.BlueSoft, textColor = Tidy.Ink) }
            facts.freeRamMb?.let { dev.tidytv.app.ui.InfoChip(Icons.Rounded.Memory, "${gbOrMb(it)} RAM free", color = Tidy.GreenSoft, textColor = Tidy.GreenInk) }
        }
        Box(Modifier.enterRise(40)) { FeaturedTile(featured) }
        rest.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).enterRise(110 + rowIndex * 55), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { GridTile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The one primary action of the hub: a wide card with the accent gradient. */
@Composable
private fun FeaturedTile(item: HubItem) {
    val shape = RoundedCornerShape(22.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressScale(src).clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Tidy.Blue, Color(0xFF1B58C9), Tidy.Aqua.copy(alpha = 0.85f))))
            .semantics { contentDescription = if (item.badge != null) "${item.title}, ${item.badge}" else item.title }
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = item.onClick)
            .heightIn(min = 92.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, Modifier.size(28.dp), tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(if (item.badge != null) item.badge else "Back up, review, then switch apps off", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(26.dp), tint = Color.White)
    }
}

/** A secondary feature: icon on a quiet plate, title underneath, same radius and border everywhere. */
@Composable
private fun GridTile(item: HubItem, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.pressScale(src).clip(shape).background(Tidy.Backdrop).border(1.dp, Tidy.Line, shape)
            .semantics { contentDescription = if (item.badge != null) "${item.title}, ${item.badge}" else item.title }
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = item.onClick)
            .heightIn(min = 104.dp).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Tidy.BlueSoft), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, Modifier.size(22.dp), tint = Tidy.Aqua)
            }
            if (item.badge != null) Text(item.badge, style = MaterialTheme.typography.labelMedium, color = Tidy.GreenInk, maxLines = 1)
        }
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.padding(top = 10.dp))
    }
}

private fun gbOrMb(mb: Int) = if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"
