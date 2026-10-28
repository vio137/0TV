package dev.tidytv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import dev.tidytv.app.HomeAction
import dev.tidytv.app.HomeState
import dev.tidytv.core.lists.HomeApp
import dev.tidytv.app.TidyState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.ChoiceCard
import dev.tidytv.app.ui.InfoChip
import dev.tidytv.app.ui.MoreInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ProgressBar
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.core.ops.PkgResult

@Composable
fun TidyingScreen(s: TidyState, onRestore: () -> Unit, onContinue: () -> Unit, onShareLogs: () -> Unit) {
    val stopped = s.stopReason != null
    ScreenFrame(bottom = if (!stopped) null else { {
        BigButton("Restore everything", onRestore, color = Tidy.Green, leading = { Icon(Icons.Rounded.Restore, null, tint = Color.White) })
        TextLink("Keep what was done and continue", onContinue)
        androidx.compose.foundation.layout.Row { TextLink("Share logs", onShareLogs); dev.tidytv.app.ui.ReportProblemLink("tidy") }
    } }) {
        if (stopped) {
            HeroMascot(worried = true)
            Title("0TV stopped to be safe")
            Note(s.stopReason!!)
            Body("Your TV is fine. ${s.disabledCount} apps were turned off before it stopped; Restore everything turns them back on.")
        } else {
            Bubble(if (s.saving) "Saving, keep the TV on" else "Tidying up…") { Spinner(26.dp) }
            ProgressBar(if (s.order.isEmpty()) 0f else s.results.size / s.order.size.toFloat(), Tidy.Green)
            if (s.reconnecting) Note("Wi-Fi dropped. Reconnecting and carrying on where it left off…", color = Tidy.BlueSoft, textColor = Tidy.Ink)
        }
        Outline {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in s.order) Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(c.pkg, c.label, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.label, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f))
                    Status(s.results[c.pkg], running = !stopped && !s.finished)
                }
            }
        }
    }
}

@Composable
private fun Status(r: PkgResult?, running: Boolean) {
    when (r) {
        is PkgResult.Disabled -> Check(Tidy.Green, 26.dp)
        is PkgResult.Removed -> Small(if (r.yours) "uninstalled" else "uninstalled for this TV", Tidy.GreenInk)
        is PkgResult.AlreadyOff -> Small("already off")
        is PkgResult.NotInstalled -> Small("not on this TV")
        is PkgResult.Skipped -> Small("kept on")
        is PkgResult.Failed -> Small("didn't turn off", Tidy.Red)
        null -> if (running) Spinner(20.dp, Tidy.Line) else Small("not touched")
    }
}

