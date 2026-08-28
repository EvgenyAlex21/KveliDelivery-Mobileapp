package com.kvelidelivery.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF1E88E5)
val PrimaryDark = Color(0xFF1565C0)
val AccentGreen = Color(0xFF43A047)
val AccentOrange = Color(0xFFFB8C00)
val AccentRed = Color(0xFFE53935)
val SurfaceLight = Color(0xFFF5F7FA)
val SurfaceDark = Color(0xFF121212)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF1E1E1E)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = AccentGreen,
    onSecondary = Color.White,
    tertiary = AccentOrange,
    background = SurfaceLight,
    onBackground = Color(0xFF1A1A1A),
    surface = CardLight,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF5A6570),
    error = AccentRed,
    outline = Color(0xFFB0BEC5)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF1B5E20),
    tertiary = Color(0xFFFFB74D),
    background = SurfaceDark,
    onBackground = Color(0xFFE0E0E0),
    surface = CardDark,
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFEF5350),
    outline = Color(0xFF546E7A)
)

@Composable
fun KveliDeliveryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
