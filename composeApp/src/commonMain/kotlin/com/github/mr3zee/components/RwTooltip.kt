package com.github.mr3zee.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Custom tooltip that renders in a Popup layer to avoid clipping by sibling composables.
 * Hover detection via pointerInput; tooltip panel displayed via Popup for correct z-ordering.
 */
@Composable
fun RwTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    hoverDelayMillis: Long = 500L,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    var isHovered by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(hoverDelayMillis)
            showTooltip = true
        } else {
            showTooltip = false
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    when (event.type) {
                        PointerEventType.Enter -> isHovered = true
                        PointerEventType.Exit -> isHovered = false
                    }
                }
            }
        },
    ) {
        content()

        if (showTooltip) {
            Popup(
                popupPositionProvider = TooltipPositionProvider,
                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .background(colors.tooltipBg, AppShapes.xs)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = tooltip,
                        style = AppTypography.caption,
                        color = colors.tooltipText,
                    )
                }
            }
        }
    }
}

/** Positions tooltip centered above anchor, flipping below if insufficient space. */
private object TooltipPositionProvider : PopupPositionProvider {
    private const val GAP = 4

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.top - popupContentSize.height - GAP

        val finalY = if (y < 0) {
            anchorBounds.bottom + GAP
        } else {
            y
        }
        val finalX = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        return IntOffset(finalX, finalY)
    }
}
