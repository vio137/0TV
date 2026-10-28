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

/** Dark-first: midnight surfaces, an electric blue / aqua accent, restrained tints for status. */
object Tidy {
    val Backdrop = Color(0xFF070B16)
    val Card = Color(0xFF111827)
    val Ink = Color(0xFFEAF0FF)
    val Muted = Color(0xFF9FB0D0)
    val Line = Color(0xFF26324D)
    val Blue = Color(0xFF2F7BFF)
    val BlueSoft = Color(0xFF15264A)
    val Aqua = Color(0xFF2DD4D0)
    val Green = Color(0xFF1E9E5A)
    val GreenSoft = Color(0xFF10301F)
    val GreenInk = Color(0xFF8BE8B0)
    val Orange = Color(0xFFE8782A)
    val Purple = Color(0xFF7C5CF0)
    val Grey = Color(0xFF1E2840)
    val Amber = Color(0xFF3A2A12)
    val AmberInk = Color(0xFFFFC98A)
    val Red = Color(0xFFE5484D)
    val RedSoft = Color(0xFF3A1519)
    val Yellow = Color(0xFF3B3312)
}

val Nunito = FontFamily.SansSerif

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(600), fontSize = 17.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 13.sp),
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
        ),
        typography = typography,
        content = content,
    )
    }
}