@Composable
private fun Small(t: String, color: androidx.compose.ui.graphics.Color = Tidy.Muted) =
    Text(t, style = MaterialTheme.typography.labelMedium, color = color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreenStep(
    s: HomeState,
    disabledCount: Int,
    onToggle: (String) -> Unit,
    onChoose: (HomeApp) -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onAction: (HomeAction, Boolean) -> Unit,
    onContinue: () -> Unit,
    onShareLogs: () -> Unit,
    onCancelPlayStore: () -> Unit = {},
    onOpenLook: () -> Unit = {},
    onCloseLook: () -> Unit = {},
    onApplyLook: (dev.tidytv.app.LookId) -> Unit = {},
    onApplyPhoto: (dev.tidytv.app.PickedFile) -> Unit = {},
) {
    when (s) {
        is HomeState.Offer -> if (s.untested) ScreenFrame(bottom = { BigButton("OK", onSkip) }) {
            if (disabledCount > 0) InfoChip(Icons.Rounded.CheckCircle, "$disabledCount apps turned off")
            UntestedHomeCard()
        } else ScreenFrame(bottom = {
            BigButton("Use ${s.app.title}", onInstall)
            TextLink("No thanks, keep my home screen", onSkip, color = Tidy.Muted)
        }) {
            if (disabledCount > 0) InfoChip(Icons.Rounded.CheckCircle, "$disabledCount apps turned off")
            if (s.styledIsHome) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dock home screen is on", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.weight(1f))
                TextLink("Change the look", onOpenLook, icon = Icons.Rounded.Palette)
            }
            Title("Want a cleaner home screen?")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Just your apps, no ads.", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted, modifier = Modifier.weight(1f))
                MoreInfo("Home screens") {
                    Body("No ads and no rows of suggestions. Your old home screen comes back with Restore everything.", color = Tidy.Ink)
                    for (app in s.available) {
                        Text(app.title, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                        Body(app.blurb)
                    }
                }
            }
            InfoChip(Icons.Rounded.CheckCircle, "Selected: ${s.app.title}", color = Tidy.BlueSoft, textColor = Tidy.Ink)
            for (app in s.available) ChoiceCard(
                app.title, selected = app == s.app, onClick = { onChoose(app) },
                tag = app.tag, line = app.benefit, preview = { HomePreview(app) },
            )
            if (s.options.isNotEmpty() && s.app != HomeApp.PROJECTIVY) Outline {
                Text("Apps on your home screen", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (o in s.options) AppChipLayout(o.pkg, o.label, o.pkg in s.chosen) { onToggle(o.pkg) }
                }
            }
        }
        is HomeState.WaitingForPlayStore -> ScreenFrame(bottom = {
            TextLink("Choose another home screen", onCancelPlayStore)
        }) {
            HeroMascot(mood = Mood.SEARCH, height = 130.dp)
            Title("Press Install on your TV")
            Body("Use the remote to press Install. 0TV carries on by itself.")
            Bubble("Waiting for the install…") { Spinner(26.dp) }
        }
        HomeState.Running -> ScreenFrame {
            HeroMascot()
            Bubble("Setting up your new home screen…") { Spinner(26.dp) }
            Body("About 30 seconds. Please don't press the remote.")
        }
        is HomeState.Look -> LookScreen(s.look, onCloseLook, onApplyLook, onApplyPhoto)
        is HomeState.Installed -> ScreenFrame(bottom = {
            if (s.app == HomeApp.STYLED || s.app == HomeApp.PREMIUM) BigButton("Pick a look", onOpenLook, color = Tidy.BlueSoft, textColor = Tidy.Ink)
            BigButton("Continue", onContinue)
        }) {
            HeroMascot(mood = Mood.HAPPY)
            Bubble("New home screen is on", color = Tidy.GreenSoft, textColor = Tidy.GreenInk) { Check(Tidy.Green, 30.dp) }
            HomePreview(s.app)
            Body(s.app.tip)
            Body("Some TVs go back to their old home screen after a restart. If yours does, open Check my TV and tap Put my home screen back.", color = Tidy.Muted)
        }
        is HomeState.Problem -> {
            var ethernet by rememberSaveable { mutableStateOf(false) }
            ScreenFrame(bottom = {
                when (s.action) {
                    HomeAction.TURN_OFF_SETUP_APP -> BigButton("Try again", { onAction(HomeAction.TURN_OFF_SETUP_APP, ethernet) })
                    HomeAction.RETRY -> BigButton("Try again", { onAction(HomeAction.RETRY, false) })
                    null -> Unit
                }
                TextLink(if (s.action == null) "Continue" else "Skip, keep my home screen", onContinue)
                androidx.compose.foundation.layout.Row { TextLink("Share logs", onShareLogs); if (s.worried) dev.tidytv.app.ui.ReportProblemLink("tidy") }
            }) {
                HeroMascot(worried = s.worried)
                Title(if (s.worried) "Something needs a look" else "Home screen not changed")
                Body(s.message)
                if (s.action == HomeAction.TURN_OFF_SETUP_APP) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ethernet, { ethernet = it })
                    Text("My TV uses a network cable, not Wi-Fi", style = MaterialTheme.typography.bodyLarge, color = Tidy.Ink)
                }
                if (s.rescue.isNotEmpty()) Outline {
                    Text("If the TV looks wrong, these commands put it back (from a computer):", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
                    for (c in s.rescue) Text(c, style = MaterialTheme.typography.labelMedium, color = Tidy.Ink)
                }
            }
        }
    }
}

