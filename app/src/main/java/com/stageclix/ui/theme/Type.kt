package com.stageclix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.stageclix.R

val StageclixFont = FontFamily(
    Font(R.font.font, FontWeight.Normal),
    Font(R.font.font, FontWeight.Medium),
    Font(R.font.font, FontWeight.Bold),
)

val StageclixTypography = Typography(
    bodyLarge    = TextStyle(fontFamily = StageclixFont),
    bodyMedium   = TextStyle(fontFamily = StageclixFont),
    bodySmall    = TextStyle(fontFamily = StageclixFont),
    labelLarge   = TextStyle(fontFamily = StageclixFont),
    labelMedium  = TextStyle(fontFamily = StageclixFont),
    labelSmall   = TextStyle(fontFamily = StageclixFont),
    titleLarge   = TextStyle(fontFamily = StageclixFont),
    titleMedium  = TextStyle(fontFamily = StageclixFont),
    titleSmall   = TextStyle(fontFamily = StageclixFont),
    displayLarge = TextStyle(fontFamily = StageclixFont),
    displayMedium = TextStyle(fontFamily = StageclixFont),
    displaySmall = TextStyle(fontFamily = StageclixFont),
)
