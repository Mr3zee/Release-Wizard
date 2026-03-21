package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom filter chip replacing M3 FilterChip.
 * Pill-shaped with pastel background when selected, subtle hover for unselected.
 */
@Composable
fun RwChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> colors.chipBgSelected
            isHovered -> colors.chromeSurfaceSecondary
            else -> colors.chipBg
        },
        animationSpec = tween(durationMillis = 100),
    )

    val borderColor = if (selected) colors.chipTextSelected else colors.chipBorder

    val textColor = if (selected) colors.chipTextSelected else colors.chipText

    FocusRingBox(
        cornerRadius = 50.dp,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(AppShapes.pill)
                .drawBehind { drawRect(bgColor) }
                .border(1.dp, borderColor, AppShapes.pill)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                label()
            }
        }
    }
}
