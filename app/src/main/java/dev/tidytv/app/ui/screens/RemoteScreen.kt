package dev.tidytv.app.ui.screens

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.tidytv.app.RemoteApp
import dev.tidytv.app.bar
import dev.tidytv.app.ordered
import dev.tidytv.app.RemoteState
import dev.tidytv.app.ui.AppIcon
import dev.tidytv.app.ui.Bubble
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Spinner
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.core.ops.RemoteKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.unit.min

private val DeepBlue = Color(0xFF1B4FD8)
private val PadPaper = Color(0xFFF3F7FF)
private val HoldDelayMs = 350L
private val HoldEveryMs = 125L // about 8 keys a second
private val TouchpadStep = 48.dp

/** The phone remote: one screen, nothing to scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    s: RemoteState,
    tvName: String?,
    onKey: (RemoteKey) -> Unit,
    onType: (String) -> Unit,
    onLaunch: (RemoteApp) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onPin: (String) -> Unit = {},
    onMovePin: (String, Int) -> Unit = { _, _ -> },
) {
    val haptic = LocalHapticFeedback.current
    val enabled = !s.reconnecting
    var touchpad by rememberSaveable { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf(false) }
    val press: (RemoteKey) -> Unit = { k ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKey(k)
    }

    // Own frame rather than ScreenFrame: that one scrolls, and the remote must fit as it is.
    Box(Modifier.fillMaxSize().background(Tidy.Backdrop).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Tidy.Card).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopRow(tvName, enabled, onBack, onPower = { press(RemoteKey.POWER) }, onKeyboard = { keyboard = true }, onApps = { allApps = true })

                if (s.message != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Note(s.message)
                        TextLink("OK", onDismissMessage)
                    }
                }

                // Short screens or large text: scroll so the shortcut row is never cut off.
                val compact = LocalConfiguration.current.screenHeightDp < 780 || LocalDensity.current.fontScale > 1.15f
                Column(
                    Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f).then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ModeSwitch(touchpad, enabled) { touchpad = it }

                    BoxWithConstraints(if (compact) Modifier.fillMaxWidth().height(300.dp) else Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val h = min(maxHeight, 420.dp)
                        Box(Modifier.fillMaxWidth().height(h)) {
                            if (touchpad) Touchpad(enabled, press) else DPad(enabled, press)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavPill(Icons.AutoMirrored.Rounded.ArrowBack, "Back", enabled, Modifier.weight(1f)) { press(RemoteKey.BACK) }
                        NavPill(Icons.Rounded.Home, "Home", enabled, Modifier.weight(1f)) { press(RemoteKey.HOME) }
                        NavPill(Icons.Rounded.Menu, "Menu", enabled, Modifier.weight(1f)) { press(RemoteKey.MENU) }
                    }

                    Row(Modifier.fillMaxWidth().height(148.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VolumeRocker(enabled, press)
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaPill(enabled, press, Modifier.weight(1f))
                            QuickApps(s, enabled, onLaunch) { allApps = true }
                        }
                    }
                }
            }
            if (s.reconnecting) Box(Modifier.align(Alignment.Center).padding(horizontal = 12.dp).zIndex(1f)) {
                Bubble("Reconnecting to your TV…", leading = { Spinner() })
            }
        }
    }

    if (keyboard) KeyboardSheet(enabled, press, onType) { keyboard = false }
    if (allApps) AppsSheet(s, onLaunch, onPin, onMovePin) { allApps = false }
}

// ---------- top row ----------

@Composable
private fun TopRow(tvName: String?, enabled: Boolean, onBack: () -> Unit, onPower: () -> Unit, onKeyboard: () -> Unit, onApps: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIcon(Icons.Rounded.PowerSettingsNew, "Power", enabled, Tidy.RedSoft, Tidy.Red, onClick = onPower)
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(999.dp)).background(Tidy.BlueSoft)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back to My TV" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp), tint = Tidy.Blue)
            Spacer(Modifier.width(6.dp))
            Text(
                tvName ?: "My TV", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        }
        RoundIcon(Icons.Rounded.Keyboard, "Keyboard", enabled, Tidy.BlueSoft, Tidy.Blue, onClick = onKeyboard)
        RoundIcon(Icons.Rounded.Apps, "Apps", enabled, Tidy.BlueSoft, Tidy.Blue, onClick = onApps)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, enabled: Boolean, bg: Color, tint: Color, size: Dp = 48.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
}

// ---------- mode switch ----------

@Composable
private fun ModeSwitch(touchpad: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Tidy.BlueSoft).padding(4.dp)) {
            Segment("D-pad", !touchpad, enabled) { onChange(false) }
            Segment("Touchpad", touchpad, enabled) { onChange(true) }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "seg")
    Box(
        Modifier.height(40.dp).widthIn(min = 104.dp)
            .then(if (selected) Modifier.shadow(3.dp, RoundedCornerShape(999.dp), ambientColor = Tidy.Blue.copy(alpha = 0.25f), spotColor = Tidy.Blue.copy(alpha = 0.25f)) else Modifier)
            .clip(RoundedCornerShape(999.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Tidy.Blue else Tidy.Muted, modifier = Modifier.padding(horizontal = 14.dp))
    }
}

// ---------- the pad ----------

private enum class Zone(val key: RemoteKey?) { UP(RemoteKey.UP), DOWN(RemoteKey.DOWN), LEFT(RemoteKey.LEFT), RIGHT(RemoteKey.RIGHT), OK(null) }

/** Which part of a pad [size] wide the finger at [p] is on, or null outside the dial. */
private fun zoneAt(p: Offset, size: Float): Zone? {
    val c = size / 2
    val dx = p.x - c
    val dy = p.y - c
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    if (d > c * 1.04f) return null
    if (d < c * 0.34f) return Zone.OK
    return if (abs(dx) >= abs(dy)) (if (dx > 0) Zone.RIGHT else Zone.LEFT) else (if (dy > 0) Zone.DOWN else Zone.UP)
}

