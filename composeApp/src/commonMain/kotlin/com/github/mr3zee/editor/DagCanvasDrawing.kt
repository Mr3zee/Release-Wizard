package com.github.mr3zee.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.github.mr3zee.model.*
import com.github.mr3zee.theme.AppColors
import kotlin.math.PI

// Default block dimensions in dp (logical coordinates) — kept for backward compat references
internal const val BLOCK_WIDTH = BlockPosition.DEFAULT_BLOCK_WIDTH
internal const val BLOCK_HEIGHT = BlockPosition.DEFAULT_BLOCK_HEIGHT
internal const val PORT_RADIUS = 8f
internal const val PORT_HIT_RADIUS = 14f
internal const val GRID_SIZE = 20f
internal const val MIN_ZOOM = 0.25f
internal const val MAX_ZOOM = 4f
internal const val RESIZE_HANDLE_WIDTH = 6f

enum class ResizeEdge {
    Top, Bottom, Left, Right,
    TopLeft, TopRight, BottomLeft, BottomRight;

    val isCorner: Boolean get() = when (this) {
        TopLeft, TopRight, BottomLeft, BottomRight -> true
        else -> false
    }
}

internal fun blockColor(block: Block, colors: AppColors): Color = when (block) {
    is Block.ActionBlock -> blockTypeColor(block.type, colors)
    is Block.ContainerBlock -> colors.containerBlock
}

internal fun blockTypeColor(type: BlockType, colors: AppColors): Color = when (type) {
    BlockType.TEAMCITY_BUILD -> colors.teamcityBuild
    BlockType.GITHUB_ACTION -> colors.githubAction
    BlockType.GITHUB_PUBLICATION -> colors.githubPublication
    BlockType.SLACK_MESSAGE -> colors.slackMessage
}

// Coordinate transform
internal class CanvasTransform(
    val zoom: Float,
    val panOffset: Offset,
    val density: Float,
) {
    fun toScreenX(dpX: Float): Float = dpX * density * zoom + panOffset.x
    fun toScreenY(dpY: Float): Float = dpY * density * zoom + panOffset.y
    fun toScreen(dp: Float): Float = dp * density * zoom

    fun toLogical(screenPos: Offset): Offset = Offset(
        (screenPos.x - panOffset.x) / (density * zoom),
        (screenPos.y - panOffset.y) / (density * zoom),
    )

    fun toLogicalDelta(screenDelta: Offset): Offset = Offset(
        screenDelta.x / (density * zoom),
        screenDelta.y / (density * zoom),
    )
}

/**
 * Process a pointer event for scroll-to-zoom. Consumes the scroll change
 * and returns the new (zoom, panOffset) pair, or null if the event is not a scroll.
 */
internal fun applyScrollZoom(
    event: PointerEvent,
    zoom: Float,
    panOffset: Offset,
    density: Float,
): Pair<Float, Offset>? {
    if (event.type != PointerEventType.Scroll) return null
    val change = event.changes.firstOrNull() ?: return null
    val result = handleScrollZoom(change.scrollDelta.y, change.position, zoom, panOffset, density) ?: return null
    change.consume()
    return result
}

/**
 * Shared scroll-to-zoom logic for canvas pointer input.
 * Returns the new (zoom, panOffset) pair, or null if no scroll occurred.
 */
internal fun handleScrollZoom(
    scrollY: Float,
    pointerPos: Offset,
    zoom: Float,
    panOffset: Offset,
    density: Float,
): Pair<Float, Offset>? {
    if (scrollY == 0f) return null
    // Proportional zoom: larger scroll delta = faster zoom.
    // scrollY sign: negative = zoom in, positive = zoom out (macOS convention).
    // Works for both discrete scroll wheel ticks and continuous trackpad pinch.
    val factor = (1f - scrollY * 0.03f).coerceIn(0.5f, 2f)
    val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val logicalBefore = Offset(
        (pointerPos.x - panOffset.x) / (density * zoom),
        (pointerPos.y - panOffset.y) / (density * zoom),
    )
    val newPanOffset = Offset(
        pointerPos.x - logicalBefore.x * density * newZoom,
        pointerPos.y - logicalBefore.y * density * newZoom,
    )
    return newZoom to newPanOffset
}

