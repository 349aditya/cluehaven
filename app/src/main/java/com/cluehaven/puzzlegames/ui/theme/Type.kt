package com.cluehaven.puzzlegames.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = 0.sp,
)
val Typography = Typography(
    headlineLarge = type(28, 34, FontWeight.Bold),
    headlineMedium = type(24, 30, FontWeight.Bold),
    headlineSmall = type(22, 28, FontWeight.Bold),
    titleLarge = type(20, 26, FontWeight.SemiBold),
    titleMedium = type(15, 20, FontWeight.SemiBold),
    titleSmall = type(13, 18, FontWeight.SemiBold),
    bodyLarge = type(14, 20),
    bodyMedium = type(13, 18),
    bodySmall = type(11, 16),
    labelLarge = type(13, 18, FontWeight.Medium),
    labelMedium = type(11, 15, FontWeight.Medium),
    labelSmall = type(10, 14),
)
