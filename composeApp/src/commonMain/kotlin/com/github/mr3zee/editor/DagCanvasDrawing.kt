package com.github.mr3zee.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.github.mr3zee.model.*

// Block dimensions in dp (logical coordinates)
internal const val BLOCK_WIDTH = 180f
internal const val BLOCK_HEIGHT = 70f
internal const val PORT_RADIUS = 5f
internal const val PORT_HIT_RADIUS = 14f
internal const val GRID_SIZE = 20f
internal const val MIN_ZOOM = 0.25f
internal const val MAX_ZOOM = 4f

internal val CANVAS_BG = Color(0xFFF8F9FA)
internal val GRID_DOT_COLOR = Color(0xFFD1D5DB)
internal val EDGE_COLOR = Color(0xFF6B7280)
internal val EDGE_SELECTED_COLOR = Color(0xFF3B82F6)
internal val PORT_COLOR = Color(0xFF9CA3AF)
internal val PORT_HOVER_COLOR = Color(0xFF3B82F6)
internal val SELECTION_BORDER = Color(0xFF3B82F6)
internal val DRAFT_EDGE_COLOR = Color(0xFF3B82F6)

internal fun blockColor(block: Block): Color = when (block) {
    is Block.ActionBlock -> blockTypeColor(block.type)
    is Block.ContainerBlock -> Color(0xFF6B7280)
}

internal fun blockTypeColor(type: BlockType): Color = when (type) {
    BlockType.TEAMCITY_BUILD -> Color(0xFF4A90D9)
    BlockType.GITHUB_ACTION -> Color(0xFF8B5CF6)
    BlockType.GITHUB_PUBLICATION -> Color(0xFF059669)
    BlockType.MAVEN_CENTRAL_PUBLICATION -> Color(0xFFF59E0B)
    BlockType.SLACK_MESSAGE -> Color(0xFFE11D48)
    BlockType.USER_ACTION -> Color(0xFF0D9488)
}

internal fun blockTypeLabel(block: Block): String = when (block) {
    is Block.ActionBlock -> block.type.name.lowercase().replace("_", " ")
    is Block.ContainerBlock -> "container"
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

internal fun DrawScope.drawGrid(transform: CanvasTransform) {
    val gridScreenSize = transform.toScreen(GRID_SIZE)
    if (gridScreenSize < 4f) return

    val startX = transform.panOffset.x % gridScreenSize
    val startY = transform.panOffset.y % gridScreenSize

    var x = startX
    while (x < size.width) {
        var y = startY
        while (y < size.height) {
            drawCircle(GRID_DOT_COLOR, radius = 1.5f, center = Offset(x, y))
            y += gridScreenSize
        }
        x += gridScreenSize
    }
}

internal fun DrawScope.drawBlock(
    transform: CanvasTransform,
    block: Block,
    position: BlockPosition,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
    fillColor: Color = blockColor(block),
) {
    val screenX = transform.toScreenX(position.x)
    val screenY = transform.toScreenY(position.y)
    val screenW = transform.toScreen(BLOCK_WIDTH)
    val screenH = transform.toScreen(BLOCK_HEIGHT)
    val cornerRadius = transform.toScreen(8f)

    // Shadow
    drawRoundRect(
        color = Color(0x20000000),
        topLeft = Offset(screenX + 2f, screenY + 2f),
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

    // Selection border
    if (isSelected) {
        drawRoundRect(
            color = SELECTION_BORDER,
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
        style = TextStyle(fontSize = nameSize.sp, color = Color.White),
    )
    drawText(
        nameLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + transform.toScreen(12f)),
    )

    // Type label
    val typeSize = (10f * zoom).coerceIn(5f, 30f)
    val typeLayout = textMeasurer.measure(
        blockTypeLabel(block),
        style = TextStyle(fontSize = typeSize.sp, color = Color(0xCCFFFFFF)),
    )
    drawText(
        typeLayout,
        topLeft = Offset(screenX + transform.toScreen(10f), screenY + transform.toScreen(36f)),
    )
}

internal fun DrawScope.drawPorts(
    transform: CanvasTransform,
    position: BlockPosition,
    isInputHovered: Boolean,
    isOutputHovered: Boolean,
) {
    val portScreenRadius = transform.toScreen(PORT_RADIUS)

    val inX = transform.toScreenX(position.x)
    val inY = transform.toScreenY(position.y + BLOCK_HEIGHT / 2)
    drawCircle(
        color = if (isInputHovered) PORT_HOVER_COLOR else PORT_COLOR,
        radius = portScreenRadius,
        center = Offset(inX, inY),
    )

    val outX = transform.toScreenX(position.x + BLOCK_WIDTH)
    val outY = transform.toScreenY(position.y + BLOCK_HEIGHT / 2)
    drawCircle(
        color = if (isOutputHovered) PORT_HOVER_COLOR else PORT_COLOR,
        radius = portScreenRadius,
        center = Offset(outX, outY),
    )
}

internal fun DrawScope.drawEdge(
    transform: CanvasTransform,
    fromPos: BlockPosition,
    toPos: BlockPosition,
    isSelected: Boolean,
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

    drawPath(
        path,
        color = if (isSelected) EDGE_SELECTED_COLOR else EDGE_COLOR,
        style = Stroke(width = if (isSelected) 3f else 2f),
    )

    // Arrow dot at end
    val arrowSize = transform.toScreen(6f)
    drawCircle(
        color = if (isSelected) EDGE_SELECTED_COLOR else EDGE_COLOR,
        radius = arrowSize,
        center = Offset(endX, endY),
    )
}

internal fun DrawScope.drawDraftEdge(start: Offset, end: Offset) {
    val dx = end.x - start.x
    val cp1x = start.x + dx * 0.4f
    val cp2x = end.x - dx * 0.4f

    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(cp1x, start.y, cp2x, end.y, end.x, end.y)
    }

    drawPath(
        path,
        color = DRAFT_EDGE_COLOR,
        style = Stroke(
            width = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
        ),
    )
}
