package com.github.mr3zee.releases

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.github.mr3zee.theme.AppColors
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.util.typeLabel
import kotlin.math.PI

internal fun blockStatusColor(status: BlockStatus, colors: AppColors): Color = when (status) {
    BlockStatus.SUCCEEDED -> colors.blockStatusSucceeded
    BlockStatus.RUNNING -> colors.blockStatusRunning
    BlockStatus.FAILED -> colors.blockStatusFailed
    BlockStatus.WAITING -> colors.blockStatusWaiting
    BlockStatus.WAITING_FOR_INPUT -> colors.blockStatusWaitingForInput
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
    val appColors = LocalAppColors.current

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val executionMap = remember(blockExecutions) {
        blockExecutions.associateBy { it.blockId }
    }

    val drawTransform = CanvasTransform(zoom, panOffset, density)

    val hasRunningBlocks = blockExecutions.any { it.status == BlockStatus.RUNNING }
    val infiniteTransition = rememberInfiniteTransition(label = "running_indicator")
    val runningPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "running_phase",
    )

    // Pre-resolve block type labels in composable context (typeLabel() uses string resources)
    val blockLabels = graph.blocks.associate { block -> block.id to block.typeLabel() }

    val effectiveRunningPhase = if (hasRunningBlocks) runningPhase else 0f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(appColors.canvasBackground)
            .testTag("execution_dag_canvas")
            // Scroll for zoom
            .pointerInput(graph) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        applyScrollZoom(event, zoom, panOffset, density)?.let { (z, p) ->
                            zoom = z
                            panOffset = p
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
        drawGrid(drawTransform, appColors)

        // Edges
        for (edge in graph.edges) {
            val fromPos = graph.positions[edge.fromBlockId] ?: continue
            val toPos = graph.positions[edge.toBlockId] ?: continue
            drawEdge(drawTransform, fromPos, toPos, isSelected = false, appColors)
        }

        // Blocks with status-based colors
        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val execution = executionMap[block.id]
            val fillColor = if (execution != null) {
                blockStatusColor(execution.status, appColors)
            } else {
                blockColor(block, appColors)
            }
            drawBlock(drawTransform, block, pos, isSelected = false, textMeasurer, zoom, appColors, blockLabels[block.id] ?: "", fillColor)
            if (execution != null) {
                drawBlockStatusIcon(drawTransform, pos, execution.status, appColors)
            }
            if (execution?.status == BlockStatus.RUNNING && hasRunningBlocks) {
                drawRunningIndicator(drawTransform, pos, effectiveRunningPhase, appColors)
            }
        }
    }
}
