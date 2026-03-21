package com.github.mr3zee.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.composeunstyled.Tooltip
import com.composeunstyled.TooltipPanel
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom tooltip replacing M3 TooltipBox + PlainTooltip.
 * Uses Compose Unstyled Tooltip for positioning and hover detection.
 */
@Composable
fun RwTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current

    Box(modifier = modifier) {
        Tooltip(
            enabled = true,
            panel = {
                TooltipPanel(
                    modifier = Modifier.zIndex(15f),
                    shape = AppShapes.xs,
                    backgroundColor = colors.tooltipBg,
                    contentColor = colors.tooltipText,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = tooltip,
                        style = AppTypography.caption,
                        color = colors.tooltipText,
                    )
                }
            },
            anchor = content,
        )
    }
}
