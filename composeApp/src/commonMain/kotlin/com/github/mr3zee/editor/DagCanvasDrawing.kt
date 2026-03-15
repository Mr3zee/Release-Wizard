package com.github.mr3zee.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

// Block dimensions in dp (logical coordinates)
internal const val BLOCK_WIDTH = 180f
internal const val BLOCK_HEIGHT = 70f
internal const val PORT_RADIUS = 5f
internal const val PORT_HIT_RADIUS = 14f
internal const val GRID_SIZE = 20f
internal const val MIN_ZOOM = 0.25f
internal const val MAX_ZOOM = 4f

internal fun blockColor(block: Block, colors: AppColors): Color = when (block) {
    is Block.ActionBlock -> blockTypeColor(block.type, colors)
    is Block.ContainerBlock -> colors.containerBlock
}

internal fun blockTypeColor(type: BlockType, colors: AppColors): Color = when (type) {
    BlockType.TEAMCITY_BUILD -> colors.teamcityBuild
    BlockType.GITHUB_ACTION -> colors.githubAction
    BlockType.GITHUB_PUBLICATION -> colors.githubPublication
    BlockType.MAVEN_CENTRAL_PUBLICATION -> colors.mavenCentral
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
    val factor = if (scrollY < 0) 1.08f else 1f / 1.08f
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
            drawLine(colors.canvasGridMinor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.25f)
            x += minorScreenSize
        }
        var y = startY
        while (y < size.height) {
            drawLine(colors.canvasGridMinor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.25f)
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
            drawLine(colors.canvasGridMajor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += majorScreenSize
        }
        var y = startY
        while (y < size.height) {
            drawLine(colors.canvasGridMajor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
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
    val screenW = transform.toScreen(BLOCK_WIDTH)
    val screenH = transform.toScreen(BLOCK_HEIGHT)
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
    val nameSize = (13f * zoom).coerceIn(6f, 40f)
    val nameLayout = textMeasurer.measure(
        block.name,
        style = TextStyle(fontSize = nameSize.sp, color = colors.blockText),
    )
    drawText(
        nameLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + transform.toScreen(12f)),
    )

    // Type label
    val typeSize = (10f * zoom).coerceIn(5f, 30f)
    val typeLayout = textMeasurer.measure(
        typeLabel,
        style = TextStyle(fontSize = typeSize.sp, color = colors.blockTextSecondary),
    )
    drawText(
        typeLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + transform.toScreen(36f)),
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

internal fun DrawScope.drawPorts(
    transform: CanvasTransform,
    position: BlockPosition,
    isInputHovered: Boolean,
    isOutputHovered: Boolean,
    colors: AppColors,
) {
    val portScreenRadius = transform.toScreen(PORT_RADIUS)

    val inX = transform.toScreenX(position.x)
    val inY = transform.toScreenY(position.y + BLOCK_HEIGHT / 2)
    // Hover ring (outer glow) on input port
    if (isInputHovered) {
        drawCircle(
            color = colors.portHoverRing,
            radius = portScreenRadius * 2f,
            center = Offset(inX, inY),
        )
    }
    drawCircle(
        color = if (isInputHovered) colors.portHover else colors.portDefault,
        radius = portScreenRadius,
        center = Offset(inX, inY),
    )

    val outX = transform.toScreenX(position.x + BLOCK_WIDTH)
    val outY = transform.toScreenY(position.y + BLOCK_HEIGHT / 2)
    // Hover ring (outer glow) on output port
    if (isOutputHovered) {
        drawCircle(
            color = colors.portHoverRing,
            radius = portScreenRadius * 2f,
            center = Offset(outX, outY),
        )
    }
    drawCircle(
        color = if (isOutputHovered) colors.portHover else colors.portDefault,
        radius = portScreenRadius,
        center = Offset(outX, outY),
    )
}

internal fun DrawScope.drawEdge(
    transform: CanvasTransform,
    fromPos: BlockPosition,
    toPos: BlockPosition,
    isSelected: Boolean,
    colors: AppColors,
) {
    val startX = transform.toScreenX(fromPos.x + BLOCK_WIDTH)
    val startY = transform.toScreenY(fromPos.y + BLOCK_HEIGHT / 2)
    val endX = transform.toScreenX(toPos.x)
    val endY = transform.toScreenY(toPos.y + BLOCK_HEIGHT / 2)

    val dx = endX - startX
    val cp1x = startX + dx * 0.4f
    val cp2x = endX - dx * 0.4f

    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(cp1x, startY, cp2x, endY, endX, endY)
    }

    // Scale stroke widths with zoom for consistent visual weight
    val baseStroke = transform.toScreen(1.5f)
    val selectedStroke = transform.toScreen(2f)

    // Glow effect for selected edges (outer pass at 30% alpha, wider stroke)
    if (isSelected) {
        drawPath(
            path,
            color = colors.edgeGlow,
            style = Stroke(width = transform.toScreen(4f)),
        )
    }

    drawPath(
        path,
        color = if (isSelected) colors.edgeSelected else colors.edgeDefault,
        style = Stroke(width = if (isSelected) selectedStroke else baseStroke),
    )

    // Arrow dot at end (smaller than PORT_RADIUS to avoid overlap)
    val arrowSize = transform.toScreen(3.5f)
    drawCircle(
        color = if (isSelected) colors.edgeSelected else colors.edgeDefault,
        radius = arrowSize,
        center = Offset(endX, endY),
    )
}

internal fun DrawScope.drawDraftEdge(start: Offset, end: Offset, colors: AppColors, transform: CanvasTransform? = null) {
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
        color = colors.draftEdge,
        style = Stroke(
            width = strokeWidth,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength)),
        ),
    )
}
