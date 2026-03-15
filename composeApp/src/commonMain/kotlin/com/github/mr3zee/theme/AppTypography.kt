package com.github.mr3zee.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Custom type scale using system fonts (SF Pro / Segoe UI / system sans-serif).
 * Floor is 12sp for body text accessibility. Monospace style for code/IDs.
 */
object AppTypography {
    /** Page titles, hero text — 28sp SemiBold */
    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Section headings — 18sp SemiBold */
    val heading = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    )

    /** Sub-section headers, panel titles — 15sp Medium */
    val subheading = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** Primary body text — 14sp Normal */
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** Secondary body text — 13sp Normal */
    val bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Button labels, form labels — 12sp Medium */
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    )

    /** Captions, metadata — 12sp Normal (floor, accessible) */
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    )

    /** Code, template expressions, IDs — 13sp Monospace */
    val code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

/**
 * Maps our custom type scale into M3's Typography so that retained M3 components
 * (TopAppBar, AlertDialog, etc.) pick up the new type system automatically.
 */
val AppMaterialTypography = Typography(
    displayLarge = AppTypography.display,
    displayMedium = AppTypography.display,
    displaySmall = AppTypography.heading,
    headlineLarge = AppTypography.display,
    headlineMedium = AppTypography.display,
    headlineSmall = AppTypography.heading,
    titleLarge = AppTypography.heading,
    titleMedium = AppTypography.heading,
    titleSmall = AppTypography.subheading,
    bodyLarge = AppTypography.body,
    bodyMedium = AppTypography.body,
    bodySmall = AppTypography.bodySmall,
    labelLarge = AppTypography.label,
    labelMedium = AppTypography.label,
    labelSmall = AppTypography.caption,
)
