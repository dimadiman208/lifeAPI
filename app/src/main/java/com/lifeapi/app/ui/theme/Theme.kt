package com.lifeapi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifeapi.app.R
import com.lifeapi.app.data.Hue

object C {
    val Graphite0 = Color(0xFF0A0B0D)
    val Graphite1 = Color(0xFF121316)
    val Graphite2 = Color(0xFF191A1E)
    val Graphite3 = Color(0xFF24262B)
    val Hairline = Color(0xFF1F2125)
    val Divider = Color(0xFF1C1D21)
    val Track = Color(0xFF1A1C20)
    val Bar = Color(0xFF2C2F36)
    val Ghost = Color(0xFF3A3F4A)
    val Volt = Color(0xFFD3F36B)

    val Ink = Color(0xFFEDEEF0)
    val InkMuted = Color(0xFFA1A4AB)
    val InkSubtle = Color(0xFF8A8E96)
    val InkFaint = Color(0xFF6E727A)
    val InkSoft = Color(0xFFC9CBD0)
    val Positive = Color(0xFF8BE3A5)
    val Negative = Color(0xFFFF9A8F)

    val Health = Color(0xFF62D6C6); val HealthTint = Color(0xFF14211F)
    val Fuel = Color(0xFFF3B76A); val FuelTint = Color(0xFF2E271E)
    val Money = Color(0xFF93A9FF); val MoneyTint = Color(0xFF1E2233)
    val Train = Color(0xFFFF8F70); val TrainTint = Color(0xFF33231F)
    val Grow = Color(0xFFC5A1FF); val GrowTint = Color(0xFF29233A)
    val Gain = Color(0xFF8BE3A5); val GainTint = Color(0xFF18261E)

    fun hue(h: Int): Color = when (h) {
        Hue.HEALTH -> Health; Hue.FUEL -> Fuel; Hue.MONEY -> Money
        Hue.TRAIN -> Train; Hue.GROW -> Grow; else -> Gain
    }

    fun tint(h: Int): Color = when (h) {
        Hue.HEALTH -> HealthTint; Hue.FUEL -> FuelTint; Hue.MONEY -> MoneyTint
        Hue.TRAIN -> TrainTint; Hue.GROW -> GrowTint; else -> GainTint
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.jbmono_regular, FontWeight.Normal),
    Font(R.font.jbmono_medium, FontWeight.Medium),
)

private const val TNUM = "tnum, cv11"

object T {
    val display = TextStyle(fontFamily = Inter, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM)
    val headline = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM)
    val title = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM)
    val number = TextStyle(fontFamily = Inter, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM)
    val section = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val body = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)
    val bodyR = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TNUM)
    val small = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TNUM)
    val smallB = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val caption = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TNUM)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)
    val label = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em)
    val mono = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}

private val Scheme = darkColorScheme(
    primary = C.Volt,
    onPrimary = C.Graphite0,
    secondary = C.Volt,
    onSecondary = C.Graphite0,
    background = C.Graphite0,
    onBackground = C.Ink,
    surface = C.Graphite0,
    onSurface = C.Ink,
    surfaceVariant = C.Graphite2,
    onSurfaceVariant = C.InkMuted,
    surfaceContainerLowest = C.Graphite0,
    surfaceContainerLow = C.Graphite1,
    surfaceContainer = C.Graphite1,
    surfaceContainerHigh = C.Graphite2,
    surfaceContainerHighest = C.Graphite3,
    outline = C.Ghost,
    outlineVariant = C.Hairline,
    inverseSurface = C.Ink,
    inverseOnSurface = C.Graphite0,
    error = C.Negative,
    scrim = Color(0xFF050608),
)

@Immutable
private object Typo {
    val value = Typography(
        bodyLarge = T.bodyR.copy(fontSize = 16.sp),
        bodyMedium = T.bodyR,
        bodySmall = T.caption,
        labelLarge = T.smallB.copy(fontSize = 14.sp),
        labelMedium = T.tiny,
        titleLarge = T.title,
        titleMedium = T.section,
        headlineSmall = T.title,
    )
}

@Composable
fun LifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typo.value, content = content)
}
