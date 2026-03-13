package com.github.mr3zee.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.mr3zee.model.*

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