internal fun DrawScope.drawGrid(transform: CanvasTransform, colors: AppColors) {
    // Minor grid lines (every GRID_SIZE = 20 logical units)
    val minorScreenSize = transform.toScreen(GRID_SIZE)
    if (minorScreenSize >= 8f) {
        val startX = transform.panOffset.x % minorScreenSize
        val startY = transform.panOffset.y % minorScreenSize

        var x = startX
        while (x < size.width) {
            drawLine(colors.canvasGridMinor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += minorScreenSize
        }
        var y = startY
        while (y < size.height) {
            drawLine(colors.canvasGridMinor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            y += minorScreenSize
        }
    }

    // Major grid lines (every 100 logical units = 5 * GRID_SIZE)
    val majorScreenSize = transform.toScreen(GRID_SIZE * 5f)
    if (majorScreenSize >= 20f) {
        val startX = transform.panOffset.x % majorScreenSize
        val startY = transform.panOffset.y % majorScreenSize

        var x = startX
        while (x < size.width) {
            drawLine(colors.canvasGridMajor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.0f)
            x += majorScreenSize
        }
        var y = startY
        while (y < size.height) {
            drawLine(colors.canvasGridMajor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.0f)
            y += majorScreenSize
        }
    }
}

internal fun DrawScope.drawBlock(
    transform: CanvasTransform,
    block: Block,
    position: BlockPosition,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
    colors: AppColors,
    typeLabel: String,
    fillColor: Color = blockColor(block, colors),
) {
    val screenX = transform.toScreenX(position.x)
    val screenY = transform.toScreenY(position.y)
    val screenW = transform.toScreen(position.width)
    val screenH = transform.toScreen(position.height)
    val cornerRadius = transform.toScreen(8f)

    // Shadow (offset scaled with zoom)
    val shadowOffset = transform.toScreen(1.5f)
    drawRoundRect(
        color = colors.blockShadow,
        topLeft = Offset(screenX + shadowOffset, screenY + shadowOffset),
        size = Size(screenW, screenH),
        cornerRadius = CornerRadius(cornerRadius),
    )

    // Background
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(screenX, screenY),
        size = Size(screenW, screenH),
        cornerRadius = CornerRadius(cornerRadius),
    )

    // Inner highlight (thin light line at top edge for raised-panel effect)
    val highlightInset = transform.toScreen(1.5f)
    drawRoundRect(
        color = colors.blockInnerHighlight,
        topLeft = Offset(screenX + highlightInset, screenY + highlightInset),
        size = Size(screenW - highlightInset * 2, screenH - highlightInset * 2),
        cornerRadius = CornerRadius(cornerRadius - highlightInset),
        style = Stroke(width = transform.toScreen(0.75f)),
    )

    // Selection border
    if (isSelected) {
        drawRoundRect(
            color = colors.blockSelectionHighlight,
            topLeft = Offset(screenX, screenY),
            size = Size(screenW, screenH),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = transform.toScreen(2.5f)),
        )
    }

    // Name text
    val nameSize = (14f * zoom).coerceIn(6f, 42f)
    val nameLayout = textMeasurer.measure(
        block.name,
        style = TextStyle(fontSize = nameSize.sp, color = colors.blockText),
    )
    drawText(
        nameLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + transform.toScreen(12f)),
    )

    // Description text — shown below name, more lines as block grows taller
    if (block.description.isNotBlank()) {
        val descSize = (12f * zoom).coerceIn(5f, 36f)
        val descTopY = transform.toScreen(34f)
        val typeLabelHeight = transform.toScreen(16f) // reserve space for type label at bottom
        val availableHeight = screenH - descTopY - typeLabelHeight
        if (availableHeight > 0f) {
            val maxWidth = (screenW - transform.toScreen(20f)).toInt().coerceAtLeast(1)
            val lineHeight = descSize * transform.density * 1.3f
            val maxLines = (availableHeight / lineHeight).toInt().coerceAtLeast(1)
            val descLayout = textMeasurer.measure(
                block.description,
                style = TextStyle(fontSize = descSize.sp, color = colors.blockTextSecondary),
                maxLines = maxLines,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth),
            )
            drawText(
                descLayout,
                topLeft = Offset(screenX + transform.toScreen(10f), screenY + descTopY),
            )
        }
    }

    // Type label — bottom-left corner
    val typeSize = (9f * zoom).coerceIn(4f, 26f)
    val typeLayout = textMeasurer.measure(
        typeLabel,
        style = TextStyle(fontSize = typeSize.sp, color = colors.blockTextSecondary),
    )
    drawText(
        typeLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + screenH - typeLayout.size.height - transform.toScreen(6f)),
    )

    // Gate badge indicators
    if (block is Block.ActionBlock) {
        val badgeRadius = transform.toScreen(5f)
        val strokeWidth = transform.toScreen(1.5f)
        if (block.preGate != null) {
            // Pre-gate: filled circle at top-left corner
            drawCircle(
                color = colors.gateIndicator,
                radius = badgeRadius,
                center = Offset(screenX + badgeRadius * 2, screenY),
            )
        }
        if (block.postGate != null) {
            // Post-gate: ring at top-right corner (visually distinct from pre-gate)
            drawCircle(
                color = colors.gateIndicator,
                radius = badgeRadius,
                center = Offset(screenX + screenW - badgeRadius * 2, screenY),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

internal fun DrawScope.drawContainerBlock(
    transform: CanvasTransform,
    container: Block.ContainerBlock,
    position: BlockPosition,
    isSelected: Boolean,
    isDropTarget: Boolean,
    isDetaching: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
    colors: AppColors,
) {
    val screenX = transform.toScreenX(position.x)
    val screenY = transform.toScreenY(position.y)
    val screenW = transform.toScreen(position.width)
    val screenH = transform.toScreen(position.height)
    val cornerRadius = transform.toScreen(8f)
    val headerHeight = transform.toScreen(position.headerHeight)

    // Subtle fill at 8% opacity (distinguishes container area from empty canvas)
    drawRoundRect(
        color = colors.containerBlock.copy(alpha = 0.08f),
        topLeft = Offset(screenX, screenY),
        size = Size(screenW, screenH),
        cornerRadius = CornerRadius(cornerRadius),
    )

    // Dashed border
    val borderColor = when {
        isDropTarget -> colors.containerDropHighlight
        isDetaching -> colors.containerDetachHighlight
        isSelected -> colors.blockSelectionHighlight
        else -> colors.containerBorder
    }
    val borderWidth = if (isDropTarget || isDetaching || isSelected) transform.toScreen(2.5f) else transform.toScreen(1.5f)
    val dashLength = transform.toScreen(8f)
    val gapLength = transform.toScreen(4f)
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(screenX, screenY),
        size = Size(screenW, screenH),
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(
            width = borderWidth,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength)),
        ),
    )

    // Info header background (filled, top portion only)
    val headerPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = screenX,
                top = screenY,
                right = screenX + screenW,
                bottom = screenY + headerHeight,
                topLeftCornerRadius = CornerRadius(cornerRadius),
                topRightCornerRadius = CornerRadius(cornerRadius),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
    }
    drawPath(headerPath, color = colors.containerHeaderBg)

    // Divider line between header and content (inset by corner radius to avoid overshoot)
    val dividerInset = cornerRadius * 0.3f
    drawLine(
        color = colors.containerBorder,
        start = Offset(screenX + dividerInset, screenY + headerHeight),
        end = Offset(screenX + screenW - dividerInset, screenY + headerHeight),
        strokeWidth = transform.toScreen(1f),
    )

    // Child count badge (top-right of header)
    val childCount = container.children.blocks.size
    val countSize = (10f * zoom).coerceIn(4f, 28f)
    val countText = "$childCount block${if (childCount != 1) "s" else ""}"
    val countLayout = textMeasurer.measure(
        countText,
        style = TextStyle(fontSize = countSize.sp, color = colors.chromeTextSecondary),
        maxLines = 1,
    )
    val padding = transform.toScreen(10f)
    val countX = screenX + screenW - countLayout.size.width - padding

    // Container name (top of header)
    val nameSize = (13f * zoom).coerceIn(5f, 38f)
    val nameMaxWidth = (countX - screenX - transform.toScreen(20f)).toInt().coerceAtLeast(1)
    val nameLayout = textMeasurer.measure(
        container.name,
        style = TextStyle(fontSize = nameSize.sp, color = colors.chromeTextPrimary),
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = nameMaxWidth),
    )
    val nameY = screenY + transform.toScreen(6f)
    drawText(nameLayout, topLeft = Offset(screenX + padding, nameY))

    // Draw count badge with pill background (aligned with name)
    val badgePadH = transform.toScreen(4f)
    val badgePadV = transform.toScreen(2f)
    val badgeY = nameY + (nameLayout.size.height - countLayout.size.height) / 2
    drawRoundRect(
        color = colors.containerBorder.copy(alpha = 0.2f),
        topLeft = Offset(countX - badgePadH, badgeY - badgePadV),
        size = Size(countLayout.size.width + badgePadH * 2, countLayout.size.height.toFloat() + badgePadV * 2),
        cornerRadius = CornerRadius(transform.toScreen(4f)),
    )
    drawText(countLayout, topLeft = Offset(countX, badgeY))

    // Description in header (below name, fills remaining header space)
    if (container.description.isNotBlank()) {
        val descSize = (12f * zoom).coerceIn(5f, 36f)
        val descTopY = nameY + nameLayout.size.height + transform.toScreen(2f)
        val availableHeight = headerHeight - (descTopY - screenY) - transform.toScreen(4f)
        if (availableHeight > 0f) {
            val maxWidth = (screenW - transform.toScreen(20f)).toInt().coerceAtLeast(1)
            val lineHeight = descSize * transform.density * 1.3f
            val maxLines = (availableHeight / lineHeight).toInt().coerceAtLeast(1)
            val descLayout = textMeasurer.measure(
                container.description,
                style = TextStyle(fontSize = descSize.sp, color = colors.chromeTextSecondary),
                maxLines = maxLines,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth),
            )
            drawText(descLayout, topLeft = Offset(screenX + padding, descTopY))
        }
    }

    // Empty container placeholder
    if (childCount == 0) {
        val hintSize = (10f * zoom).coerceIn(4f, 28f)
        val hintLayout = textMeasurer.measure(
            "Drop blocks here",
            style = TextStyle(fontSize = hintSize.sp, color = colors.chromeTextTertiary),
            maxLines = 1,
        )
        val contentCenterY = screenY + headerHeight + (screenH - headerHeight) / 2
        drawText(
            hintLayout,
            topLeft = Offset(
                screenX + (screenW - hintLayout.size.width) / 2,
                contentCenterY - hintLayout.size.height / 2,
            ),
        )
    }
}

