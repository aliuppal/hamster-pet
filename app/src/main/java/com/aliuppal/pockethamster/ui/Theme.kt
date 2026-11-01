package com.aliuppal.pockethamster.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB4561C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3B1E08),
    secondary = Color(0xFF7A5A3F),
    secondaryContainer = Color(0xFFF6E3D0),
    onSecondaryContainer = Color(0xFF2E1D0E),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF2B2018),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF2B2018),
    surfaceVariant = Color(0xFFF1E2D4),
    onSurfaceVariant = Color(0xFF5B4637),
    surfaceContainer = Color(0xFFF8EBDF),
    outline = Color(0xFF8A7464),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF552100),
    primaryContainer = Color(0xFF6E3A15),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE2C0A4),
    secondaryContainer = Color(0xFF4A3828),
    onSecondaryContainer = Color(0xFFF6E3D0),
    background = Color(0xFF1C140F),
    onBackground = Color(0xFFF1DFD2),
    surface = Color(0xFF1C140F),
    onSurface = Color(0xFFF1DFD2),
    surfaceVariant = Color(0xFF3A2D23),
    onSurfaceVariant = Color(0xFFD8C2B2),
    surfaceContainer = Color(0xFF261C16),
    outline = Color(0xFFA08D7E),
)

@Composable
fun HamsterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