/** How each home screen is named and described in the app. */
val HomeApp.title: String get() = when (this) {
    HomeApp.SIMPLE -> "Simple"
    HomeApp.STYLED -> "Original dock (author build)"
    HomeApp.PREMIUM -> "0TV Home"
    HomeApp.PROJECTIVY -> "Projectivy"
}

/** One line on what it's like, for its card. */
private val HomeApp.benefit: String get() = when (this) {
    HomeApp.SIMPLE -> "Calm, just your apps"
    HomeApp.STYLED -> "A dock and a big clock"
    HomeApp.PREMIUM -> "Quiet dock, uniform tiles"
    HomeApp.PROJECTIVY -> "Rows you arrange yourself"
}

private val HomeApp.tag: String? get() = null

private val HomeApp.blurb: String get() = when (this) {
    HomeApp.SIMPLE -> "One calm screen with your apps, your HDMI inputs and a clock. Tiny (37 KB). Move apps around on the TV with the remote."
    HomeApp.STYLED -> "The original open-source dock launcher, unchanged and signed by its author (not by 0TV): a dock of your favourite apps with a glass highlight, a big clock and an inputs menu. About 480 KB, Android 8 or newer."
    HomeApp.PREMIUM -> "A restrained dock with uniform app tiles, names under every tile and a quiet focus rim. Installs beside any other home screen; your current one is not removed."
    HomeApp.PROJECTIVY -> "A popular free launcher with rows you arrange yourself. Installed from the Play Store on your TV, so you always get the latest version."
}

private val HomeApp.preview: Int? get() = when (this) {
    HomeApp.SIMPLE -> dev.tidytv.app.R.drawable.home_simple
    HomeApp.STYLED -> dev.tidytv.app.R.drawable.home_styled
    HomeApp.PREMIUM -> dev.tidytv.app.R.drawable.home_premium
    HomeApp.PROJECTIVY -> dev.tidytv.app.R.drawable.home_projectivy
}

val HomeApp.tip: String get() = when (this) {
    HomeApp.SIMPLE -> "Press Home on your remote to see it. Hold OK (or press Menu) on an app to move, replace or remove it. The Add app tile adds more."
    HomeApp.STYLED -> "Press Home on your remote to see it. Hold OK on an app to move or remove it, and use the App library button to add apps back. Hold OK on Settings for more options."
    HomeApp.PREMIUM -> "Press Home on your remote to see it. Hold OK on an app to move or remove it. Use Pick a look to change the wallpaper."
    HomeApp.PROJECTIVY -> "Press Home on your remote to see it. Change its look in Projectivy's own settings."
}

@Composable
private fun HomePreview(app: HomeApp) {
    val res = app.preview
    if (res != null) androidx.compose.foundation.layout.Box {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), "The ${app.title} home screen",
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        if (app == HomeApp.PROJECTIVY) Text(
            "Screenshot: Projectivy Launcher by Spocky, from its Google Play page",
            style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    } else Column(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0xFF2B2F3A)).padding(12.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val premium = app == HomeApp.PREMIUM
        Text(if (premium) "0TV Home" else "Projectivy Launcher", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
        Text(if (premium) "No preview image yet." else "from Google Play on your TV", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
    }
}

/** The TV's home app hasn't been tested with TidyTV, so no home screen change is offered. */
@Composable
fun UntestedHomeCard() {
    CardBox(color = Tidy.Grey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Home, null, tint = Tidy.Muted)
            Spacer(Modifier.width(10.dp))
            Text("Your TV's home screen isn't supported yet", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        }
    }
}
