package com.github.mr3zee.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing

/**
 * Pill-shaped badge with tinted background and colored text.
 * Replaces ad-hoc Surface+pill badge implementations across the app.
 *
 * @param text The label to display
 * @param color The accent color — used at full opacity for text, 15% for background
 * @param modifier Optional modifier
 * @param testTag Optional test tag for the badge surface
 */
@Composable
fun RwBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val surfaceModifier = if (testTag != null) {
        modifier.then(Modifier.testTag(testTag))
    } else {
        modifier
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = AppShapes.pill,
        modifier = surfaceModifier,
    ) {
        Text(
            text = text,
            style = AppTypography.label,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
