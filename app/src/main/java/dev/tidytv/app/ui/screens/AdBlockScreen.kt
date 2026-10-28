package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tidytv.app.AdsState
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import dev.tidytv.core.ops.AdBlockInfo
import dev.tidytv.core.ops.AdBlockProvider

/** Block ads: Private DNS with AdGuard, and a plain list of what that does and doesn't stop. */
@Composable
fun AdBlockScreen(
    s: AdsState,
    tvName: String?,
    onBlock: (AdBlockProvider, Boolean) -> Unit,
    onCustom: (String, Boolean) -> String?,
    onRefresh: () -> Unit,
    onUnblock: () -> Unit,
    onCancelReplace: () -> Unit,
    onBack: () -> Unit,
) {
    val on = s.dns?.pinned == true
    var host by androidx.compose.runtime.saveable.rememberSaveable(s.lastCustom, s.dns?.host) { androidx.compose.runtime.mutableStateOf(s.dns?.takeIf { it.custom }?.host ?: s.lastCustom ?: "") }
    var hostError by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    ScreenFrame(
        top = { TopBar("My TV", onBack, tvName) },
        bottom = {
            if (!s.loading && s.supported) {
                if (on) BigButton(if (s.busy) "Turning off…" else "Turn off ad blocking", onUnblock, color = Color.White, textColor = Tidy.Ink, enabled = !s.busy)
                else BigButton(if (s.busy) "Turning on…" else "Block ads and trackers", { onBlock(AdBlockProvider.ADS, false) }, color = Tidy.Green, enabled = !s.busy)
            }
        },
    ) {
        Title("Block ads")
        if (s.loading) Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner(26.dp); Spacer(Modifier.width(12.dp))
            Text("Checking your TV…", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted)
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = Tidy.RedSoft, textColor = Tidy.Red) }
        if (!s.supported) {
            Note("This TV is too old for ad blocking. It needs Android 9 or newer.")
            return@ScreenFrame
        }
        if (s.loading) return@ScreenFrame
        // What the TV reports right now, read straight from its settings.
        if (s.dns == null) {
            Note("Couldn't read this TV's DNS setting.", color = Tidy.RedSoft, textColor = Tidy.Red)
        } else {
            val d = s.dns
            if (d.pinned) SafetyBanner(d.headline())
            else Note(d.headline(), color = Tidy.Grey, textColor = Tidy.Ink)
            Text("Read from the TV: mode ${d.mode ?: "not set"}${d.host?.let { ", server $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
        }
        dev.tidytv.app.ui.TextLink(if (s.busy) "Working…" else "Check the TV again", onRefresh, small = true)
        if (s.confirmReplace != null || s.confirmReplaceHost != null) {
            Outline {
                Text("Replace the DNS this TV already uses (${s.dns?.host})? Turning this off puts it back.", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Replace it", { s.confirmReplace?.let { onBlock(it, true) } ?: s.confirmReplaceHost?.let { onCustom(it, true) } }, Modifier.weight(1f), color = Tidy.Green)
                    BigButton("Keep it", onCancelReplace, Modifier.weight(1f), color = Color.White, textColor = Tidy.Ink)
                }
            }
        }
        if (!on) Note(AdBlockInfo.DISCLOSURE)
        CardBox(color = Tidy.GreenSoft) {
            Text("What it can block", style = MaterialTheme.typography.titleMedium, color = Tidy.GreenInk)
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdBlockInfo.BLOCKS.forEach { Text("✓  $it", style = MaterialTheme.typography.bodyLarge, color = Tidy.Ink) }
            }
        }
        CardBox(color = Tidy.Amber) {
            Text("What it does not block", style = MaterialTheme.typography.titleMedium, color = Tidy.AmberInk)
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdBlockInfo.DOES_NOT_BLOCK.forEach { Text("✗  $it", style = MaterialTheme.typography.bodyLarge, color = Tidy.Ink) }
            }
            Text(AdBlockInfo.YOUTUBE_WARNING, style = MaterialTheme.typography.titleMedium, color = Tidy.AmberInk, modifier = Modifier.padding(top = 10.dp))
        }
        Body(AdBlockInfo.SIDE_EFFECT, color = Tidy.Muted)
        if (s.dns?.provider != AdBlockProvider.FAMILY && !s.busy) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Also block adult sites?", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
            dev.tidytv.app.ui.TextLink("Use the family version", { onBlock(AdBlockProvider.FAMILY, false) })
        }
        CardBox {
            Text("Use your own DNS-over-TLS server", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            Body("Type the server name your DNS provider gave you, like dns.example.com. No https:// and no port: Android uses port 853.", color = Tidy.Muted)
            androidx.compose.material3.OutlinedTextField(
                host, { host = it; hostError = null }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                label = { Text("Server name") }, placeholder = { Text("dns.example.com", color = Tidy.Muted) },
                isError = hostError != null, enabled = !s.busy,
                supportingText = hostError?.let { e -> @Composable { Text(e, color = Tidy.Red) } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            val updating = s.dns?.custom == true
            BigButton(if (s.busy) "Working…" else if (updating) "Update my server" else "Use my server", { hostError = onCustom(host, false) }, Modifier.padding(top = 8.dp), enabled = !s.busy && host.isNotBlank())
        }
    }
}
