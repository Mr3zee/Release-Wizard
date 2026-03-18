package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.LocalAppColors

/**
 * Modifier that draws a focus ring **outside** the component bounds.
 * Uses `drawWithContent` with `graphicsLayer { clip = false }` so the ring
 * extends beyond the component's clipped area.
 *
 * This is safe to use even when the component applies `.clip()` internally
 * (like UnstyledButton) because the graphicsLayer creates a new layer that
 * renders the ring before the child's clip takes effect.
 */
fun Modifier.focusRing(
    cornerRadius: Dp,
    ringColor: Color = Color.Unspecified,
    interactionSource: InteractionSource,
): Modifier = composed {
    val ring = resolveFocusRing(cornerRadius, ringColor, interactionSource)

    this
        .graphicsLayer { clip = false }
        .drawWithContent {
            drawContent()
            drawFocusRing(ring)
        }
}

/**
 * Wrapper that draws a focus ring **outside** the child's bounds.
 * Use this for components that apply `.clip()` internally where the
 * modifier-based approach won't work (RwCard, RwChip, RwCheckbox, RwRadioButton).
 */
@Composable
fun FocusRingBox(
    cornerRadius: Dp,
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    ringColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val ring = resolveFocusRing(cornerRadius, ringColor, interactionSource)

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .drawWithContent {
                drawFocusRing(ring)
                drawContent()
            },
        content = content,
    )
}

private class FocusRingParams(
    val animatedColor: Color,
    val strokeWidth: Dp,
    val offset: Dp,
    val outerCornerRadius: Dp,
)

@Composable
private fun resolveFocusRing(
    cornerRadius: Dp,
    ringColor: Color,
    interactionSource: InteractionSource,
): FocusRingParams {
    val colors = LocalAppColors.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val resolvedColor = if (ringColor != Color.Unspecified) ringColor else colors.focusRing
    val targetColor = if (isFocused) resolvedColor else Color.Transparent

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 100),
    )

    val strokeWidth = 2.dp
    val gap = 2.dp

    return FocusRingParams(
        animatedColor = animatedColor,
        strokeWidth = strokeWidth,
        offset = strokeWidth / 2 + gap,
        outerCornerRadius = cornerRadius + gap + strokeWidth / 2,
    )
}

private fun DrawScope.drawFocusRing(ring: FocusRingParams) {
    if (ring.animatedColor != Color.Transparent) {
        val strokePx = ring.strokeWidth.toPx()
        val offsetPx = ring.offset.toPx()
        val cr = CornerRadius(ring.outerCornerRadius.toPx())
        drawRoundRect(
            color = ring.animatedColor,
            topLeft = Offset(-offsetPx, -offsetPx),
            size = Size(size.width + offsetPx * 2, size.height + offsetPx * 2),
            cornerRadius = cr,
            style = Stroke(width = strokePx),
        )
    }
}
