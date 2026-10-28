package dev.tidytv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.MoreInfo
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.NumberedStep
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TvCard
import dev.tidytv.core.flow.ConnectEvent
import dev.tidytv.core.flow.ConnectMachine
import dev.tidytv.core.flow.ConnectState
import dev.tidytv.core.flow.FoundTv

@Composable
fun ConnectScreen(state: ConnectState, onEvent: (ConnectEvent) -> Unit, onShareLogs: () -> Unit) {
    when (state) {
        is ConnectState.Scanning -> Scanning(state, onEvent)
        is ConnectState.NothingFound -> NothingFound(state, onEvent)
        is ConnectState.Connecting -> Waiting(state.tv, "Connecting to ${state.tv.title}…")
        is ConnectState.PressAllow -> PressAllow(state, onEvent)
        is ConnectState.Connected -> ScreenFrame {
            HeroMascot(mood = Mood.HAPPY)
            Bubble("Found your TV!", color = Tidy.GreenSoft, textColor = Tidy.GreenInk) { Check(Tidy.Green, 30.dp) }
            TvCard(state.tv.title, state.tv.subtitle)
        }
        is ConnectState.Pairing -> Pairing(state, onEvent)
        is ConnectState.TurnOnDebugging -> Help(
            "Turn on Wireless debugging",
            "${state.tv.title} answered, but debugging over the network is off.",
            listOf(
                "On the TV: Settings › System › Developer options.",
                "Turn on Wireless debugging. Some TVs call it Network debugging or ADB over network instead.",
                "Greyed out? The TV is probably on Ethernet. Turn on the TV's Wi-Fi and join the same network as your phone, then turn on Wireless debugging.",
                "Come back and tap Try again.",
            ),
            onEvent,
        )
        is ConnectState.Unreachable -> Help(
            "Can't reach ${state.tv.host}",
            "Nothing answered at that address.",
            listOf(
                "Check the TV is on (not asleep) and connected to your network, by Wi-Fi or Ethernet.",
                "Check your phone is on the same home network, not mobile data or a guest network.",
                "Find the TV's address in Settings › Network & Internet (your Wi-Fi name, or Ethernet).",
            ),
            onEvent,
        )
        is ConnectState.Denied -> Help(
            "Your TV said no",
            "Someone pressed Deny on the TV, or the prompt was closed.",
            listOf("Tap Try again.", "On the TV, tick Always allow from this computer, then press Allow."),
            onEvent,
        )
        is ConnectState.Failed -> ScreenFrame(bottom = {
            BigButton("Try again", { onEvent(ConnectEvent.Retry) })
            androidx.compose.foundation.layout.Row { TextLink("Share logs", onShareLogs); dev.tidytv.app.ui.ReportProblemLink("connect") }
        }) {
            HeroMascot(worried = true)
            Title("That didn't work")
            Body(state.message)
            TextLink("Pick another TV", { onEvent(ConnectEvent.Back) })
        }
    }
}

@Composable
private fun Scanning(state: ConnectState.Scanning, onEvent: (ConnectEvent) -> Unit) {
    var typing by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = typing) { typing = false }
    ScreenFrame(bottom = {
        if (state.done) TextLink("Search again", { onEvent(ConnectEvent.StartScan) })
    }) {
        HeroMascot(mood = Mood.SEARCH)
        Bubble(
            when {
                state.lookingFor != null -> "Looking for ${state.lookingFor}…"
                state.found.isEmpty() -> "Looking for your TV…"
                else -> "Tap your TV"
            },
        ) { if (!state.done) Spinner(26.dp) }
        if (state.lookingFor != null) Body("Your TV may have a new address. 0TV is finding it again.")
        for (tv in state.found) TvCard(tv.title, tv.subtitle, onClick = { onEvent(ConnectEvent.Pick(tv)) })
        if (state.done || state.found.isNotEmpty()) {
            if (typing) IpField(false, onEvent) else TextLink("My TV isn't here? Type its IP address", { typing = true })
        }
    }
}

@Composable
private fun NothingFound(state: ConnectState.NothingFound, onEvent: (ConnectEvent) -> Unit) {
    ScreenFrame(bottom = { TextLink("Search again", { onEvent(ConnectEvent.StartScan) }) }) {
        HeroMascot(worried = true)
        Title("No TV found yet")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Type your TV's IP address.", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted, modifier = Modifier.weight(1f))
            MoreInfo("Same network?") {
                Body("You'll find the TV's address on the TV in Settings › Network & Internet (your Wi-Fi name, or Ethernet).", color = Tidy.Ink)
                Body("Your phone and TV must be on the same home network. The TV can use Wi-Fi or Ethernet. Guest networks and mobile data won't find the TV.", color = Tidy.Ink)
            }
        }
        IpField(state.badIp, onEvent)
    }
}