/** Compute the Y offset for port placement. Always centered on the block height. */
internal fun portYOffset(position: BlockPosition): Float =
    position.height / 2

internal fun DrawScope.drawPorts(
    transform: CanvasTransform,
    position: BlockPosition,
    isInputHovered: Boolean,
    isOutputHovered: Boolean,
    colors: AppColors,
    showInput: Boolean = true,
    showOutput: Boolean = true,
) {
    val portScreenRadius = transform.toScreen(PORT_RADIUS)
    val yOff = portYOffset(position)

    if (showInput) {
        val inX = transform.toScreenX(position.x)
        val inY = transform.toScreenY(position.y + yOff)
        drawPort(transform, portScreenRadius, Offset(inX, inY), isInputHovered, colors)
    }

    if (showOutput) {
        val outX = transform.toScreenX(position.x + position.width)
        val outY = transform.toScreenY(position.y + yOff)
        drawPort(transform, portScreenRadius, Offset(outX, outY), isOutputHovered, colors)
    }
}

private fun DrawScope.drawPort(
    transform: CanvasTransform,
    portScreenRadius: Float,
    center: Offset,
    isHovered: Boolean,
    colors: AppColors,
) {
    if (isHovered) {
        drawCircle(
            color = colors.portHoverRing,
            radius = portScreenRadius * 2f,
            center = center,
        )
    }
    val portColor = if (isHovered) colors.portHover else colors.portDefault
    drawCircle(
        color = portColor,
        radius = portScreenRadius,
        center = center,
        style = Stroke(width = transform.toScreen(1f)),
    )
    drawCircle(
        color = portColor,
        radius = portScreenRadius * 0.5f,
        center = center,
    )
}