/** Fires [tick] now, then again every [HoldEveryMs] after [HoldDelayMs], until the finger lifts. */
private suspend fun PressGestureScope.repeatWhileHeld(tick: () -> Unit) = coroutineScope {
    tick()
    val job = launch {
        delay(HoldDelayMs)
        while (isActive) { tick(); delay(HoldEveryMs) }
    }
    try { awaitRelease() } finally { job.cancel() }
}

@Composable
private fun DPad(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    var down by remember { mutableStateOf<Zone?>(null) }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxSize()
            .shadow(14.dp, shape, ambientColor = Tidy.Blue.copy(alpha = 0.35f), spotColor = DeepBlue.copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1C2A4A), Color(0xFF121B33)))),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            Box(
                Modifier.size(side)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { p ->
                            val z = zoneAt(p, size.width.toFloat())
                            if (z == null || !on) return@detectTapGestures
                            down = z
                            try {
                                val k = z.key
                                if (k == null) { send(RemoteKey.OK); awaitRelease() }
                                else repeatWhileHeld { send(k) }
                            } finally { down = null }
                        })
                    },
            ) {
                DialDrawing(down, Modifier.fillMaxSize())
                // Chevrons and the OK disc, over the drawing.
                val r = side / 2
                val mid = r * 0.67f
                Chevron(Icons.Rounded.KeyboardArrowUp, "Up", enabled, down == Zone.UP, Modifier.align(Alignment.Center).offset(y = -mid)) { send(RemoteKey.UP) }
                Chevron(Icons.Rounded.KeyboardArrowDown, "Down", enabled, down == Zone.DOWN, Modifier.align(Alignment.Center).offset(y = mid)) { send(RemoteKey.DOWN) }
                Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", enabled, down == Zone.LEFT, Modifier.align(Alignment.Center).offset(x = -mid)) { send(RemoteKey.LEFT) }
                Chevron(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", enabled, down == Zone.RIGHT, Modifier.align(Alignment.Center).offset(x = mid)) { send(RemoteKey.RIGHT) }
                val ok = down == Zone.OK
                val okScale by animateFloatAsState(if (ok) 0.93f else 1f, label = "ok")
                Box(
                    Modifier.align(Alignment.Center).size(side * 0.4f).scale(okScale)
                        .shadow(if (ok) 2.dp else 8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = DeepBlue)
                        .clip(CircleShape).background(if (ok) Tidy.Grey else Tidy.Blue)
                        .semantics { contentDescription = "OK"; role = Role.Button; onClick { send(RemoteKey.OK); true } },
                    contentAlignment = Alignment.Center,
                ) { Text("OK", style = MaterialTheme.typography.titleLarge, color = Color.White) }
            }
        }
    }
}