@Composable
private fun IpField(bad: Boolean, onEvent: (ConnectEvent) -> Unit) {
    var ip by rememberSaveable { mutableStateOf("") }
    val valid = ConnectMachine.isIp(ip)
    val bad = bad || (ip.count { it == '.' } == 3 && !valid)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = ip, onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
            label = { Text("TV's IP address, e.g. 192.168.1.42") }, singleLine = true, isError = bad,
            supportingText = if (bad) { { Text("That doesn't look like an IP address.") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (valid) onEvent(ConnectEvent.TypedIp(ip)) }),
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.titleLarge,
        )
        BigButton("Connect", { onEvent(ConnectEvent.TypedIp(ip)) }, enabled = valid)
    }
}

@Composable
private fun Waiting(tv: FoundTv, text: String) {
    ScreenFrame {
        HeroMascot()
        Bubble(text) { Spinner(26.dp) }
        TvCard(tv.title, tv.subtitle)
    }
}

@Composable
private fun PressAllow(state: ConnectState.PressAllow, onEvent: (ConnectEvent) -> Unit) {
    ScreenFrame(bottom = {
        if (state.canRetry) BigButton("Try again", { onEvent(ConnectEvent.Retry) })
        TextLink("Pick another TV", { onEvent(ConnectEvent.Back) })
    }) {
        HeroMascot()
        Bubble("Press Allow on your TV") { Spinner(26.dp) }
        AllowDialogPicture()
        Body("Tick Always allow first, so it never asks again.")
        if (state.canRetry) Note("No answer yet? If the prompt went away, tap Try again.")
        MoreInfo("Asked again every time?") {
            Body("If you didn't tick Always allow, the TV asks every time. Connect again and tick it.", color = Tidy.Ink)
            Body("Still asked? Some TVs forget approvals after a while or after a restart. Approve it again on the TV each time. This app never turns off the TV's approval check.", color = Tidy.Ink)
        }
    }
}

/** A sketch of the TV's "Allow debugging?" prompt, so people know what to look for. */
@Composable
private fun AllowDialogPicture() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color(0xFF2B2F3A)).padding(18.dp)
            .semantics { contentDescription = "The TV shows: Allow USB debugging? with Always allow and Allow" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Allow USB debugging?", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Tidy.Blue), contentAlignment = Alignment.Center) { Check(androidx.compose.ui.graphics.Color.White, 16.dp) }
            Spacer(Modifier.width(10.dp))
            Text("Always allow from this computer", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("CANCEL", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFFB8C2E0), modifier = Modifier.padding(10.dp))
            Text("ALLOW", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF1B1B3A),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color(0xFFFFE9A8)).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Pairing(state: ConnectState.Pairing, onEvent: (ConnectEvent) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    val submit = { onEvent(ConnectEvent.SubmitCode(code, port.toIntOrNull())) }
    ScreenFrame(bottom = {
        BigButton(if (state.busy) "Connecting…" else "Connect", submit, enabled = !state.busy && code.length == 6)
        TextLink("Pick another TV", { onEvent(ConnectEvent.Back) })
    }) {
        TvCard(state.tv.title, state.tv.subtitle)
        Title("Enter the code shown on your TV", align = TextAlign.Center)
        Body("On the TV: Developer options › Wireless debugging › Pair device with pairing code.", align = TextAlign.Center)
        CodeBoxes(code) { code = it }
        if (state.tv.pairPort == null) OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
            label = { Text("Port") }, singleLine = true,
            supportingText = { Text("The number after the colon on your TV, e.g. 37155") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Note(it, color = Tidy.RedSoft, textColor = Tidy.Red) }
        if (state.busy) Row(verticalAlignment = Alignment.CenterVertically) { Spinner(); Spacer(Modifier.width(10.dp)); Body("Pairing…") }
    }
}

/** Six boxes as in concept C, backed by one text field so paste and the number keyboard just work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = code, onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pairing code, 6 digits" },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    Box(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .border(2.dp, if (i == code.length) Tidy.Blue else Tidy.Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.getOrNull(i)?.toString() ?: "–", style = MaterialTheme.typography.headlineMedium,
                            color = if (i < code.length) Tidy.Ink else Tidy.Line)
                    }
                }
            }
        },
    )
}

@Composable
private fun Help(title: String, intro: String, steps: List<String>, onEvent: (ConnectEvent) -> Unit) {
    ScreenFrame(bottom = {
        BigButton("Try again", { onEvent(ConnectEvent.Retry) })
        TextLink("Pick another TV", { onEvent(ConnectEvent.Back) })
    }) {
        HeroMascot(worried = true)
        Title(title)
        Body(intro)
        MoreInfo(title, link = "How to fix it") {
            steps.forEachIndexed { i, s -> NumberedStep(i + 1, s) }
        }
    }
}
