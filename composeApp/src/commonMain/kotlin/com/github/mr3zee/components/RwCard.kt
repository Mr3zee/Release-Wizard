package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom card replacing M3 Card.
 * Uses border + subtle shadow (light mode) or border + elevated surface (dark mode).
 * Supports optional click behavior with hover feedback.
 */
@Composable
fun RwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
    val isHovered = if (interactionSource != null) {
        val state by interactionSource.collectIsHoveredAsState()
        state
    } else false

    val bgColor = when {
        containerColor != Color.Unspecified -> containerColor
        isHovered && onClick != null -> colors.chromeSurfaceSecondary
        else -> colors.chromeSurface
    }

    val animatedBg by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(durationMillis = 100),
    )

    val clickMod = if (onClick != null && interactionSource != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    val cardContent: @Composable BoxScope.() -> Unit = {
        Box(
            modifier = Modifier
                .shadow(1.dp, AppShapes.md)
                .clip(AppShapes.md)
                .drawBehind { drawRect(animatedBg) }
                .border(1.dp, colors.chromeBorder, AppShapes.md)
                .then(clickMod),
            content = content,
        )
    }

    // Wrap in FocusRingBox only for clickable cards (focus ring drawn outside clip)
    if (interactionSource != null) {
        FocusRingBox(
            cornerRadius = 10.dp,
            interactionSource = interactionSource,
            modifier = modifier,
            content = cardContent,
        )
    } else {
        Box(modifier = modifier, content = cardContent)
    }
}
