package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tidytv.app.CheckState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.MoreInfo
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import dev.tidytv.core.lists.AppLabels

/** Check my TV: compares the TV with what TidyTV turned off, and offers to turn things off again. */
@Composable
fun CheckScreen(
    s: CheckState,
    tvName: String?,
    labels: Map<String, String>,
    onRetidy: () -> Unit,
    onRebootTest: () -> Unit,
    onRecheck: () -> Unit,
    onBack: () -> Unit,
    onTurnOnRemoteHelper: (String) -> Unit = {},
    onRepairHome: () -> Unit = {},
) {
    val report = s.report
    val busy = s.rebooting != null || s.running
    ScreenFrame(
        top = { TopBar(back = "My TV", onBack = onBack, tvName = tvName) },
        bottom = {
            if (s.rebooting == null) {
                if (report?.repairableHome != null) BigButton(
                    if (s.repairingHome) "Putting it back…" else "Put my home screen back", onRepairHome, color = Tidy.Blue, enabled = !s.repairingHome && !busy,
                )
                if (report != null && report.toTidy.isNotEmpty()) {
                    BigButton(
                        if (s.retidying) "Turning off…" else "Turn these off again",
                        onRetidy, color = Tidy.Green, enabled = !s.retidying,
                    )
                }
                if (!busy) {
                    BigButton("Restart the TV and check", onRebootTest, color = Color.White, textColor = Tidy.Ink,
                        leading = { Icon(Icons.Rounded.RestartAlt, null, tint = Tidy.Ink) })
                    Text("Checks it all stays off. About 2 minutes.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
                }
                if (report != null && report.toTidy.isEmpty() && !busy) TextLink("Check again", onRecheck)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Check my TV", Modifier.weight(1f))
            MoreInfo("Check my TV") {
                Body("TV updates sometimes switch apps back on. This checks your TV against what 0TV turned off.", color = Tidy.Ink)
                Body("Restarting checks that everything stays off after a reboot. It takes about 2 minutes.", color = Tidy.Ink)
            }
        }
        if (s.rebooting != null) {
            HeroMascot(mood = Mood.SEARCH, height = 120.dp)
            Bubble(s.rebooting, leading = { Spinner() })
            return@ScreenFrame
        }
        if (s.running) Bubble("Checking your TV…", leading = { Spinner() })
        if (report != null) {
            if (report.allGood) {
                ResultCard(Mood.HAPPY, "All tidy", "${report.stillOff} still off", Tidy.GreenSoft, Tidy.GreenInk)
            } else {
                val back = report.backOn.size + report.newOnList.size
                ResultCard(
                    Mood.WORRIED,
                    if (back == 0) "Home screen changed" else if (back == 1) "1 app to turn off" else "$back apps to turn off",
                    "${report.stillOff} still off", Tidy.Amber, Tidy.AmberInk,
                )
                if (report.backOn.isNotEmpty()) PackageCard("Switched back on", "Usually after a restart or a TV update.", report.backOn, labels)
                if (report.newOnList.isNotEmpty()) PackageCard("New since your last tidy", null, report.newOnList, labels)
                if (report.homeChanged) {
                    Note(if (report.repairableHome != null) "Your TV's own home screen took over again, usually after a restart. Tap Put my home screen back." else "Your TV's old home screen took over again. Open Home screen on My TV to switch back.")
                }
            }
        }
        if (report != null && report.remoteOff.isNotEmpty()) Outline {
            Text("Remote features are off", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            Text("Turned off, so the remote's mic or shortcut buttons may not work. Turn them on to get them back.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pkg in report.remoteOff) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(labels[pkg] ?: dev.tidytv.core.lists.AppLabels.label(pkg), style = MaterialTheme.typography.bodyLarge, color = Tidy.Ink)
                        dev.tidytv.core.lists.AllApps.TRADE_OFFS[pkg]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted) }
                    }
                    if (busy) Spinner() else TextLink("Turn on", { onTurnOnRemoteHelper(pkg) })
                }
            }
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message) }
    }
}

/** The answer first: a mascot, a big line and a small one. */
@Composable
private fun ResultCard(mood: Mood, title: String, sub: String, color: Color, ink: Color) {
    CardBox(color = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) { dev.tidytv.app.ui.HeroMascot(mood = mood) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = ink)
                Text(sub, style = MaterialTheme.typography.titleMedium, color = ink)
            }
        }
    }
}

@Composable
private fun PackageCard(title: String, note: String?, pkgs: List<String>, labels: Map<String, String>) {
    Outline {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
        Spacer(Modifier.heightIn(min = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (pkg in pkgs) {
                val label = labels[pkg] ?: AppLabels.label(pkg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, label, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
