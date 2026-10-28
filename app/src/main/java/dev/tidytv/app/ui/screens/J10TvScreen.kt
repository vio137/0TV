package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tidytv.app.J10Ui
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ProgressBar
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar

/** Optional: install J10TV as its own app on the TV. Nothing else on the TV is changed. */
@Composable
fun J10TvScreen(
    s: J10Ui,
    tvName: String?,
    onInstall: () -> Unit,
    onInstallWeak: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenPage: () -> Unit,
    onBack: () -> Unit,
) {
    val busy = s is J10Ui.Downloading || s is J10Ui.Sending || s is J10Ui.Checking
    ScreenFrame(
        top = { TopBar(back = "My TV", onBack = onBack, tvName = tvName) },
        bottom = {
            when (s) {
                is J10Ui.Ready -> dev.tidytv.app.ui.BigButton(if (s.alreadyInstalled != null) "Reinstall / update" else "Download and install", onInstall)
                is J10Ui.Failed -> if (s.retry) dev.tidytv.app.ui.BigButton("Try again", onRetry)
                J10Ui.WeakCheck -> dev.tidytv.app.ui.BigButton("Install with the weaker check", onInstallWeak, color = Tidy.Orange)
                is J10Ui.Installed -> dev.tidytv.app.ui.BigButton("Done", onBack)
                else -> Unit
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            dev.tidytv.app.ui.HeroMascot(mood = dev.tidytv.app.ui.Mood.STAND, height = 90.dp)
            Title("J10TV", align = TextAlign.Center)
            Body(
                "An optional JioTV Go app for the TV, made by the open-source JioTV-Go project. " +
                    "It installs as its own app, next to everything else on your TV. You sign in with your own Jio account inside the app.",
                align = TextAlign.Center,
            )
            when (s) {
                J10Ui.Idle -> Unit
                is J10Ui.Checking -> Body(s.what + "…", align = TextAlign.Center)
                is J10Ui.Ready -> {
                    Outline {
                        Body("Latest release ${s.tag}, ${formatBytes(s.sizeBytes)}")
                        Body(s.abiNote)
                        Body("Downloaded from the project's GitHub releases, then checked against the SHA-256 GitHub publishes for that file before anything is sent to the TV.")
                    }
                    s.alreadyInstalled?.let { Body(it) }
                    Note("This is separate from Restore Everything: restoring your TV does not remove or roll back J10TV.")
                    Note("Android may ask you to allow the install on the TV screen. Use the TV remote if it does.")
                }
                is J10Ui.Downloading -> { ProgressBar(if (s.total > 0) s.done.toFloat() / s.total else 0f); Body("Downloading ${formatBytes(s.done)} of ${formatBytes(s.total)}") }
                is J10Ui.Sending -> { ProgressBar(if (s.total > 0) s.done.toFloat() / s.total else 0f); Body("Sending to the TV ${formatBytes(s.done)} of ${formatBytes(s.total)}. Keep both devices on.") }
                is J10Ui.Installed -> {
                    SafetyBanner("J10TV ${s.tag} is installed (${s.pkg}).")
                    Body(if (s.launched) "It opened on the TV." else "It installed but didn't come to the front. Open it from the TV's app list.")
                    Body("File check: the download matched GitHub's SHA-256 and the app's package id and signing certificate matched the project's release. On the TV: ${s.integrity}.")
                    if (s.integrity.startsWith("size only")) Note("This TV can't compute checksums, so this check is weaker than the others.")
                    Body("Restore Everything does not remove J10TV. To update, run this again. To launch, open it from the TV's apps.")
                    if (s.removable) TextLink("Remove J10TV", onRemove, color = Tidy.Red)
                    else Body("It was already on this TV before, so 0TV updated it but will not remove it. Remove it on the TV under Settings > Apps if you want it gone.")
                }
                J10Ui.WeakCheck -> Note("Nothing was installed. This TV has no checksum tool, so the file can only be checked by size and by Android's own package and signature check. The download itself was fully checksum-verified on the phone and the app identity matched. Only continue if you accept the weaker check.")
                is J10Ui.Failed -> Note(s.message)
            }
            if (!busy) TextLink("Project releases on GitHub", onOpenPage, small = true)
        }
    }
}
