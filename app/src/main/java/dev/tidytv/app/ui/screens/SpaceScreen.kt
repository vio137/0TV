package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidytv.app.SpaceState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.MoreInfo
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar

private fun gb(mb: Int) = String.format(java.util.Locale.US, "%.1f", mb / 1024.0)

/** Free up space: how full the TV is, which apps hold the most cache, and one button to clear it all. */
@Composable
fun SpaceScreen(s: SpaceState, tvName: String?, labels: Map<String, String>, onClear: () -> Unit, onBack: () -> Unit) {
    ScreenFrame(
        top = { TopBar("My TV", onBack, tvName) },
        bottom = {
            if (!s.loading) BigButton(
                if (s.clearing) (if (s.progressMb > 0) "Freed ${s.progressMb} MB…" else "Clearing…") else "Clear all caches", onClear, color = Tidy.Green, enabled = !s.clearing,
                leading = if (s.clearing) ({ Spinner(22.dp, Color.White) }) else null,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Free up space", Modifier.weight(1f))
            MoreInfo("Caches") {
                Body("Caches are temporary files apps keep to load faster. Clearing them is safe: apps rebuild what they need. Your sign-ins, settings and downloads stay.", color = Tidy.Ink)
            }
        }
        if (s.loading) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(26.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading your TV's storage…", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted)
            }
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = Tidy.RedSoft, textColor = Tidy.Red) }
        val st = s.storage
        if (!s.loading && st != null) {
            CardBox(color = Tidy.BlueSoft) {
                val free = st.freeMb
                val total = st.totalMb
                if (free != null && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)),
                        color = Tidy.Blue, trackColor = Tidy.Grey,
                    )
                    Text("${gb(free)} GB free of ${gb(total)} GB", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.padding(top = 10.dp))
                } else Text("Storage size unknown", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                Row(Modifier.padding(top = 10.dp)) {
                    // Android counts app caches only about once a day, so before a clear this is an estimate.
                    InfoChip(Icons.Rounded.Storage, if (s.estimated) "Caches: about ${st.cacheMb} MB" else "Caches: cleared", color = Color.White, textColor = Tidy.Ink)
                }
                if (s.clearing) Text("Android is clearing caches in the background. The free space above updates as it goes.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (!s.loading && s.topCache.isNotEmpty()) Outline {
            Text("Biggest caches", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            Text("As of Android's last count, which it refreshes about once a day.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((pkg, mb) in s.topCache) {
                    val name = labels[pkg] ?: dev.tidytv.core.lists.AppLabels.label(pkg)
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, color = Tidy.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("$mb MB", style = MaterialTheme.typography.titleMedium, color = Tidy.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}
