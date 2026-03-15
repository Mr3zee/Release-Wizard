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
    /** Filled primary background */
    Primary,
    /** Outlined with border */
    Secondary,
    /** Text-only, no background or border (replaces M3 TextButton) */
    Ghost,
    /** Filled danger/error background */
    Danger,
}

/**
 * Custom button component replacing M3 Button/TextButton/OutlinedButton.
 * Uses Compose Unstyled UnstyledButton for accessibility and keyboard support.
 * Provides hover/press feedback via color shift + subtle scale (no ripple).
 */
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
        // Ghost buttons with no explicit color use primary blue for affordance
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

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.6f),
        shape = AppShapes.sm,
        backgroundColor = bgColor,
        contentColor = resolvedContentColor,
        contentPadding = contentPadding,
        borderColor = borderColor,
        borderWidth = borderWidth,
        role = Role.Button,
        indication = null,
        interactionSource = interactionSource,
    ) {
        // Primary/Danger CTAs get body (14sp); Ghost/Secondary get label (12sp)
        val textStyle = when (variant) {
            RwButtonVariant.Primary, RwButtonVariant.Danger -> AppTypography.body
            else -> AppTypography.label
        }
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
            LocalTextStyle provides textStyle,
        ) {
            content()
        }
    }
}

/**
 * Icon-only button with hover background.
 */
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
            .size(40.dp)
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
