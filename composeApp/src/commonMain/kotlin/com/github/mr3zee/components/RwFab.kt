package com.github.mr3zee.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing

@Composable
fun RwFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        isPressed -> colors.buttonPrimaryPress
        isHovered -> colors.buttonPrimaryHover
        else -> colors.buttonPrimaryBg
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 50),
    )

    UnstyledButton(
        onClick = onClick,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
            .focusRing(cornerRadius = 14.dp, ringColor = colors.focusRingOnColor, interactionSource = interactionSource)
            .scale(scale)
            .shadow(6.dp, AppShapes.lg),
        shape = AppShapes.lg,
        backgroundColor = bgColor,
        contentColor = colors.buttonPrimaryText,
        contentPadding = PaddingValues(Spacing.lg),
        role = Role.Button,
        indication = null,
        interactionSource = interactionSource,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.buttonPrimaryText) {
            content()
        }
    }
}