internal fun DrawScope.drawEdge(
    transform: CanvasTransform,
    fromPos: BlockPosition,
    toPos: BlockPosition,
    isSelected: Boolean,
    colors: AppColors,
) {
    val startX = transform.toScreenX(fromPos.x + fromPos.width)
    val startY = transform.toScreenY(fromPos.y + portYOffset(fromPos))
    val endX = transform.toScreenX(toPos.x)
    val endY = transform.toScreenY(toPos.y + portYOffset(toPos))

    val dx = endX - startX
    val cp1x = startX + dx * 0.4f
    val cp2x = endX - dx * 0.4f
    val arrowSize = transform.toScreen(8f)
    val edgeColor = if (isSelected) colors.edgeSelected else colors.edgeDefault

    // Compute arrow direction from full curve (sampled at t=0.9)
    val t = 0.9f; val inv = 1f - t
    val nearX = inv*inv*inv*startX + 3*inv*inv*t*cp1x + 3*inv*t*t*cp2x + t*t*t*endX
    val nearY = inv*inv*inv*startY + 3*inv*inv*t*startY + 3*inv*t*t*endY + t*t*t*endY
    val adx = endX - nearX; val ady = endY - nearY
    val len = kotlin.math.sqrt(adx * adx + ady * ady).coerceAtLeast(0.001f)
    val nx = adx / len; val ny = ady / len

    // Visible curve ends at arrow base (so stroke doesn't poke past the arrowhead)
    val cropX = endX - arrowSize * 0.7f * nx
    val cropY = endY - arrowSize * 0.7f * ny
    val visiblePath = Path().apply {
        moveTo(startX, startY)
        cubicTo(cp1x, startY, cp2x, endY, cropX, cropY)
    }

    val baseStroke = transform.toScreen(2.5f)
    val selectedStroke = transform.toScreen(3f)
    if (isSelected) {
        drawPath(visiblePath, color = colors.edgeGlow, style = Stroke(width = transform.toScreen(5f)))
    }
    drawPath(
        visiblePath,
        color = if (isSelected) colors.edgeSelected else colors.edgeDefault,
        style = Stroke(width = if (isSelected) selectedStroke else baseStroke),
    )

    // Arrowhead: tip at port, base behind it along approach direction
    val arrowPath = Path().apply {
        moveTo(endX, endY)
        lineTo(endX - arrowSize * nx + arrowSize * 0.5f * ny,
               endY - arrowSize * ny - arrowSize * 0.5f * nx)
        lineTo(endX - arrowSize * nx - arrowSize * 0.5f * ny,
               endY - arrowSize * ny + arrowSize * 0.5f * nx)
        close()
    }
    drawPath(arrowPath, color = edgeColor)
}

