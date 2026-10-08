package com.safecomp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.safecomp.R

/**
 * "Midnight vault" design tokens. SafeComp is always dark: it reads as a security tool,
 * and a dark UI also makes the glowing state colour the first thing you notice.
 */
object Sc {
    val Bg = Color(0xFF06080C)
    val Surface = Color(0xFF0F151D)
    val SurfaceHigh = Color(0xFF151D28)
    val SurfaceHighest = Color(0xFF1C2633)
    val Hairline = Color(0xFF223041)
    val TextPrimary = Color(0xFFE9EEF5)
    val TextSecondary = Color(0xFF8D9AAC)
    val TextTertiary = Color(0xFF5A6678)
    val Accent = Color(0xFF67E8F9)
    val AccentDeep = Color(0xFF3B82F6)
    val Inside = Color(0xFF34D399)
    val Outside = Color(0xFFFB7185)
    val Unknown = Color(0xFFFBBF24)
    val Violet = Color(0xFFA78BFA)
    val Sky = Color(0xFF38BDF8)
}

// Kept for existing call sites: the same three state colours, in the new palette.
val InsideGreen = Sc.Inside
val OutsideRed = Sc.Outside
val UnknownAmber = Sc.Unknown

private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter for UI text: variable font, one file for every weight. */
val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private fun mono(weight: Int) = Font(
    R.font.jetbrains_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** JetBrains Mono for numbers, timers and small technical labels. */
val MonoFamily = FontFamily(mono(400), mono(500), mono(700))

private val ScTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em),
)

private val ScColors = darkColorScheme(
    primary = Sc.Accent,
    onPrimary = Sc.Bg,
    primaryContainer = Sc.Accent.copy(alpha = 0.16f),
    onPrimaryContainer = Sc.Accent,
    secondary = Sc.Accent,
    onSecondary = Sc.Bg,
    secondaryContainer = Sc.Accent.copy(alpha = 0.18f),
    onSecondaryContainer = Sc.Accent,
    tertiary = Sc.Violet,
    background = Sc.Bg,
    onBackground = Sc.TextPrimary,
    surface = Sc.Bg,
    onSurface = Sc.TextPrimary,
    surfaceVariant = Sc.SurfaceHigh,
    onSurfaceVariant = Sc.TextSecondary,
    surfaceContainerLowest = Sc.Bg,
    surfaceContainerLow = Sc.Surface,
    surfaceContainer = Sc.Surface,
    surfaceContainerHigh = Sc.SurfaceHigh,
    surfaceContainerHighest = Sc.SurfaceHighest,
    outline = Sc.Hairline,
    outlineVariant = Sc.Hairline,
    error = Sc.Outside,
    onError = Sc.Bg,
    errorContainer = Sc.Outside.copy(alpha = 0.14f),
    onErrorContainer = Sc.Outside,
)

@Composable
fun SafeCompTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ScColors, typography = ScTypography, content = content)
}
