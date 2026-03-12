package com.github.mr3zee.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.github.mr3zee.model.*
import kotlin.math.abs

// Block dimensions in dp (logical coordinates)
private const val BLOCK_WIDTH = 180f
private const val BLOCK_HEIGHT = 70f
private const val PORT_RADIUS = 5f
private const val PORT_HIT_RADIUS = 14f
private const val GRID_SIZE = 20f
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 4f

private val CANVAS_BG = Color(0xFFF8F9FA)
private val GRID_DOT_COLOR = Color(0xFFD1D5DB)
private val EDGE_COLOR = Color(0xFF6B7280)
private val EDGE_SELECTED_COLOR = Color(0xFF3B82F6)
private val PORT_COLOR = Color(0xFF9CA3AF)
private val PORT_HOVER_COLOR = Color(0xFF3B82F6)
private val SELECTION_BORDER = Color(0xFF3B82F6)
private val DRAFT_EDGE_COLOR = Color(0xFF3B82F6)

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

private fun blockTypeLabel(block: Block): String = when (block) {
    is Block.ActionBlock -> block.type.name.lowercase().replace("_", " ")
    is Block.ContainerBlock -> "container"
}

// Coordinate transform — created inline where needed to avoid stale captures
private class CanvasTransform(
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

// Hit testing
private sealed class HitTarget {
    data object None : HitTarget()
    data class BlockHit(val blockId: BlockId) : HitTarget()
    data class OutputPort(val blockId: BlockId) : HitTarget()
    data class InputPort(val blockId: BlockId) : HitTarget()
    data class EdgeHit(val index: Int) : HitTarget()
}

private fun hitTest(logicalPos: Offset, graph: DagGraph): HitTarget {
    // Check ports first (higher priority than blocks)
    for (block in graph.blocks) {
        val pos = graph.positions[block.id] ?: continue

        val outPort = Offset(pos.x + BLOCK_WIDTH, pos.y + BLOCK_HEIGHT / 2)
        if ((logicalPos - outPort).getDistance() <= PORT_HIT_RADIUS) {
            return HitTarget.OutputPort(block.id)
        }

        val inPort = Offset(pos.x, pos.y + BLOCK_HEIGHT / 2)
        if ((logicalPos - inPort).getDistance() <= PORT_HIT_RADIUS) {
            return HitTarget.InputPort(block.id)
        }
    }

    // Check blocks (reverse order so topmost block is hit first)
    for (block in graph.blocks.asReversed()) {
        val pos = graph.positions[block.id] ?: continue
        if (logicalPos.x in pos.x..(pos.x + BLOCK_WIDTH) &&
            logicalPos.y in pos.y..(pos.y + BLOCK_HEIGHT)
        ) {
            return HitTarget.BlockHit(block.id)
        }
    }

    // Check edges (proximity test)
    for ((index, edge) in graph.edges.withIndex()) {
        val fromPos = graph.positions[edge.fromBlockId] ?: continue
        val toPos = graph.positions[edge.toBlockId] ?: continue
        val start = Offset(fromPos.x + BLOCK_WIDTH, fromPos.y + BLOCK_HEIGHT / 2)
        val end = Offset(toPos.x, toPos.y + BLOCK_HEIGHT / 2)
        if (isNearBezier(logicalPos, start, end, threshold = 8f)) {
            return HitTarget.EdgeHit(index)
        }
    }

    return HitTarget.None
}

private fun isNearBezier(point: Offset, start: Offset, end: Offset, threshold: Float): Boolean {
    val dx = end.x - start.x
    val cp1 = Offset(start.x + dx * 0.4f, start.y)
    val cp2 = Offset(end.x - dx * 0.4f, end.y)

    for (i in 0..20) {
        val t = i / 20f
        val invT = 1f - t
        val bx = invT * invT * invT * start.x + 3 * invT * invT * t * cp1.x +
                3 * invT * t * t * cp2.x + t * t * t * end.x
        val by = invT * invT * invT * start.y + 3 * invT * invT * t * cp1.y +
                3 * invT * t * t * cp2.y + t * t * t * end.y
        val dist = Offset(point.x - bx, point.y - by).getDistance()
        if (dist < threshold) return true
    }
    return false
}

private data class ConnectionDraft(
    val fromBlockId: BlockId,
    val currentScreenPos: Offset,
)

@Composable
fun DagCanvas(
    graph: DagGraph,
    selectedBlockId: BlockId?,
    selectedEdgeIndex: Int?,
    onSelectBlock: (BlockId?) -> Unit,
    onSelectEdge: (Int?) -> Unit,
    onMoveBlock: (BlockId, Float, Float) -> Unit,
    onCommitMove: () -> Unit,
    onAddEdge: (BlockId, BlockId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var connectionDraft by remember { mutableStateOf<ConnectionDraft?>(null) }
    var hoveredPort by remember { mutableStateOf<HitTarget?>(null) }

    // Transform for drawing — recreated each recomposition
    val drawTransform = CanvasTransform(zoom, panOffset, density)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(CANVAS_BG)
            // Scroll for zoom + hover tracking
            .pointerInput(graph) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            val scrollY = change.scrollDelta.y
                            if (scrollY != 0f) {
                                val factor = if (scrollY < 0) 1.08f else 1f / 1.08f
                                val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                val pointerPos = change.position
                                // Zoom toward pointer position
                                val logicalBefore = Offset(
                                    (pointerPos.x - panOffset.x) / (density * zoom),
                                    (pointerPos.y - panOffset.y) / (density * zoom),
                                )
                                panOffset = Offset(
                                    pointerPos.x - logicalBefore.x * density * newZoom,
                                    pointerPos.y - logicalBefore.y * density * newZoom,
                                )
                                zoom = newZoom
                                change.consume()
                            }
                        }
                        if (event.type == PointerEventType.Move) {
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            // Compute transform inline to avoid stale captures
                            val t = CanvasTransform(zoom, panOffset, density)
                            val logical = t.toLogical(pos)
                            val hit = hitTest(logical, graph)
                            hoveredPort = if (hit is HitTarget.InputPort || hit is HitTarget.OutputPort) hit else null
                        }
                    }
                }
            }
            // Click and drag
            .pointerInput(graph, selectedBlockId) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) continue
                        val downChange = down.changes.firstOrNull() ?: continue
                        val downPos = downChange.position
                        // Compute transform inline from current state
                        val downTransform = CanvasTransform(zoom, panOffset, density)
                        val logicalDown = downTransform.toLogical(downPos)
                        val hit = hitTest(logicalDown, graph)

                        var prevPos = downPos
                        var wasDragged = false

                        while (true) {
                            val moveEvent = awaitPointerEvent()
                            val moveChange = moveEvent.changes.firstOrNull() ?: break
                            if (!moveChange.pressed) {
                                // Released
                                if (!wasDragged) {
                                    when (hit) {
                                        is HitTarget.BlockHit -> onSelectBlock(hit.blockId)
                                        is HitTarget.InputPort -> onSelectBlock(hit.blockId)
                                        is HitTarget.OutputPort -> onSelectBlock(hit.blockId)
                                        is HitTarget.EdgeHit -> onSelectEdge(hit.index)
                                        is HitTarget.None -> {
                                            onSelectBlock(null)
                                            onSelectEdge(null)
                                        }
                                    }
                                } else {
                                    when (hit) {
                                        is HitTarget.BlockHit -> onCommitMove()
                                        is HitTarget.OutputPort -> {
                                            val releaseTransform = CanvasTransform(zoom, panOffset, density)
                                            val releaseLogical = releaseTransform.toLogical(moveChange.position)
                                            val releaseHit = hitTest(releaseLogical, graph)
                                            if (releaseHit is HitTarget.InputPort) {
                                                onAddEdge(hit.blockId, releaseHit.blockId)
                                            }
                                            connectionDraft = null
                                        }
                                        else -> {}
                                    }
                                }
                                break
                            }

                            val delta = moveChange.position - prevPos
                            if (delta.getDistance() > 2f) wasDragged = true

                            if (wasDragged) {
                                // Compute transform inline for each move
                                val moveTransform = CanvasTransform(zoom, panOffset, density)
                                when (hit) {
                                    is HitTarget.BlockHit -> {
                                        val logicalDelta = moveTransform.toLogicalDelta(delta)
                                        onMoveBlock(hit.blockId, logicalDelta.x, logicalDelta.y)
                                    }
                                    is HitTarget.OutputPort -> {
                                        connectionDraft = ConnectionDraft(
                                            fromBlockId = hit.blockId,
                                            currentScreenPos = moveChange.position,
                                        )
                                    }
                                    is HitTarget.None, is HitTarget.EdgeHit,
                                    is HitTarget.InputPort -> {
                                        panOffset += delta
                                    }
                                }
                            }

                            prevPos = moveChange.position
                            moveChange.consume()
                        }
                    }
                }
            }
    ) {
        drawGrid(drawTransform)

        for ((index, edge) in graph.edges.withIndex()) {
            val fromPos = graph.positions[edge.fromBlockId] ?: continue
            val toPos = graph.positions[edge.toBlockId] ?: continue
            val isSelected = index == selectedEdgeIndex
            drawEdge(drawTransform, fromPos, toPos, isSelected)
        }

        connectionDraft?.let { draft ->
            val fromPos = graph.positions[draft.fromBlockId] ?: return@let
            val startScreenX = drawTransform.toScreenX(fromPos.x + BLOCK_WIDTH)
            val startScreenY = drawTransform.toScreenY(fromPos.y + BLOCK_HEIGHT / 2)
            drawDraftEdge(Offset(startScreenX, startScreenY), draft.currentScreenPos)
        }

        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val isSelected = block.id == selectedBlockId
            drawBlock(drawTransform, block, pos, isSelected, textMeasurer, zoom)
        }

        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val isInputHovered = hoveredPort is HitTarget.InputPort &&
                    (hoveredPort as HitTarget.InputPort).blockId == block.id
            val isOutputHovered = hoveredPort is HitTarget.OutputPort &&
                    (hoveredPort as HitTarget.OutputPort).blockId == block.id
            drawPorts(drawTransform, pos, isInputHovered, isOutputHovered)
        }
    }
}

private fun DrawScope.drawGrid(transform: CanvasTransform) {
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

private fun DrawScope.drawBlock(
    transform: CanvasTransform,
    block: Block,
    position: BlockPosition,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
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
        color = blockColor(block),
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

private fun DrawScope.drawPorts(
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

private fun DrawScope.drawEdge(
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

private fun DrawScope.drawDraftEdge(start: Offset, end: Offset) {
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