internal fun DrawScope.drawDraftEdge(start: Offset, end: Offset, colors: AppColors, transform: CanvasTransform? = null, colorOverride: Color? = null) {
    val dx = end.x - start.x
    val cp1x = start.x + dx * 0.4f
    val cp2x = end.x - dx * 0.4f

    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(cp1x, start.y, cp2x, end.y, end.x, end.y)
    }

    val strokeWidth = transform?.toScreen(1.5f) ?: 2f
    val dashLength = transform?.toScreen(6f) ?: 8f
    val gapLength = transform?.toScreen(3f) ?: 4f

    drawPath(
        path,
        color = colorOverride ?: colors.draftEdge,
        style = Stroke(
            width = strokeWidth,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength)),
        ),
    )
}

internal fun DrawScope.drawRunningIndicator(
    transform: CanvasTransform,
    position: BlockPosition,
    phase: Float, // 0 to 2π
    colors: AppColors,
) {
    val screenX = transform.toScreenX(position.x)
    val screenY = transform.toScreenY(position.y)
    val screenW = transform.toScreen(position.width)
    val screenH = transform.toScreen(position.height)
    val dotRadius = transform.toScreen(3f)
    val clampedRadius = dotRadius.coerceAtLeast(1.5f)

    // Orbit the dot around the block perimeter
    val perimeter = 2 * (screenW + screenH)
    val dist = (phase / (2f * PI.toFloat())) * perimeter

    val (dotX, dotY) = when {
        dist < screenW -> Offset(screenX + dist, screenY) // top edge
        dist < screenW + screenH -> Offset(screenX + screenW, screenY + (dist - screenW)) // right edge
        dist < 2 * screenW + screenH -> Offset(screenX + screenW - (dist - screenW - screenH), screenY + screenH) // bottom edge
        else -> Offset(screenX, screenY + screenH - (dist - 2 * screenW - screenH)) // left edge
    }

    drawCircle(
        color = colors.blockStatusRunning,
        radius = clampedRadius,
        center = Offset(dotX, dotY),
    )
    // Subtle glow behind the dot
    drawCircle(
        color = colors.blockStatusRunning.copy(alpha = 0.3f),
        radius = clampedRadius * 2.5f,
        center = Offset(dotX, dotY),
    )
}

