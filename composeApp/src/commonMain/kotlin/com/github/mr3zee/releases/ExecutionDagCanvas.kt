package com.github.mr3zee.releases

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.mr3zee.editor.*
import com.github.mr3zee.model.*

internal fun blockStatusColor(status: BlockStatus): Color = when (status) {
    BlockStatus.SUCCEEDED -> Color(0xFF22C55E) // green
    BlockStatus.RUNNING -> Color(0xFF3B82F6)   // blue
    BlockStatus.FAILED -> Color(0xFFEF4444)    // red
    BlockStatus.WAITING -> Color(0xFF9CA3AF)   // gray
    BlockStatus.WAITING_FOR_INPUT -> Color(0xFFF59E0B) // amber
}

@Composable
fun ExecutionDagCanvas(
    graph: DagGraph,
    blockExecutions: List<BlockExecution>,
    onBlockClick: (BlockId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val executionMap = remember(blockExecutions) {
        blockExecutions.associateBy { it.blockId }
    }

    val drawTransform = CanvasTransform(zoom, panOffset, density)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(CANVAS_BG)
            .testTag("execution_dag_canvas")
            // Scroll for zoom
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
                    }
                }
            }
            // Pan and click
            .pointerInput(graph) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) continue
                        val downChange = down.changes.firstOrNull() ?: continue
                        val downPos = downChange.position

                        var prevPos = downPos
                        var wasDragged = false

                        while (true) {
                            val moveEvent = awaitPointerEvent()
                            val moveChange = moveEvent.changes.firstOrNull() ?: break
                            if (!moveChange.pressed) {
                                if (!wasDragged) {
                                    // Click — check if a block was hit
                                    val t = CanvasTransform(zoom, panOffset, density)
                                    val logical = t.toLogical(downPos)
                                    for (block in graph.blocks.asReversed()) {
                                        val pos = graph.positions[block.id] ?: continue
                                        if (logical.x in pos.x..(pos.x + BLOCK_WIDTH) &&
                                            logical.y in pos.y..(pos.y + BLOCK_HEIGHT)
                                        ) {
                                            onBlockClick(block.id)
                                            break
                                        }
                                    }
                                }
                                break
                            }

                            val delta = moveChange.position - prevPos
                            if (delta.getDistance() > 2f) wasDragged = true
                            if (wasDragged) {
                                panOffset += delta
                            }
                            prevPos = moveChange.position
                            moveChange.consume()
                        }
                    }
                }
            }
    ) {
        drawGrid(drawTransform)

        // Edges
        for (edge in graph.edges) {
            val fromPos = graph.positions[edge.fromBlockId] ?: continue
            val toPos = graph.positions[edge.toBlockId] ?: continue
            drawEdge(drawTransform, fromPos, toPos, isSelected = false)
        }

        // Blocks with status-based colors
        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val execution = executionMap[block.id]
            val fillColor = if (execution != null) {
                blockStatusColor(execution.status)
            } else {
                blockColor(block)
            }
            drawBlock(drawTransform, block, pos, isSelected = false, textMeasurer, zoom, fillColor)
        }
    }
}
