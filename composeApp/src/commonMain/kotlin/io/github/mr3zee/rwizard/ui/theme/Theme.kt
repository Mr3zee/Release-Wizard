package io.github.mr3zee.rwizard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = ColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E3FF),
    onPrimaryContainer = Color(0xFF001D35),
    inversePrimary = Color(0xFF66AFFF),

    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E),

    tertiary = Color(0xFF30D158),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF8D8),
    onTertiaryContainer = Color(0xFF00210C),

    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF2C2C2E),

    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),

    outline = Color(0x331C1C1E),
    outlineVariant = Color(0x1A1C1C1E),
    scrim = Color(0x66000000),

    surfaceBright = Color(0xFFF8F8FA),
    surfaceDim = Color(0xFFEFEFF4),
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFEDEDED),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),

    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),

    // Deprecated params, but required in constructor on some platforms
    primaryFixed = Color(0xFF0A84FF),
    primaryFixedDim = Color(0xFF0A84FF),
    onPrimaryFixed = Color.White,
    onPrimaryFixedVariant = Color.White,
    secondaryFixed = Color(0xFF8E8E93),
    secondaryFixedDim = Color(0xFF8E8E93),
    onSecondaryFixed = Color.White,
    onSecondaryFixedVariant = Color.White,
    tertiaryFixed = Color(0xFF30D158),
    tertiaryFixedDim = Color(0xFF30D158),
    onTertiaryFixed = Color.White,
    onTertiaryFixedVariant = Color.White,
)

private val DarkColors = LightColors.copy(
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFE5E5EA),
    outline = Color(0x33FFFFFF)
)

private val MacTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val MacShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

@Composable
fun ReleaseWizardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MacTypography,
        shapes = MacShapes,
        content = content
    )
}
