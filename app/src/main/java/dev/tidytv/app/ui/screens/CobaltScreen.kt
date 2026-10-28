package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tidytv.app.CobaltUi
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ProgressBar
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar

/** Optional: install TizenTube Cobalt next to the stock YouTube app. Nothing here changes YouTube. */
@Composable
fun CobaltScreen(
    s: CobaltUi,
    tvName: String?,
    onInstall: () -> Unit,
    onInstallWeak: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenPage: () -> Unit,
    onBack: () -> Unit,
) {
    val busy = s is CobaltUi.Downloading || s is CobaltUi.Sending || s is CobaltUi.Checking
    ScreenFrame(
        top = { TopBar(back = "My TV", onBack = onBack, tvName = tvName) },
        bottom = {
            when (s) {
                is CobaltUi.Ready -> BigButton(if (s.alreadyInstalled != null) "Reinstall / update" else "Download and install", onInstall)
                is CobaltUi.Failed -> if (s.retry) BigButton("Try again", onRetry)
                CobaltUi.WeakCheck -> BigButton("Install with the weaker check", onInstallWeak, color = Tidy.Orange)
                is CobaltUi.Installed -> BigButton("Done", onBack)
                else -> Unit
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroMascot(mood = Mood.STAND, height = 90.dp)
            Title("TizenTube Cobalt", align = TextAlign.Center)
            Body(
                "An optional YouTube app for the TV with ad blocking and SponsorBlock, made by the open-source TizenTubeCobalt project. " +
                    "It installs as its own app. Your TV's YouTube is not changed, disabled or replaced.",
                align = TextAlign.Center,
            )
            when (s) {
                CobaltUi.Idle -> Unit
                is CobaltUi.Checking -> Body(s.what + "…", align = TextAlign.Center)
                is CobaltUi.Ready -> {
                    Outline {
                        Body("Latest release ${s.tag}, ${formatBytes(s.sizeBytes)}")
                        Body(s.abiNote)
                        Body("Downloaded from the project's GitHub releases, then checked against the SHA-256 GitHub publishes for that file before anything is sent to the TV.")
                    }
                    Note(s.bridgeNote)
                    s.alreadyInstalled?.let { Body(it) }
                    Note("This is separate from Restore Everything: restoring your TV does not remove or roll back TizenTube Cobalt.")
                    Note("Android may ask you to allow the install on the TV screen. Use the TV remote if it does.")
                }
                is CobaltUi.Downloading -> { ProgressBar(if (s.total > 0) s.done.toFloat() / s.total else 0f); Body("Downloading ${formatBytes(s.done)} of ${formatBytes(s.total)}") }
                is CobaltUi.Sending -> { ProgressBar(if (s.total > 0) s.done.toFloat() / s.total else 0f); Body("Sending to the TV ${formatBytes(s.done)} of ${formatBytes(s.total)}. Keep both devices on.") }
                is CobaltUi.Installed -> {
                    SafetyBanner("TizenTube Cobalt ${s.tag} is installed (${s.pkg}).")
                    Body(if (s.launched) "It opened on the TV." else "It installed but didn't come to the front. Open it from the TV's app list.")
                    Body("File check: the download matched GitHub's SHA-256 and the app's package id and signing certificate matched the project's release. On the TV: ${s.integrity}.")
                    if (s.integrity.startsWith("size only")) Note("This TV can't compute checksums, so this check is weaker than the others.")
                    Body("Restore Everything does not remove Cobalt. To update, run this again. To launch, open it from the TV's apps.")
                    if (s.removable) TextLink("Remove TizenTube Cobalt", onRemove, color = Tidy.Red)
                    else Body("It was already on this TV before, so 0TV updated it but will not remove it. Remove it on the TV under Settings > Apps if you want it gone.")
                }
                CobaltUi.WeakCheck -> Note("Nothing was installed. This TV has no checksum tool, so the file can only be checked by size and by Android's own package and signature check. The download itself was fully checksum-verified on the phone and the app identity matched. Only continue if you accept the weaker check.")
                is CobaltUi.Failed -> Note(s.message)
            }
            if (!busy) TextLink("Project releases on GitHub", onOpenPage, small = true)
        }
    }
}
