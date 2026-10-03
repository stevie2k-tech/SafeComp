package com.safecomp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Status colours are used for the INSIDE / OUTSIDE / UNKNOWN card, so they are fixed
// rather than taken from the wallpaper (dynamic colour) and always mean the same thing.
val InsideGreen = Color(0xFF2E7D4F)
val OutsideRed = Color(0xFFB3261E)
val UnknownAmber = Color(0xFF9A6B00)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6EF),
    onPrimaryContainer = Color(0xFF062631),
    secondary = Color(0xFF8A5A1C),
    secondaryContainer = Color(0xFFFBDDB5),
    onSecondaryContainer = Color(0xFF2C1800),
    background = Color(0xFFF7F4EF),
    surface = Color(0xFFF7F4EF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DCDE2),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF1F4E5F),
    onPrimaryContainer = Color(0xFFC8E6EF),
    secondary = Color(0xFFF0BD7E),
    secondaryContainer = Color(0xFF6B4410),
    onSecondaryContainer = Color(0xFFFBDDB5),
    background = Color(0xFF141A1C),
    surface = Color(0xFF141A1C),
)

@Composable
fun SafeCompTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
