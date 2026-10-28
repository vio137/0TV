package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.unit.dp
import dev.tidytv.app.RescueState
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.IconBadge
import dev.tidytv.app.ui.MoreInfo
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wifi
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.NumberedStep
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TvCard

/** Boot-loop rescue: keep trying a saved TV and put everything back as soon as it answers. */
@Composable
fun RescueScreen(
    s: RescueState,
    onStop: () -> Unit,
    onTryAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val result = s.result
    val trying = result == null && !s.gaveUp && !s.restoring
    ScreenFrame(
        top = { TextLink("Back", onStop, icon = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack) },
        bottom = {
            when {
                result != null -> {
                    BigButton("Done", onDone)
                    if (!result.ok) TextLink("Try again", onTryAgain)
                }
                s.gaveUp -> {
                    BigButton("Try again", onTryAgain)
                    TextLink("Back", onStop)
                }
                trying -> TextLink("Stop trying", onStop)
            }
        },
    ) {
        Title("TV stuck after a change?")
        if (s.tv != null) TvCard(s.tv.title, s.tv.model)
        when {
            result != null -> {
                HeroMascot(mood = if (result.ok) Mood.CHEER else Mood.WORRIED, height = 120.dp)
                if (result.ok) SafetyBanner(result.message)
                else Note(result.message)
            }
            s.gaveUp -> {
                HeroMascot(mood = Mood.WORRIED, height = 120.dp)
                Note("Your TV didn't answer for 10 minutes. Check it's on and on the same network, then try again.")
            }
            s.restoring -> Bubble("Your TV answered. Turning everything back on…", leading = { Spinner() })
            else -> {
                HeroMascot(mood = Mood.SEARCH, height = 120.dp)
                Bubble("Trying to reach your TV… attempt ${s.attempts}", leading = { Spinner() })
                StepCard(Icons.Rounded.PowerSettingsNew, Tidy.Orange, "Unplug the TV for 10 seconds")
                StepCard(Icons.Rounded.Wifi, Tidy.Blue, "Keep your phone on the same Wi-Fi")
                StepCard(Icons.Rounded.Restore, Tidy.Green, "0TV puts everything back")
                MoreInfo("TV stuck after a change?", link = "More about this") {
                    NumberedStep(1, "Turn the TV off at the wall, wait 10 seconds, and turn it on again.")
                    NumberedStep(2, "Keep your phone on the same network.")
                    NumberedStep(3, "As soon as the TV answers, 0TV turns everything it changed back on.")
                }
            }
        }
    }
}

@Composable
private fun StepCard(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, text: String) {
    Outline {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconBadge(icon, color, 44.dp)
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(14.dp))
            androidx.compose.material3.Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        }
    }
}
