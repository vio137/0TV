package dev.tidytv.app.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline

/**
 * Every screen: a white rounded sheet on the pale-blue backdrop (concept C), a scrolling body and an
 * optional pinned bottom action so the main button is always in reach.
 */
@Composable
fun ScreenFrame(
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1226), Tidy.Backdrop))).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp)).background(Tidy.Card).border(1.dp, Tidy.Line, RoundedCornerShape(24.dp)),
        ) {
            if (top != null) Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp)) { top() }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = body,
            )
            if (bottom != null) Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = bottom,
            )
        }
    }
}

/**
 * The bar on every screen inside My TV: a back link on the left and the TV's name on the right,
 * so it's always clear which TV the buttons act on.
 */
@Composable
fun TopBar(back: String, onBack: () -> Unit, tvName: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextLink(back, onBack, icon = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack)
        Spacer(Modifier.weight(1f))
        if (tvName != null) Text(
            tvName, style = MaterialTheme.typography.labelMedium, color = Tidy.Ink, maxLines = 1,
            modifier = Modifier.background(Tidy.BlueSoft, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Tidy.Ink, textAlign = align,
        modifier = modifier.fillMaxWidth().semantics { heading() })

@Composable
fun Body(text: String, color: Color = Tidy.Muted, align: TextAlign = TextAlign.Start) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = align, modifier = Modifier.fillMaxWidth())

/** Speech bubble the mascot "says", as in concept C. */
@Composable
fun Bubble(text: String, color: Color = Tidy.BlueSoft, textColor: Color = Tidy.Ink, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(color).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Text(text, style = MaterialTheme.typography.headlineMedium, color = textColor, modifier = Modifier.semantics { heading() })
    }
}

/** Status marks: a restrained glyph on a quiet plate. (Replaces the old illustrated mascot.) */
enum class Mood(val description: String) {
    STAND("TV"), SEARCH("Checking"), HAPPY("All good"), CHEER("Done"), WORRIED("Needs attention"),
}

/** The mark at the top of a screen: a thin-ringed plate with one glyph, no animation. */
@Composable
fun HeroMascot(worried: Boolean = false, mood: Mood = if (worried) Mood.WORRIED else Mood.STAND, height: androidx.compose.ui.unit.Dp = 150.dp) {
    val icon = when (mood) {
        Mood.STAND -> androidx.compose.material.icons.Icons.Rounded.Tv
        Mood.SEARCH -> androidx.compose.material.icons.Icons.Rounded.Search
        Mood.HAPPY, Mood.CHEER -> androidx.compose.material.icons.Icons.Rounded.CheckCircle
        Mood.WORRIED -> androidx.compose.material.icons.Icons.Rounded.ErrorOutline
    }
    val tint = if (mood == Mood.WORRIED) Tidy.AmberInk else if (mood == Mood.HAPPY || mood == Mood.CHEER) Tidy.GreenInk else Tidy.Aqua
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Tidy.BlueSoft, Tidy.Card)))
                .border(1.dp, Tidy.Line, RoundedCornerShape(20.dp))
                .semantics { contentDescription = mood.description },
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(icon, null, Modifier.size(30.dp), tint = tint) }
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Switch(
        checked = on, onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Tidy.Green, checkedBorderColor = Tidy.Green,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Tidy.Grey, uncheckedBorderColor = Tidy.Line,
        ),
    )
}

/** Numbered instruction, used on Get ready and the help states. */
@Composable
fun NumberedStep(n: Int, title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Tidy.Blue), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
        }
    }
}

/** A little TV with a colourful screen, standing in for the TV's own picture. */
@Composable
fun TvPicture(width: androidx.compose.ui.unit.Dp = 92.dp) {
    Canvas(Modifier.size(width, width * 0.66f)) {
        val w = size.width; val h = size.height
        drawRoundRect(Tidy.Ink, Offset(0f, 0f), Size(w, h * 0.86f), CornerRadius(w * 0.04f))
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF2D5BFF), Color(0xFFB040E0), Color(0xFFFF5A3C), Color(0xFFFFB020)), Offset(0f, 0f), Offset(w, h)),
            Offset(w * 0.035f, h * 0.05f), Size(w * 0.93f, h * 0.74f), CornerRadius(w * 0.02f),
        )
        drawLine(Tidy.Ink, Offset(w * 0.2f, h * 0.86f), Offset(w * 0.14f, h), strokeWidth = w * 0.03f)
        drawLine(Tidy.Ink, Offset(w * 0.8f, h * 0.86f), Offset(w * 0.86f, h), strokeWidth = w * 0.03f)
    }
}

/** Card for one TV in the scan list and on My TVs. */
@Composable
fun TvCard(title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, preview: dev.tidytv.app.TvPreview = dev.tidytv.app.TvPreview()) {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressScale(src, onClick != null).clip(RoundedCornerShape(16.dp)).background(Tidy.Card)
            .border(1.dp, Tidy.Line, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPreviewFrame(title, preview)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Tidy.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
        }
        if (trailing != null) trailing() else if (onClick != null) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, Modifier.size(28.dp), tint = Tidy.Muted)
        }
    }
}

/** Round coloured badge holding a glyph, for group headers. */
@Composable
fun Badge(color: Color, glyph: String) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
fun Outline(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    CardBox(modifier, border = Tidy.Line, content = content)

/** A quiet TV bezel is shared by the waiting symbol and the saved screen. */
@Composable
fun TvPreviewFrame(title: String, preview: dev.tidytv.app.TvPreview) {
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp, 67.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF283345))
            .border(1.dp, Color(0xFF536077), RoundedCornerShape(7.dp)).padding(4.dp)) {
            val screen = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color(0xFF101C2E))
            val image = preview.image
            if (image != null) androidx.compose.foundation.Image(image.asImageBitmap(), "Saved screen from $title", screen,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else AndroidTvWaiting(screen)
        }
        Box(Modifier.size(12.dp, 3.dp).background(Color(0xFF283345)))
        Box(Modifier.size(36.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF536077)))
    }
}

@Composable
private fun AndroidTvWaiting(modifier: Modifier) {
    val reduce = LocalReduceMotion.current || androidx.compose.ui.platform.LocalInspectionMode.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "Android TV waiting")
    val glow = if (reduce) 1f else transition.animateFloat(0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "Android glow").value
    Box(modifier.semantics { contentDescription = "Android TV placeholder, waiting for a saved screen" }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Android, null,
            Modifier.size(32.dp).graphicsLayer { alpha = glow }, tint = Color(0xFF9EDDB4))
    }
}