internal fun DrawScope.drawBlockStatusIcon(
    transform: CanvasTransform,
    position: BlockPosition,
    status: BlockStatus,
    colors: AppColors,
) {
    val screenX = transform.toScreenX(position.x)
    val screenY = transform.toScreenY(position.y)
    val screenW = transform.toScreen(position.width)
    val screenH = transform.toScreen(position.height)
    val iconSize = transform.toScreen(8f)
    if (iconSize < 4f) return
    val iconCenterX = screenX + screenW - transform.toScreen(14f)
    val iconCenterY = screenY + screenH - transform.toScreen(14f)
    val strokeWidth = transform.toScreen(1.5f)

    val iconColor = when (status) {
        BlockStatus.SUCCEEDED -> colors.blockStatusSucceeded
        BlockStatus.FAILED -> colors.blockStatusFailed
        BlockStatus.WAITING -> colors.blockStatusWaiting
        BlockStatus.WAITING_FOR_INPUT -> colors.blockStatusWaitingForInput
        BlockStatus.RUNNING -> return // spinner dot handles this
        BlockStatus.STOPPED -> colors.blockStatusStopped
    }

    // Draw white background circle for contrast
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = iconSize + transform.toScreen(2f),
        center = Offset(iconCenterX, iconCenterY),
    )

    when (status) {
        BlockStatus.SUCCEEDED -> {
            // Checkmark using lines instead of Path to avoid per-frame allocation
            drawLine(iconColor,
                Offset(iconCenterX - iconSize * 0.6f, iconCenterY),
                Offset(iconCenterX - iconSize * 0.1f, iconCenterY + iconSize * 0.5f),
                strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(iconColor,
                Offset(iconCenterX - iconSize * 0.1f, iconCenterY + iconSize * 0.5f),
                Offset(iconCenterX + iconSize * 0.6f, iconCenterY - iconSize * 0.4f),
                strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        BlockStatus.FAILED -> {
            // X mark
            drawLine(iconColor, Offset(iconCenterX - iconSize * 0.4f, iconCenterY - iconSize * 0.4f), Offset(iconCenterX + iconSize * 0.4f, iconCenterY + iconSize * 0.4f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(iconColor, Offset(iconCenterX - iconSize * 0.4f, iconCenterY + iconSize * 0.4f), Offset(iconCenterX + iconSize * 0.4f, iconCenterY - iconSize * 0.4f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        BlockStatus.WAITING -> {
            // Clock circle
            drawCircle(iconColor, radius = iconSize * 0.6f, center = Offset(iconCenterX, iconCenterY), style = Stroke(width = strokeWidth))
            // Hour/minute hands
            drawLine(iconColor, Offset(iconCenterX, iconCenterY), Offset(iconCenterX, iconCenterY - iconSize * 0.35f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(iconColor, Offset(iconCenterX, iconCenterY), Offset(iconCenterX + iconSize * 0.25f, iconCenterY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        BlockStatus.WAITING_FOR_INPUT -> {
            // Exclamation mark
            drawLine(iconColor, Offset(iconCenterX, iconCenterY - iconSize * 0.5f), Offset(iconCenterX, iconCenterY + iconSize * 0.15f), strokeWidth = strokeWidth * 1.2f, cap = StrokeCap.Round)
            drawCircle(iconColor, radius = strokeWidth * 0.8f, center = Offset(iconCenterX, iconCenterY + iconSize * 0.45f))
        }
        BlockStatus.STOPPED -> {
            // Pause icon (two vertical bars)
            val barGap = iconSize * 0.3f
            val barHeight = iconSize * 0.85f
            drawLine(iconColor,
                Offset(iconCenterX - barGap, iconCenterY - barHeight / 2),
                Offset(iconCenterX - barGap, iconCenterY + barHeight / 2),
                strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
            drawLine(iconColor,
                Offset(iconCenterX + barGap, iconCenterY - barHeight / 2),
                Offset(iconCenterX + barGap, iconCenterY + barHeight / 2),
                strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
        }
    }
}
