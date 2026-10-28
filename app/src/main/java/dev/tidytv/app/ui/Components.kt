package dev.tidytv.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tidytv.core.lists.AppLabels
import org.json.JSONObject

private val Chunky = RoundedCornerShape(18.dp)

/** Concept C's chunky button: thick ink outline with a solid "pressed-in" shadow underneath. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tidy.Blue,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    // Flat, quiet buttons. A near-white colour from older call sites means "secondary" here.
    val secondary = color.luminance() > 0.8f || color == Tidy.Grey
    val bg = when { !enabled -> Tidy.Grey; secondary -> Tidy.Grey; else -> color }
    val fg = when { !enabled -> Tidy.Muted; secondary -> Tidy.Ink; else -> textColor }
    val shape = RoundedCornerShape(16.dp)
    // The accent (primary) action wears the same blue-to-aqua gradient as the hub's feature card; other colours stay flat.
    val accent = enabled && !secondary && color == Tidy.Blue
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth()
            .pressScale(src, enabled)
            .clip(shape)
            .then(if (accent) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Tidy.Blue, Color(0xFF1B58C9), Tidy.Aqua.copy(alpha = 0.85f)))) else Modifier.background(bg))
            .then(if (secondary || !enabled) Modifier.border(1.dp, Tidy.Line, shape) else Modifier)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button) {
                if (accent) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
    }
}

/** A colour-coded choice row on the welcome screen: an icon, a name and an optional small badge. */
@Composable
fun ChoiceButton(text: String, icon: ImageVector, color: Color, textColor: Color = Color.White, badge: String? = null, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressScale(src).clip(Chunky).background(color)
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = textColor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = textColor)
            if (badge != null) Text(
                badge, style = MaterialTheme.typography.labelMedium, color = textColor,
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = textColor)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, color: Color = Tidy.Blue, modifier: Modifier = Modifier, icon: ImageVector? = null, small: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = color); Spacer(Modifier.width(6.dp)) }
        Text(text, style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, color: Color = Tidy.Card, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color)
            .then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(22.dp)) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Note(text: String, color: Color = Tidy.Amber, textColor: Color = Tidy.AmberInk) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = textColor,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(14.dp),
    )
}

@Composable
fun SafetyBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tidy.GreenSoft)
            .border(2.dp, Tidy.Green, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Check(Tidy.Green, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Tidy.GreenInk)
    }
}

@Composable
fun Check(color: Color, size: Dp) {
    // The tick draws itself in (about 0.45 s) when it first appears; instant with animations off and in previews.
    val still = LocalReduceMotion.current || LocalInspectionMode.current
    val draw = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!still) draw.animateTo(1f, androidx.compose.animation.core.tween(450, 120, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Canvas(Modifier.size(size)) {
        val full = Path().apply {
            moveTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.52f)
            lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.74f)
            lineTo(this@Canvas.size.width * 0.82f, this@Canvas.size.height * 0.3f)
        }
        val shown = if (draw.value >= 1f) full else Path().also { out ->
            val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(full, false) }
            m.getSegment(0f, m.length * draw.value, out, true)
        }
        drawPath(shown, color, style = Stroke(width = this.size.width * 0.14f, cap = StrokeCap.Round))
    }
}

/** Still arc in previews and screenshot tests, where an endless animation would never settle. */
@Composable
fun Spinner(size: Dp = 22.dp, color: Color = Tidy.Blue) {
    if (LocalInspectionMode.current) CircularProgressIndicator(progress = { 0.7f }, modifier = Modifier.size(size), color = color, strokeWidth = 3.dp)
    else CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 3.dp)
}

/** Square checkbox drawn in concept C style. */
@Composable
fun TickBox(checked: Boolean) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
            .background(if (checked) Tidy.Blue else Tidy.Grey)
            .border(1.5.dp, if (checked) Tidy.Blue else Tidy.Muted, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) { if (checked) Check(Color.White, 18.dp) }
}

