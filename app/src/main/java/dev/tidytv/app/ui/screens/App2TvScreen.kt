package dev.tidytv.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.tidytv.app.App2TvState
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.CardBox
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ProgressBar
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import dev.tidytv.core.ops.App2Tv

/**
 * App2TV: take an app that is installed on this phone and put it on the TV. The screen says what it
 * is for and how it works, lists the phone's apps, and for the chosen one shows what the TV said
 * before anything is sent, then the plain-English result.
 */
@Composable
fun App2TvScreen(
    s: App2TvState,
    tvName: String?,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val app = s.selected
    ScreenFrame(
        top = { TopBar(back = if (app == null) "My TV" else "All apps", onBack = if (app == null) onBack else onClose, tvName = tvName) },
        bottom = if (app != null) { { SendButton(s, onSend) } } else null,
    ) {
        if (app == null) AppPicker(s, onQuery, onPick) else AppDetail(s)
    }
}

@Composable
private fun SendButton(s: App2TvState, onSend: () -> Unit) {
    val ok = s.verdict is App2Tv.Verdict.Ok
    val done = s.resultText != null && s.resultOk
    BigButton(
        when { s.busy -> "Sending…"; done -> "Send again"; s.verdict == null -> "Checking the TV…"; else -> "Send to my TV" },
        onSend, enabled = ok && !s.busy,
    )
}

@Composable
private fun AppPicker(s: App2TvState, onQuery: (String) -> Unit, onPick: (String) -> Unit) {
    Title("App2TV")
    Body("Many apps aren't in the TV's app store. If an app is on your phone, App2TV can copy it to your TV and install it there.")
    CardBox(color = Tidy.BlueSoft) {
        Text("How it works", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
        Body("1. Install the app on this phone first. It has to be on the phone.", color = Tidy.Ink)
        Body("2. Pick it from the list below. The TV is asked if it can run it.", color = Tidy.Ink)
        Body("3. 0TV copies the app's files to the TV over your existing connection and the TV installs it. You are told plainly if it fails and why.", color = Tidy.Ink)
    }
    Note("Every app on this phone is listed, those you installed first, then system apps. System apps may not be sendable, and the TV is asked before anything is copied. Phone apps often aren't made for a remote control, and some are built only for 64-bit phones. App2TV checks this before it sends anything.")
    androidx.compose.material3.OutlinedTextField(
        s.query, onQuery, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search your phone's apps") },
        shape = RoundedCornerShape(16.dp),
    )
    when {
        !s.loaded -> Row(verticalAlignment = Alignment.CenterVertically) { Spinner(); Spacer(Modifier.width(10.dp)); Body("Reading your phone's apps…") }
        s.shown.isEmpty() -> Body(if (s.apps.isEmpty()) "No apps found on this phone." else "No app matches that search.")
        else -> s.shown.forEach { a -> AppRow(a.pkg, a.label, a.system) { onPick(a.pkg) } }
    }
    if (s.resultText != null) Note(s.resultText, color = Tidy.RedSoft, textColor = Tidy.Red)
}

@Composable
private fun AppRow(pkg: String, label: String, system: Boolean, onClick: () -> Unit) {
    Outline(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (system) "$pkg · System app" else pkg, style = MaterialTheme.typography.labelSmall, color = Tidy.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, label: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val pm = LocalContext.current.packageManager
    val bmp = remember(pkg) {
        runCatching {
            val d = pm.getApplicationIcon(pkg)
            d.toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Tidy.Blue), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AppDetail(s: App2TvState) {
    val app = s.selected ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.pkg, app.label, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Title(app.label)
            Body(listOfNotNull(app.versionName?.let { "Version $it" }, formatBytes(app.totalBytes), if (app.isSplit) "${app.apks.size} parts" else null).joinToString(" · "))
        }
    }
    when (val v = s.verdict) {
        null -> Row(verticalAlignment = Alignment.CenterVertically) { Spinner(); Spacer(Modifier.width(10.dp)); Body("Asking the TV if it can run this app…") }
        is App2Tv.Verdict.Blocked -> Note(v.reason, color = Tidy.RedSoft, textColor = Tidy.Red)
        is App2Tv.Verdict.Ok -> {
            SafetyBanner("The TV can take this app.")
            v.notes.forEach { Body(it) }
        }
    }
    if (s.busy) {
        val st = s.stage
        val text = when (st) {
            App2Tv.Stage.CHECKING -> "Checking the TV…"
            App2Tv.Stage.COPYING -> "Copying file ${s.part} of ${s.parts} to the TV…"
            App2Tv.Stage.INSTALLING -> "The TV is installing it…"
            App2Tv.Stage.VERIFYING -> "Checking that it's really there…"
            null -> ""
        }
        ProgressBar(if (st == App2Tv.Stage.COPYING && s.parts > 0) (s.part - 1f).coerceAtLeast(0f) / s.parts else if (st == App2Tv.Stage.INSTALLING || st == App2Tv.Stage.VERIFYING) 0.9f else 0.05f)
        Body(text)
    }
    s.resultText?.let { if (s.resultOk) SafetyBanner(it) else Note(it, color = Tidy.RedSoft, textColor = Tidy.Red) }
}
