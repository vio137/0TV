package dev.tidytv.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.ChoiceButton
import dev.tidytv.app.ui.ChipRow
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.MoreInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tv
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.NumberedStep
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.core.flow.Mode
import dev.tidytv.core.flow.TvKind

@Composable
fun WelcomeScreen(
    onChoose: (TvKind) -> Unit,
    onRestore: () -> Unit,
    savedTvs: List<dev.tidytv.app.data.KnownTv> = emptyList(),
    onSavedTv: (dev.tidytv.app.data.KnownTv) -> Unit = {},
    onRescue: (dev.tidytv.app.data.KnownTv) -> Unit = {},
    online: Set<String> = emptySet(),
    onConnect: () -> Unit = { onChoose(TvKind.ANDROID_TV) },
) {
    var otherKinds by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    @Composable fun ConnectBlock() {
    BigButton(if (savedTvs.isNotEmpty()) "Connect another TV" else "Connect your TV", onConnect, color = Tidy.Blue,
        leading = { Icon(Icons.Rounded.Tv, null, tint = Color.White) })
    TextLink("Fire TV, Samsung, LG or not sure?", { otherKinds = true }, color = Tidy.Muted, small = true)
    if (otherKinds) dev.tidytv.app.ui.InfoSheet("Which kind of TV?", { otherKinds = false }) {
        ChoiceButton("Android TV / Google TV", Icons.Rounded.Tv, Tidy.Blue) { otherKinds = false; onChoose(TvKind.ANDROID_TV) }
        ChoiceButton("Amazon Fire TV", Icons.Rounded.SettingsInputHdmi, Tidy.Orange, badge = "Clean-up only") { otherKinds = false; onChoose(TvKind.FIRE_TV) }
        ChoiceButton("Samsung or LG", Icons.Rounded.DesktopWindows, Tidy.Purple) { otherKinds = false; onChoose(TvKind.SAMSUNG_LG) }
        ChoiceButton("Not sure", Icons.AutoMirrored.Rounded.HelpOutline, Tidy.Grey, textColor = Tidy.Ink) { otherKinds = false; onChoose(TvKind.NOT_SURE) }
    }
    }
    ScreenFrame(top = {
        val actions = dev.tidytv.app.ui.LocalAppActions.current
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            var about by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            if (about) dev.tidytv.app.ui.AboutSheet({ about = false }, actions.openUrl)
            TextLink("About", { about = true }, color = Tidy.Muted)
            TextLink("Feedback", { actions.feedback(dev.tidytv.app.ui.FeedbackRequest(dev.tidytv.app.data.FeedbackKind.FEATURE, "welcome")) }, color = Tidy.Muted)
            TextLink("Restore a TV", onRestore, icon = Icons.Rounded.Restore)
        }
    }) {
        if (savedTvs.isNotEmpty()) {
            androidx.compose.material3.Text("Your TVs", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                savedTvs.take(5).forEach { tv ->
                    dev.tidytv.app.ui.TvCard(tv.title, savedSubtitle(tv), onClick = { onSavedTv(tv) }, trailing = {
                        val up = tv.host in online
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(10.dp).background(if (up) Tidy.Green else Tidy.Muted.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                    .semantics { contentDescription = if (up) "On the network" else "Not found on the network" },
                            )
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Icon(Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = Tidy.Muted)
                        }
                    })
                }
            }
            ConnectBlock()
            var pick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            TextLink("TV stuck after a change?", { if (savedTvs.size == 1) onRescue(savedTvs[0]) else pick = true }, color = Tidy.Muted)
            if (pick) androidx.compose.material3.AlertDialog(
                onDismissRequest = { pick = false },
                title = { androidx.compose.material3.Text("Which TV is stuck?") },
                text = {
                    Column {
                        savedTvs.forEach { tv ->
                            androidx.compose.material3.TextButton({ pick = false; onRescue(tv) }) { androidx.compose.material3.Text(tv.title) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton({ pick = false }) { androidx.compose.material3.Text("Cancel") } },
            )
            HeroMascot(height = 96.dp)
        } else {
            HeroMascot(height = 120.dp)
            Title("0TV")
            Body("A calmer TV starts here.", color = Tidy.Muted)
            Body("Review preloaded apps and switch them off over Wi-Fi. 0TV saves a backup and offers restore tools. Keep that backup: uninstalling this phone app can remove its recovery history. Permanently removed app data cannot be restored.", color = Tidy.Muted)
        }
        if (savedTvs.isEmpty()) ConnectBlock()
        ChipRow(center = true) {
            InfoChip(Icons.Rounded.Favorite, "Free")
            InfoChip(Icons.Rounded.Shield, "No tracking")
            InfoChip(Icons.Rounded.Backup, "Backup first")
        }
    }
}

@Composable
fun UnsupportedScreen(onBack: () -> Unit) {
    ScreenFrame(bottom = { BigButton("Back", onBack, color = Tidy.Blue) }) {
        HeroMascot(worried = true)
        Title("Samsung and LG aren't supported")
        Body("Samsung TVs run Tizen and LG TVs run webOS. Neither lets a phone switch apps off the way Android TV does, so 0TV can't help with them.")
        Note("If your Samsung or LG has a Chromecast, Fire TV stick or Google TV streamer plugged in, pick that one instead.", color = Tidy.BlueSoft, textColor = Tidy.Ink)
    }
}

@Composable
fun GetReadyScreen(kind: TvKind?, mode: Mode, onReady: () -> Unit) {
    val fire = kind == TvKind.FIRE_TV
    ScreenFrame(bottom = { BigButton("My TV is ready", onReady) }) {
        Title(if (mode == Mode.RESTORE) "Let's reconnect to your TV" else "Get your TV ready")
        Body(if (mode == Mode.RESTORE) "TV on, same Wi-Fi, debugging still on." else "About a minute, and only once.")
        Outline {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (fire) {
                    NumberedStep(1, "Select your Fire TV's name 7 times", "Settings › My Fire TV › About")
                    NumberedStep(2, "Turn on ADB debugging", "My Fire TV › Developer options")
                    NumberedStep(3, "Allow on the TV", "Tick Always allow, then Allow")
                } else {
                    NumberedStep(1, "Select Build 7 times", "Settings › System › About › Android TV OS build")
                    NumberedStep(2, "Turn on USB debugging", "Developer options. Then Wireless debugging too.")
                    NumberedStep(3, "Allow on the TV", "Tick Always allow, then Allow")
                }
            }
        }
        MoreInfo("Need help?", link = "Need help?") {
            Body("Keep your phone on the same home network as the TV.", color = Tidy.Ink)
            Note("Using a stick powered from the TV's USB port? Plug it into its wall adapter for now, so it doesn't switch off halfway.")
            if (!fire) {
                Body("Some TVs call Wireless debugging Network debugging or ADB over network instead. With Wireless debugging, 0TV asks once for the code your TV shows.", color = Tidy.Ink)
                Note(
                    "TV on Ethernet? On most TVs Wireless debugging stays greyed out until the TV is on Wi-Fi. Turn on the TV's Wi-Fi and join the same network as your phone, then turn on Wireless debugging. You can turn Wi-Fi off again when you're done.",
                    color = Tidy.BlueSoft, textColor = Tidy.Ink,
                )
            }
        }
    }
}

/** "TCL 55C735 · 192.168.1.2 · used 2 days ago": enough to tell two TVs of one model apart. */
internal fun savedSubtitle(tv: dev.tidytv.app.data.KnownTv, now: Long = System.currentTimeMillis()): String {
    val mins = ((now - tv.lastSeen) / 60_000).coerceAtLeast(0)
    val ago = when {
        tv.lastSeen <= 1_000_000L -> null
        mins < 2 -> "used just now"
        mins < 60 -> "used $mins min ago"
        mins < 60 * 24 -> "used ${mins / 60} h ago"
        mins < 60 * 24 * 60 -> "used ${mins / (60 * 24)} days ago"
        else -> "used a while ago"
    }
    return listOfNotNull(tv.model.takeIf { it.isNotBlank() && it != tv.title }, tv.host.takeIf { it.isNotBlank() }, ago).joinToString("  ·  ")
}