/** The mascot, drawn in code so it is crisp at any size. Happy, or worried when something went wrong. */
@Composable
fun Mascot(size: Dp, worried: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = if (worried) "Worried TV" else "Smiling TV" }) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.035f
        val screenTop = h * 0.12f
        val screenH = h * 0.62f
        drawRoundRect(Tidy.Ink, Offset(w * 0.06f, screenTop + stroke), Size(w * 0.88f, screenH), CornerRadius(w * 0.1f))
        drawRoundRect(Color(0xFF5EA8FF), Offset(w * 0.06f, screenTop), Size(w * 0.88f, screenH), CornerRadius(w * 0.1f))
        drawRoundRect(Tidy.Ink, Offset(w * 0.06f, screenTop), Size(w * 0.88f, screenH), CornerRadius(w * 0.1f), style = Stroke(stroke))
        val eyeY = screenTop + screenH * 0.42f
        if (worried) {
            drawLine(Tidy.Ink, Offset(w * 0.30f, eyeY - h * 0.06f), Offset(w * 0.40f, eyeY - h * 0.03f), stroke, StrokeCap.Round)
            drawLine(Tidy.Ink, Offset(w * 0.70f, eyeY - h * 0.06f), Offset(w * 0.60f, eyeY - h * 0.03f), stroke, StrokeCap.Round)
        }
        drawCircle(Tidy.Ink, w * 0.045f, Offset(w * 0.36f, eyeY))
        drawCircle(Tidy.Ink, w * 0.045f, Offset(w * 0.64f, eyeY))
        val mouth = Path()
        val my = screenTop + screenH * 0.68f
        if (worried) {
            mouth.moveTo(w * 0.40f, my + h * 0.03f); mouth.quadraticTo(w * 0.5f, my - h * 0.04f, w * 0.60f, my + h * 0.03f)
        } else {
            mouth.moveTo(w * 0.38f, my - h * 0.02f); mouth.quadraticTo(w * 0.5f, my + h * 0.09f, w * 0.62f, my - h * 0.02f)
        }
        drawPath(mouth, Tidy.Ink, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.6f), w * 0.04f, Offset(w * 0.24f, my - h * 0.04f))
        drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.6f), w * 0.04f, Offset(w * 0.76f, my - h * 0.04f))
        val legY = screenTop + screenH + stroke
        drawLine(Tidy.Ink, Offset(w * 0.36f, legY), Offset(w * 0.30f, h * 0.94f), stroke, StrokeCap.Round)
        drawLine(Tidy.Ink, Offset(w * 0.64f, legY), Offset(w * 0.70f, h * 0.94f), stroke, StrokeCap.Round)
    }
}

/** Brand marks from Simple Icons (CC0), loaded once from assets/icons.json. */
object BrandIcons {
    data class Icon(val path: Path, val color: Color)
    @Volatile private var cache: Map<String, Icon>? = null

    fun get(context: Context): Map<String, Icon> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val obj = JSONObject(context.assets.open("icons.json").bufferedReader().readText())
            obj.keys().asSequence().associateWith { k ->
                val v = obj.getJSONObject(k)
                Icon(PathParser().parsePathString(v.getString("path")).toPath(), Color(("FF" + v.getString("hex")).toLong(16)))
            }
        }.getOrDefault(emptyMap()).also { cache = it }
    }
}

private val groupTints = listOf(Color(0xFFFFE1E1), Color(0xFFE1ECFF), Color(0xFFEDE3FF), Color(0xFFE0F5E9), Color(0xFFFFEFD9), Color(0xFFE3F4F8))

/** Real app icons read from the TV, by package (file paths on the phone). */
val LocalTvIcons = androidx.compose.runtime.staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** App icon: the real icon from the TV, else a brand mark, else a soft circle with initials. */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    // The app's real icon, read from its APK on the TV, wins over brand marks and initials.
    val real = LocalTvIcons.current[pkg]?.let { path ->
        remember(path) { runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    if (real != null) {
        androidx.compose.foundation.Image(real, label, Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)))
        return
    }
    val icons = remember { BrandIcons.get(context) }
    val icon = AppLabels.iconSlug(pkg)?.let { icons[it] }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (icon != null) Color.White else groupTints[Math.floorMod(pkg.hashCode(), groupTints.size)])
            .border(1.5.dp, Tidy.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Canvas(Modifier.size(size * 0.58f)) {
                scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) { drawPath(icon.path, icon.color) }
            }
        } else {
            Text(AppLabels.initials(label), style = MaterialTheme.typography.labelMedium, color = Color(0xFF1B2540)) // dark on the light tint: Tidy.Ink is near-white and was unreadable here
        }
    }
}

@Composable
fun ProgressBar(fractionTarget: Float, color: Color = Tidy.Blue) {
    val fraction by androidx.compose.animation.core.animateFloatAsState(
        fractionTarget.coerceIn(0f, 1f),
        if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        Modifier.fillMaxWidth().heightIn(min = 20.dp).clip(RoundedCornerShape(99.dp))
            .background(Color.White).border(2.dp, Tidy.Ink, RoundedCornerShape(99.dp)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).heightIn(min = 20.dp).background(color, RoundedCornerShape(99.dp)))
    }
}


/** True when the user turned animations off in system settings: motion collapses to instant changes. */
val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Press feedback: the control dips to 96.5% while a finger is down and springs back, the way system buttons do.
 * Pass the same [source] to the control's clickable so the press is the one the user is making. No motion when
 * animations are off.
 */
@Composable
fun Modifier.pressScale(source: androidx.compose.foundation.interaction.MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val target = if (pressed && enabled && !reduce) 0.965f else 1f
    val scale by androidx.compose.animation.core.animateFloatAsState(
        target, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 650f), label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Eases a block in on first show: fades up by [rise] after [delayMs]; instant when animations are off. */
@Composable
fun Modifier.enterRise(delayMs: Int = 0, rise: Dp = 14.dp): Modifier {
    val reduce = LocalReduceMotion.current
    val p = remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!reduce) p.animateTo(1f, androidx.compose.animation.core.tween(360, delayMs, androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * px }
}
