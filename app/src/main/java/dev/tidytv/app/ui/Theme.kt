package dev.tidytv.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 0TV design direction: calm, exact, a little warm - the living room at night.
 * One ink-tinted neutral ramp carries every surface; one accent (signal blue) marks the primary
 * action and selection; green/amber/red are semantic only. Category hues live in small dots,
 * never on surfaces.
 */
object Tidy {
    // Neutral ramp, tinted toward night ink.
    val Backdrop = Color(0xFF0A0D13)
    val Card = Color(0xFF12161E)
    val Raise = Color(0xFF181D28)
    val Ink = Color(0xFFE9EDF5)
    val Muted = Color(0xFF9AA3B7)
    val Faint = Color(0xFF6B7589)
    // Hairlines: ink at low opacity, never a solid grey.
    val Line = Color(0x14E9EDF5)
    val LineStrong = Color(0x29E9EDF5)

    // The one accent.
    val Blue = Color(0xFF3D7BFF)
    val BlueSoft = Color(0xFF182742)
    val BlueInk = Color(0xFFA9C6FF)

    // Semantic colours, toned to sit inside the palette.
    val Green = Color(0xFF3CB97E)
    val GreenSoft = Color(0xFF13291F)
    val GreenInk = Color(0xFF93DFB9)
    val Amber = Color(0xFF2C2414)
    val AmberInk = Color(0xFFE3C389)
    val Red = Color(0xFFDE5B53)
    val RedSoft = Color(0xFF2F1A1B)
    val RedInk = Color(0xFFEFA79F)
    val Yellow = Color(0xFF2C2716)

    // Category hues for app groups - used on small dots and icons only, never as surfaces.
    val Aqua = Color(0xFF56B8C4)
    val Purple = Color(0xFF9A8AF0)
    val Orange = Color(0xFFD08A4E)

    // Legacy name kept for call sites: the disabled/quiet control fill is a ramp step now.
    val Grey = Color(0xFF1A2029)
}

val Nunito = FontFamily.SansSerif

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 13.sp, fontFeatureSettings = "tnum"),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun TidyTheme(content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Settings > Accessibility > Remove animations (or the developer animator scale at 0) turns all of our motion off.
    val reduce = androidx.compose.runtime.remember(ctx) {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides reduce) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tidy.Blue, onPrimary = Color.White, background = Tidy.Backdrop, surface = Tidy.Card,
            onSurface = Tidy.Ink, onBackground = Tidy.Ink, secondary = Tidy.Green, error = Tidy.Red,
            surfaceVariant = Tidy.Raise, onSurfaceVariant = Tidy.Muted, outline = Tidy.LineStrong,
        ),
        typography = typography,
        content = content,
    )
    }
}
