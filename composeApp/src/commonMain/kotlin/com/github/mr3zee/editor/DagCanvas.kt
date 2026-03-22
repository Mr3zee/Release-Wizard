package com.github.mr3zee.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.*
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.typeLabel
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.editor_empty_canvas_hint
import releasewizard.composeapp.generated.resources.editor_empty_canvas_no_blocks
import releasewizard.composeapp.generated.resources.editor_zoom_fit
import releasewizard.composeapp.generated.resources.editor_zoom_in
import releasewizard.composeapp.generated.resources.editor_zoom_out

// Hit testing
private sealed class HitTarget {
    data object None : HitTarget()
    data class BlockHit(val blockId: BlockId) : HitTarget()
    data class OutputPort(val blockId: BlockId) : HitTarget()
    data class InputPort(val blockId: BlockId) : HitTarget()
    data class EdgeHit(val index: Int) : HitTarget()
}

private fun hitTest(logicalPos: Offset, graph: DagGraph, zoom: Float): HitTarget {
    val portRadius = (PORT_HIT_RADIUS / zoom).coerceIn(7f, 28f)
    val edgeThreshold = (8f / zoom).coerceIn(4f, 24f)

    // Check ports first (higher priority than blocks)
    for (block in graph.blocks) {
        val pos = graph.positions[block.id] ?: continue

        val outPort = Offset(pos.x + BLOCK_WIDTH, pos.y + BLOCK_HEIGHT / 2)
        if ((logicalPos - outPort).getDistance() <= portRadius) {
            return HitTarget.OutputPort(block.id)
        }

        val inPort = Offset(pos.x, pos.y + BLOCK_HEIGHT / 2)
        if ((logicalPos - inPort).getDistance() <= portRadius) {
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
        if (isNearBezier(logicalPos, start, end, threshold = edgeThreshold)) {
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
    selectedBlockIds: Set<BlockId>,
    selectedEdgeIndex: Int?,
    onSelectBlock: (BlockId?) -> Unit,
    onToggleBlockSelection: (BlockId) -> Unit,
    onSelectEdge: (Int?) -> Unit,
    onMoveBlock: (BlockId, Float, Float) -> Unit,
    onCommitMove: () -> Unit,
    onAddEdge: (BlockId, BlockId) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val appColors = LocalAppColors.current

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var connectionDraft by remember { mutableStateOf<ConnectionDraft?>(null) }
    var hoveredPort by remember { mutableStateOf<HitTarget?>(null) }

    // Transform for drawing — recreated each recomposition
    val drawTransform = CanvasTransform(zoom, panOffset, density)

    // Pre-resolve block type labels for canvas (stringResource requires composable context)
    val blockLabels: Map<BlockId, String> = graph.blocks.associate { block ->
        block.id to block.typeLabel()
    }

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .alpha(if (isReadOnly) 0.6f else 1f)
            .background(appColors.canvasBackground)
            // Scroll for zoom + hover tracking
            // (macOS trackpad pinch generates scroll events, so this covers both
            // scroll-wheel zoom and pinch-to-zoom on Desktop)
            .pointerInput(graph) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        applyScrollZoom(event, zoom, panOffset, density)?.let { (z, p) ->
                            zoom = z
                            panOffset = p
                        }
                        if (event.type == PointerEventType.Move) {
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            // Compute transform inline to avoid stale captures
                            val t = CanvasTransform(zoom, panOffset, density)
                            val logical = t.toLogical(pos)
                            val hit = hitTest(logical, graph, zoom)
                            hoveredPort = if (hit is HitTarget.InputPort || hit is HitTarget.OutputPort) hit else null
                        }
                    }
                }
            }
            // Click and drag
            .pointerInput(graph, isReadOnly, selectedBlockIds) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) continue
                        val downChange = down.changes.firstOrNull() ?: continue
                        val downPos = downChange.position
                        // Compute transform inline from current state
                        val downTransform = CanvasTransform(zoom, panOffset, density)
                        val logicalDown = downTransform.toLogical(downPos)
                        val hit = hitTest(logicalDown, graph, zoom)

                        var prevPos = downPos
                        var wasDragged = false

                        while (true) {
                            val moveEvent = awaitPointerEvent()
                            val moveChange = moveEvent.changes.firstOrNull() ?: break
                            if (!moveChange.pressed) {
                                // Released
                                if (!wasDragged) {
                                    val isModifierHeld = down.keyboardModifiers.isCtrlPressed ||
                                            down.keyboardModifiers.isMetaPressed
                                    when (hit) {
                                        is HitTarget.BlockHit -> {
                                            if (isModifierHeld) onToggleBlockSelection(hit.blockId)
                                            else onSelectBlock(hit.blockId)
                                        }
                                        is HitTarget.InputPort -> {
                                            if (isModifierHeld) onToggleBlockSelection(hit.blockId)
                                            else onSelectBlock(hit.blockId)
                                        }
                                        is HitTarget.OutputPort -> {
                                            if (isModifierHeld) onToggleBlockSelection(hit.blockId)
                                            else onSelectBlock(hit.blockId)
                                        }
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
                                            val releaseHit = hitTest(releaseLogical, graph, zoom)
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
                                        if (!isReadOnly) {
                                            val logicalDelta = moveTransform.toLogicalDelta(delta)
                                            // Move all selected blocks together if the dragged block is selected
                                            if (hit.blockId in selectedBlockIds && selectedBlockIds.size > 1) {
                                                for (id in selectedBlockIds) {
                                                    onMoveBlock(id, logicalDelta.x, logicalDelta.y)
                                                }
                                            } else {
                                                onMoveBlock(hit.blockId, logicalDelta.x, logicalDelta.y)
                                            }
                                        } else {
                                            panOffset += delta
                                        }
                                    }
                                    is HitTarget.OutputPort -> {
                                        if (!isReadOnly) {
                                            connectionDraft = ConnectionDraft(
                                                fromBlockId = hit.blockId,
                                                currentScreenPos = moveChange.position,
                                            )
                                        } else {
                                            panOffset += delta
                                        }
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
        drawGrid(drawTransform, appColors)

        for ((index, edge) in graph.edges.withIndex()) {
            val fromPos = graph.positions[edge.fromBlockId] ?: continue
            val toPos = graph.positions[edge.toBlockId] ?: continue
            val isSelected = index == selectedEdgeIndex
            drawEdge(drawTransform, fromPos, toPos, isSelected, appColors)
        }

        connectionDraft?.let { draft ->
            val fromPos = graph.positions[draft.fromBlockId] ?: return@let
            val startScreenX = drawTransform.toScreenX(fromPos.x + BLOCK_WIDTH)
            val startScreenY = drawTransform.toScreenY(fromPos.y + BLOCK_HEIGHT / 2)
            drawDraftEdge(Offset(startScreenX, startScreenY), draft.currentScreenPos, appColors, drawTransform)
        }

        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val isSelected = block.id in selectedBlockIds
            drawBlock(drawTransform, block, pos, isSelected, textMeasurer, zoom, appColors, blockLabels[block.id] ?: "")
        }

        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val currentHoveredPort = hoveredPort
            val isInputHovered = currentHoveredPort is HitTarget.InputPort && currentHoveredPort.blockId == block.id
            val isOutputHovered = currentHoveredPort is HitTarget.OutputPort && currentHoveredPort.blockId == block.id
            drawPorts(drawTransform, pos, isInputHovered, isOutputHovered, appColors)
        }
    }

    // Empty canvas hints
    if (graph.blocks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packStringResource(Res.string.editor_empty_canvas_no_blocks),
                style = AppTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.testTag("empty_canvas_hint"),
            )
        }
    } else if (graph.edges.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packStringResource(Res.string.editor_empty_canvas_hint),
                style = AppTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.testTag("empty_canvas_hint"),
            )
        }
    }

    // Zoom controls overlay (bottom-right)
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "${(zoom * 100).toInt()}%",
            style = AppTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("zoom_label"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            RwTooltip(tooltip = packStringResource(Res.string.editor_zoom_in)) {
                RwIconButton(
                    onClick = {
                        val newZoom = (zoom * 1.2f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        zoom = newZoom
                    },
                    modifier = Modifier.size(32.dp).testTag("zoom_in_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.editor_zoom_in), modifier = Modifier.size(16.dp))
                }
            }
            RwTooltip(tooltip = packStringResource(Res.string.editor_zoom_out)) {
                RwIconButton(
                    onClick = {
                        val newZoom = (zoom / 1.2f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        zoom = newZoom
                    },
                    modifier = Modifier.size(32.dp).testTag("zoom_out_button"),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = packStringResource(Res.string.editor_zoom_out), modifier = Modifier.size(16.dp))
                }
            }
        }
        RwButton(
            onClick = {
                zoom = 1f
                panOffset = Offset.Zero
            },
            variant = RwButtonVariant.Ghost,
            modifier = Modifier.testTag("zoom_fit_button"),
        ) {
            Text(packStringResource(Res.string.editor_zoom_fit), style = AppTypography.caption)
        }
    }
    } // Box
}