/** Four wedges around a hub; the one under the finger is lighter and a touch larger. */
@Composable
private fun DialDrawing(down: Zone?, modifier: Modifier) {
    val pressed = listOf(Zone.RIGHT, Zone.DOWN, Zone.LEFT, Zone.UP).map { z ->
        animateFloatAsState(if (down == z) 1f else 0f, label = "wedge").value
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val outer = size.width / 2
        val inner = outer * 0.42f
        val thick = outer - inner
        val mid = inner + thick / 2
        // Angles in Canvas degrees: 0 = right, 90 = down, 180 = left, 270 = up.
        for (i in 0 until 4) {
            val p = pressed[i]
            val gap = 5f
            withTransform({ scale(1f + 0.04f * p, 1f + 0.04f * p, c) }) {
                drawArc(
                    color = Color.White.copy(alpha = 0.16f + 0.26f * p),
                    startAngle = i * 90f - 45f + gap / 2, sweepAngle = 90f - gap, useCenter = false,
                    topLeft = Offset(c.x - mid, c.y - mid), size = Size(mid * 2, mid * 2),
                    style = Stroke(width = thick - 2f),
                )
            }
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, desc: String, enabled: Boolean, pressed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Touch handling is the dial's; this carries the glyph and the accessibility action.
    Box(
        modifier.size(56.dp).semantics { contentDescription = desc; role = Role.Button; if (enabled) onClick { onClick(); true } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(if (pressed) 42.dp else 38.dp), tint = Color.White) }
}

@Composable
private fun Touchpad(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    val haptic = LocalHapticFeedback.current
    val stepPx = with(LocalDensity.current) { TouchpadStep.toPx() }
    val math = remember(stepPx) { TouchpadMath(stepPx) }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxSize()
            .shadow(10.dp, shape, ambientColor = Tidy.Blue.copy(alpha = 0.25f), spotColor = Tidy.Blue.copy(alpha = 0.3f))
            .clip(shape).background(PadPaper)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (on) send(RemoteKey.OK) },
                    onLongPress = { if (on) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); send(RemoteKey.BACK) } },
                )
            }
            .pointerInput(stepPx) {
                detectDragGestures(
                    onDragStart = { math.reset() },
                    onDragEnd = { math.reset() },
                    onDragCancel = { math.reset() },
                ) { change, drag ->
                    change.consume()
                    if (on) math.drag(drag.x, drag.y).forEach { send(it) }
                }
            }
            .semantics { contentDescription = "Touchpad" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 22.dp.toPx()
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) { drawCircle(Tidy.Blue.copy(alpha = 0.16f), 1.6.dp.toPx(), Offset(x, y)); x += gap }
                y += gap
            }
        }
        Text(
            "Swipe to move · Tap to select · Press and hold to go back",
            style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp).clip(RoundedCornerShape(16.dp)).background(PadPaper.copy(alpha = 0.85f)).padding(10.dp),
        )
    }
}

// ---------- keys under the pad ----------

@Composable
private fun NavPill(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.height(52.dp).shadow(3.dp, shape, ambientColor = Tidy.Blue.copy(alpha = 0.2f), spotColor = Tidy.Blue.copy(alpha = 0.2f))
            .clip(shape).background(Tidy.BlueSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Tidy.Blue)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tidy.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun VolumeRocker(enabled: Boolean, press: (RemoteKey) -> Unit) {
    val send by rememberUpdatedState(press)
    val on by rememberUpdatedState(enabled)
    val shape = RoundedCornerShape(999.dp)
    Column(
        Modifier.width(64.dp).fillMaxHeight()
            .shadow(3.dp, shape, ambientColor = Tidy.Blue.copy(alpha = 0.2f), spotColor = Tidy.Blue.copy(alpha = 0.2f))
            .clip(shape).background(Tidy.BlueSoft),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HoldKey(Icons.Rounded.Add, "Volume up", enabled, Modifier.weight(1f).fillMaxWidth()) { send(RemoteKey.VOLUME_UP) }
        Box(
            Modifier.size(44.dp).shadow(3.dp, CircleShape, ambientColor = Tidy.Blue.copy(alpha = 0.25f), spotColor = Tidy.Blue.copy(alpha = 0.25f))
                .clip(CircleShape).background(Tidy.Grey)
                .clickable(enabled = enabled, role = Role.Button) { send(RemoteKey.MUTE) }
                .semantics { contentDescription = "Mute" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, Modifier.size(22.dp), tint = Tidy.Blue) }
        HoldKey(Icons.Rounded.Remove, "Volume down", enabled, Modifier.weight(1f).fillMaxWidth()) { send(RemoteKey.VOLUME_DOWN) }
    }
}

/** A key that repeats while it is held, like the arrows. */
@Composable
private fun HoldKey(icon: ImageVector, desc: String, enabled: Boolean, modifier: Modifier, tick: () -> Unit) {
    val onTick by rememberUpdatedState(tick)
    val on by rememberUpdatedState(enabled)
    var held by remember { mutableStateOf(false) }
    Box(
        modifier.heightIn(min = 48.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!on) return@detectTapGestures
                    held = true
                    try { repeatWhileHeld { onTick() } } finally { held = false }
                })
            }
            .semantics { contentDescription = desc; role = Role.Button; if (enabled) onClick { onTick(); true } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(if (held) 30.dp else 26.dp), tint = Tidy.Blue) }
}

