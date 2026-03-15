package com.github.mr3zee.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.mr3zee.i18n.LanguagePack
import com.github.mr3zee.i18n.LanguagePackRegistry
import com.github.mr3zee.i18n.LocalLanguagePackData

// ── Light color scheme ──────────────────────────────────────────────────────
// Maps our design tokens into M3 color roles so retained M3 components
// (Scaffold, TopAppBar, AlertDialog, etc.) pick up the new palette.
private val AppLightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A5F),
    secondary = Color(0xFF6366F1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    // Chrome surface colors mapped into M3 roles
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFF5F6F8),
    surfaceContainerHighest = Color(0xFFF0F1F3),
    inverseSurface = Color(0xFF1F2937),
    inversePrimary = Color(0xFF93C5FD),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFE5E7EB),
)

// ── Dark color scheme ───────────────────────────────────────────────────────
private val AppDarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF1E3A5F),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2),
    // Chrome surface colors for dark
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1E2230),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainer = Color(0xFF1E2230),
    surfaceContainerLow = Color(0xFF1A1D27),
    surfaceContainerHigh = Color(0xFF22252F),
    surfaceContainerHighest = Color(0xFF282C3A),
    inverseSurface = Color(0xFFE5E7EB),
    inversePrimary = Color(0xFF1E40AF),
    outline = Color(0xFF3A3F50),
    outlineVariant = Color(0xFF3A3F50),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    languagePack: LanguagePack = LanguagePack.ENGLISH,
    content: @Composable () -> Unit,
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val appColors = if (isDark) DarkAppColors else LightAppColors
    val colorScheme = if (isDark) AppDarkColorScheme else AppLightColorScheme
    val packData = remember(languagePack) { LanguagePackRegistry.getData(languagePack) }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalLanguagePackData provides packData,
        // Suppress M3 ripple globally — Rw* components use custom hover/press indication
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppMaterialTypography,
            shapes = AppMaterialShapes,
            content = content,
        )
    }
}
