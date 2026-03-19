package com.github.mr3zee.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors

enum class RwButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Danger,
}

@Composable
fun RwButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RwButtonVariant = RwButtonVariant.Ghost,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val resolvedContentColor = when {
        contentColor != Color.Unspecified -> contentColor
        variant == RwButtonVariant.Primary -> colors.buttonPrimaryText
        variant == RwButtonVariant.Danger -> Color.White
        variant == RwButtonVariant.Ghost -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bgColor = when (variant) {
        RwButtonVariant.Primary -> when {
            isPressed -> colors.buttonPrimaryPress
            isHovered -> colors.buttonPrimaryHover
            else -> colors.buttonPrimaryBg
        }
        RwButtonVariant.Secondary -> when {
            isPressed -> colors.buttonSecondaryPress
            isHovered -> colors.buttonSecondaryHover
            else -> colors.buttonSecondaryBg
        }
        RwButtonVariant.Ghost -> when {
            isPressed -> colors.buttonGhostPress
            isHovered -> colors.buttonGhostHover
            else -> Color.Transparent
        }
        RwButtonVariant.Danger -> when {
            isPressed -> colors.buttonDangerPress
            isHovered -> colors.buttonDangerHover
            else -> colors.buttonDangerBg
        }
    }

    val borderColor = when (variant) {
        RwButtonVariant.Secondary -> colors.buttonSecondaryBorder
        else -> Color.Unspecified
    }

    val borderWidth = when (variant) {
        RwButtonVariant.Secondary -> 1.dp
        else -> 0.dp
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = 50),
    )

    val focusRingColor = when (variant) {
        RwButtonVariant.Primary, RwButtonVariant.Danger -> colors.focusRingOnColor
        else -> Color.Unspecified
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .focusRing(cornerRadius = 8.dp, ringColor = focusRingColor, interactionSource = interactionSource)
            .scale(scale)
            .alpha(if (enabled || variant == RwButtonVariant.Primary) 1f else 0.6f),
        shape = AppShapes.sm,
        backgroundColor = if (!enabled && variant == RwButtonVariant.Primary) colors.chromeSurfaceSecondary else bgColor,
        contentColor = if (!enabled && variant == RwButtonVariant.Primary) colors.chromeTextTertiary else resolvedContentColor,
        contentPadding = contentPadding,
        borderColor = borderColor,
        borderWidth = borderWidth,
        role = Role.Button,
        indication = null,
        interactionSource = interactionSource,
    ) {
        val textStyle = when (variant) {
            RwButtonVariant.Primary, RwButtonVariant.Danger -> AppTypography.body
            else -> AppTypography.label
        }
        val effectiveContentColor = if (!enabled && variant == RwButtonVariant.Primary)
            colors.chromeTextTertiary else resolvedContentColor
        CompositionLocalProvider(
            LocalContentColor provides effectiveContentColor,
            LocalTextStyle provides textStyle,
        ) {
            content()
        }
    }
}

@Composable
fun RwIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        isPressed -> colors.buttonGhostPress
        isHovered -> colors.buttonGhostHover
        else -> Color.Transparent
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .focusRing(cornerRadius = 8.dp, interactionSource = interactionSource)
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.6f),
        shape = AppShapes.sm,
        backgroundColor = bgColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentPadding = PaddingValues(8.dp),
        role = Role.Button,
        indication = null,
        interactionSource = interactionSource,
    ) {
        content()
    }
}