@Composable
private fun MediaPill(enabled: Boolean, press: (RemoteKey) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .shadow(3.dp, shape, ambientColor = Tidy.Blue.copy(alpha = 0.2f), spotColor = Tidy.Blue.copy(alpha = 0.2f))
            .clip(shape).background(Tidy.BlueSoft).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundIcon(Icons.Rounded.FastRewind, "Rewind", enabled, Color.Transparent, Tidy.Blue) { press(RemoteKey.REWIND) }
        Box(
            Modifier.size(52.dp).shadow(4.dp, CircleShape, ambientColor = Tidy.Blue.copy(alpha = 0.3f), spotColor = Tidy.Blue.copy(alpha = 0.3f))
                .clip(CircleShape).background(Tidy.Blue)
                .clickable(enabled = enabled, role = Role.Button) { press(RemoteKey.PLAY_PAUSE) }
                .semantics { contentDescription = "Play or pause" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(30.dp), tint = Color.White) }
        RoundIcon(Icons.Rounded.FastForward, "Fast forward", enabled, Color.Transparent, Tidy.Blue) { press(RemoteKey.FAST_FORWARD) }
    }
}

@Composable
private fun QuickApps(s: RemoteState, enabled: Boolean, onLaunch: (RemoteApp) -> Unit, onAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        s.bar().forEach { app ->
            Box(
                Modifier.size(52.dp).shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Tidy.Blue.copy(alpha = 0.2f), spotColor = Tidy.Blue.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable(enabled = enabled, role = Role.Button) { onLaunch(app) }
                    .semantics { contentDescription = app.label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(app.pkg, app.label, 34.dp) }
        }
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(Tidy.BlueSoft)
                .clickable(enabled = enabled, role = Role.Button, onClick = onAll)
                .semantics { contentDescription = "All apps" }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Apps, null, Modifier.size(22.dp), tint = Tidy.Blue)
            Spacer(Modifier.width(6.dp))
            Text("All", style = MaterialTheme.typography.labelMedium, color = Tidy.Ink, maxLines = 1)
        }
    }
}

// ---------- sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardSheet(enabled: Boolean, press: (RemoteKey) -> Unit, onType: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val send = {
        if (text.isNotEmpty()) { onType(text); text = "" }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Type on the TV", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text("Search or type") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIcon(Icons.AutoMirrored.Rounded.Backspace, "Delete", enabled, Tidy.BlueSoft, Tidy.Blue) { press(RemoteKey.DELETE) }
                RoundIcon(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter", enabled, Tidy.BlueSoft, Tidy.Blue) { press(RemoteKey.ENTER) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).background(Tidy.Blue.copy(alpha = if (enabled) 1f else 0.5f))
                        .clickable(enabled = enabled, role = Role.Button) { send() }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Send", style = MaterialTheme.typography.labelLarge, color = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsSheet(s: RemoteState, onLaunch: (RemoteApp) -> Unit, onPin: (String) -> Unit, onMove: (String, Int) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                s.loadingApps -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Spinner(); Spacer(Modifier.width(10.dp))
                    Text("Loading apps…", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted)
                }
                s.apps.isEmpty() -> Text("No apps found", style = MaterialTheme.typography.bodyLarge, color = Tidy.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                else -> {
                    val ordered = s.ordered()
                    val pinned = dev.tidytv.core.lists.RemoteBar.pinned(s.apps.map { it.pkg }, s.pinned)
                    val by = s.apps.associateBy { it.pkg }
                    Text("Apps on your remote", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
                    Text("Tap the star to put an app on the remote's bar. Use the arrows to choose which comes first.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
                    if (pinned.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pinned.forEachIndexed { i, pkg ->
                            val app = by[pkg] ?: return@forEachIndexed
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                AppIcon(app.pkg, app.label, 44.dp)
                                Text(app.label, style = MaterialTheme.typography.labelSmall, color = Tidy.Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Row {
                                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Move ${app.label} earlier", i > 0) { onMove(pkg, -1) }
                                    SmallArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Move ${app.label} later", i < pinned.lastIndex) { onMove(pkg, +1) }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().height(380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ordered.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app ->
                                    val on = app.pkg in pinned
                                    Box(Modifier.weight(1f)) {
                                        Column(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                .clickable(role = Role.Button) { onClose(); onLaunch(app) }
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AppIcon(app.pkg, app.label, 56.dp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(app.label, style = MaterialTheme.typography.labelMedium, color = Tidy.Ink, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(40.dp).clip(CircleShape)
                                                .clickable(role = Role.Checkbox) { onPin(app.pkg) }
                                                .semantics { contentDescription = if (on) "Take ${app.label} off the remote bar" else "Put ${app.label} on the remote bar" },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(22.dp), tint = if (on) Tidy.Orange else Tidy.Muted)
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) Tidy.Blue else Tidy.Muted.copy(alpha = 0.35f)) }
}
