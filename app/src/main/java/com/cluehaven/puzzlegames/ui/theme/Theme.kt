package com.cluehaven.puzzlegames.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = Paper,
    onBackground = DarkTeal,
    surface = Color(0xFFFFFCF6),
    surfaceContainer = Color(0xFFFFFAF2),
    surfaceContainerLow = Color(0xFFFFFAF2),
    surfaceContainerHighest = Color(0xFFFFFAF2),
    onSurface = DarkTeal,
    onSurfaceVariant = Color(0xFF60777A),
    secondary = Mint,
    secondaryContainer = Color(0xFFE4EFE8),
    onSecondaryContainer = DarkTeal,
    primaryContainer = Color(0xFFD8EEE3),
    onPrimaryContainer = DarkTeal,
    outline = Color(0xFFABBDB7),
    outlineVariant = Color(0xFFE4E4DA),
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = DarkTeal,
    background = Color(0xFF102A30),
    onBackground = Color(0xFFF8F5EE),
    surface = Color(0xFF1C4148),
    surfaceContainer = Color(0xFF1C4148),
    surfaceContainerLow = Color(0xFF1C4148),
    surfaceContainerHighest = Color(0xFF1C4148),
    onSurface = Color(0xFFF8F5EE),
    onSurfaceVariant = Color(0xFFB8CECB),
    secondary = Mint,
    secondaryContainer = Teal,
    onSecondaryContainer = Color.White,
    primaryContainer = Color(0xFF28524D),
    onPrimaryContainer = Color(0xFFE7F4ED),
    outline = Color(0xFF73928C),
    outlineVariant = Color(0xFF33544F),
)

@Composable
fun CluehavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
