package com.cluehaven.puzzlegames.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = Paper,
    onBackground = DarkTeal,
    surface = Color.White,
    onSurface = DarkTeal,
    onSurfaceVariant = Color(0xFF60777A),
    secondary = Mint,
    secondaryContainer = Mint,
    onSecondaryContainer = DarkTeal,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = DarkTeal,
    background = Color(0xFF102A30),
    onBackground = Color(0xFFF8F5EE),
    surface = Color(0xFF1C4148),
    onSurface = Color(0xFFF8F5EE),
    onSurfaceVariant = Color(0xFFB8CECB),
    secondary = Mint,
    secondaryContainer = Teal,
    onSecondaryContainer = Color.White,
)

@Composable
fun CluehavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
