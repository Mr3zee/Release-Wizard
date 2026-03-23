package com.github.mr3zee.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
    data class EdgeHit(val index: Int, val containerId: BlockId? = null) : HitTarget()
    data class ResizeHandleHit(val blockId: BlockId, val edge: ResizeEdge) : HitTarget()
}

private fun hitTest(
    logicalPos: Offset,
    graph: DagGraph,
    zoom: Float,
    containers: List<Block.ContainerBlock> = graph.blocks.filterIsInstance<Block.ContainerBlock>(),
    actionBlocks: List<Block> = graph.blocks.filter { it !is Block.ContainerBlock },
): HitTarget {
    val portRadius = (PORT_HIT_RADIUS / zoom).coerceIn(7f, 28f)
    val edgeThreshold = (8f / zoom).coerceIn(4f, 24f)
    val handleWidth = (RESIZE_HANDLE_WIDTH / zoom).coerceIn(3f, 12f)

    // Helper: compute absolute position for a child block inside a container
    fun childAbsPos(container: Block.ContainerBlock, childId: BlockId): BlockPosition? {
        val cPos = graph.positions[container.id] ?: return null
        val childPos = container.children.positions[childId] ?: return null
        return BlockPosition(
            cPos.x + childPos.x,
            cPos.y + BlockPosition.CONTAINER_HEADER_HEIGHT + childPos.y,
            childPos.width, childPos.height,
        )
    }

    // Check ports: top-level blocks first, then container children
    for (block in graph.blocks) {
        val pos = graph.positions[block.id] ?: continue
        val yOff = portYOffset(pos, block is Block.ContainerBlock)
        val outPort = Offset(pos.x + pos.width, pos.y + yOff)
        if ((logicalPos - outPort).getDistance() <= portRadius) return HitTarget.OutputPort(block.id)
        val inPort = Offset(pos.x, pos.y + yOff)
        if ((logicalPos - inPort).getDistance() <= portRadius) return HitTarget.InputPort(block.id)
    }
    for (container in containers) {
        for (child in container.children.blocks) {
            val absPos = childAbsPos(container, child.id) ?: continue
            val yOff = portYOffset(absPos, child is Block.ContainerBlock)
            val outPort = Offset(absPos.x + absPos.width, absPos.y + yOff)
            if ((logicalPos - outPort).getDistance() <= portRadius) return HitTarget.OutputPort(child.id)
            val inPort = Offset(absPos.x, absPos.y + yOff)
            if ((logicalPos - inPort).getDistance() <= portRadius) return HitTarget.InputPort(child.id)
        }
    }

    // Check resize handles: action blocks first (highest z), then children, then containers
    for (block in actionBlocks.asReversed()) {
        val pos = graph.positions[block.id] ?: continue
        val edge = detectResizeEdge(logicalPos, pos, handleWidth)
        if (edge != null) return HitTarget.ResizeHandleHit(block.id, edge)
    }
    for (container in containers) {
        for (child in container.children.blocks.asReversed()) {
            val absPos = childAbsPos(container, child.id) ?: continue
            val edge = detectResizeEdge(logicalPos, absPos, handleWidth)
            if (edge != null) return HitTarget.ResizeHandleHit(child.id, edge)
        }
    }
    for (container in containers.asReversed()) {
        val pos = graph.positions[container.id] ?: continue
        val edge = detectResizeEdge(logicalPos, pos, handleWidth)
        if (edge != null) return HitTarget.ResizeHandleHit(container.id, edge)
    }

    // Check block body: action blocks first, then children, then containers
    for (block in actionBlocks.asReversed()) {
        val pos = graph.positions[block.id] ?: continue
        if (logicalPos.x in pos.x..(pos.x + pos.width) && logicalPos.y in pos.y..(pos.y + pos.height)) {
            return HitTarget.BlockHit(block.id)
        }
    }
    for (container in containers) {
        for (child in container.children.blocks.asReversed()) {
            val absPos = childAbsPos(container, child.id) ?: continue
            if (logicalPos.x in absPos.x..(absPos.x + absPos.width) && logicalPos.y in absPos.y..(absPos.y + absPos.height)) {
                return HitTarget.BlockHit(child.id)
            }
        }
    }
    for (container in containers.asReversed()) {
        val pos = graph.positions[container.id] ?: continue
        if (logicalPos.x in pos.x..(pos.x + pos.width) && logicalPos.y in pos.y..(pos.y + pos.height)) {
            return HitTarget.BlockHit(container.id)
        }
    }

    // Check edges: top-level, then container children edges
    for ((index, edge) in graph.edges.withIndex()) {
        val fromPos = graph.positions[edge.fromBlockId] ?: continue
        val toPos = graph.positions[edge.toBlockId] ?: continue
        val fromBlock = graph.blocks.find { it.id == edge.fromBlockId }
        val toBlock = graph.blocks.find { it.id == edge.toBlockId }
        val start = Offset(fromPos.x + fromPos.width, fromPos.y + portYOffset(fromPos, fromBlock is Block.ContainerBlock))
        val end = Offset(toPos.x, toPos.y + portYOffset(toPos, toBlock is Block.ContainerBlock))
        if (isNearBezier(logicalPos, start, end, threshold = edgeThreshold)) {
            return HitTarget.EdgeHit(index)
        }
    }
    // Container children edges
    for (container in containers) {
        val cPos = graph.positions[container.id] ?: continue
        val contentOffsetX = cPos.x
        val contentOffsetY = cPos.y + BlockPosition.CONTAINER_HEADER_HEIGHT
        for ((index, edge) in container.children.edges.withIndex()) {
            val fromChild = container.children.positions[edge.fromBlockId] ?: continue
            val toChild = container.children.positions[edge.toBlockId] ?: continue
            val absFrom = BlockPosition(contentOffsetX + fromChild.x, contentOffsetY + fromChild.y, fromChild.width, fromChild.height)
            val absTo = BlockPosition(contentOffsetX + toChild.x, contentOffsetY + toChild.y, toChild.width, toChild.height)
            val start = Offset(absFrom.x + absFrom.width, absFrom.y + portYOffset(absFrom, false))
            val end = Offset(absTo.x, absTo.y + portYOffset(absTo, false))
            if (isNearBezier(logicalPos, start, end, threshold = edgeThreshold)) {
                return HitTarget.EdgeHit(index, containerId = container.id)
            }
        }
    }

    return HitTarget.None
}

/** Detect which resize edge (if any) the pointer is near. Corners take priority. */
private fun detectResizeEdge(pos: Offset, blockPos: BlockPosition, handleWidth: Float): ResizeEdge? {
    val left = pos.x in (blockPos.x - handleWidth)..(blockPos.x + handleWidth)
    val right = pos.x in (blockPos.x + blockPos.width - handleWidth)..(blockPos.x + blockPos.width + handleWidth)
    val top = pos.y in (blockPos.y - handleWidth)..(blockPos.y + handleWidth)
    val bottom = pos.y in (blockPos.y + blockPos.height - handleWidth)..(blockPos.y + blockPos.height + handleWidth)

    // Must be within the extended block bounds
    val inExtendedX = pos.x in (blockPos.x - handleWidth)..(blockPos.x + blockPos.width + handleWidth)
    val inExtendedY = pos.y in (blockPos.y - handleWidth)..(blockPos.y + blockPos.height + handleWidth)
    if (!inExtendedX || !inExtendedY) return null

    // Corners first
    if (top && left) return ResizeEdge.TopLeft
    if (top && right) return ResizeEdge.TopRight
    if (bottom && left) return ResizeEdge.BottomLeft
    if (bottom && right) return ResizeEdge.BottomRight

    // Edges
    if (top) return ResizeEdge.Top
    if (bottom) return ResizeEdge.Bottom
    if (left) return ResizeEdge.Left
    if (right) return ResizeEdge.Right

    return null
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
    selectedEdgeContainerId: BlockId? = null,
    onSelectBlock: (BlockId?) -> Unit,
    onToggleBlockSelection: (BlockId) -> Unit,
    onSelectEdge: (index: Int?, containerId: BlockId?) -> Unit,
    onMoveBlock: (BlockId, Float, Float) -> Unit,
    onCommitMove: () -> Unit,
    onAddEdge: (BlockId, BlockId) -> Unit,
    onEdgeRejected: (String) -> Unit = {},
    onResizeBlock: (BlockId, ResizeEdge, Float, Float) -> Unit = { _, _, _, _ -> },
    onCommitResize: () -> Unit = {},
    hoveredContainerId: BlockId? = null,
    detachingFromContainerId: BlockId? = null,
    parentLookup: Map<BlockId, BlockId> = emptyMap(),
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
    var cursorIcon by remember { mutableStateOf(PointerIcon.Default) }

    // Always-current graph reference for pointerInput lambdas that don't
    // restart on graph changes (to avoid cancelling active drag gestures).
    val currentGraph by rememberUpdatedState(graph)

    // Transform for drawing — recreated each recomposition
    val drawTransform = CanvasTransform(zoom, panOffset, density)

    // Pre-partition blocks by type (avoids allocations in draw scope and hit test)
    val containers = remember(graph) { graph.blocks.filterIsInstance<Block.ContainerBlock>() }
    val actionBlocks = remember(graph) { graph.blocks.filter { it !is Block.ContainerBlock } }
    val currentContainers by rememberUpdatedState(containers)
    val currentActionBlocks by rememberUpdatedState(actionBlocks)

    // Pre-resolve block type labels for canvas (stringResource requires composable context)
    val blockLabels: Map<BlockId, String> = buildMap {
        for (block in graph.blocks) {
            put(block.id, block.typeLabel())
            if (block is Block.ContainerBlock) {
                for (child in block.children.blocks) {
                    put(child.id, child.typeLabel())
                }
            }
        }
    }

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .alpha(if (isReadOnly) 0.6f else 1f)
            .background(appColors.canvasBackground)
            .pointerHoverIcon(cursorIcon)
            // Scroll for zoom + hover tracking
            // (macOS trackpad pinch generates scroll events, so this covers both
            // scroll-wheel zoom and pinch-to-zoom on Desktop)
            .pointerInput(Unit) {
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
                            val hit = hitTest(logical, currentGraph, zoom, currentContainers, currentActionBlocks)
                            hoveredPort = if (hit is HitTarget.InputPort || hit is HitTarget.OutputPort) hit else null
                            cursorIcon = if (!isReadOnly && hit is HitTarget.ResizeHandleHit) {
                                resizeEdgeCursor(hit.edge)
                            } else {
                                PointerIcon.Default
                            }
                        }
                    }
                }
            }
            // Click and drag
            // Key does NOT include `graph` — graph changes on every block move,
            // which would restart this handler and cancel the active drag gesture.
            // Graph is read inline via Compose snapshot state.
            .pointerInput(isReadOnly, selectedBlockIds) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) continue
                        val downChange = down.changes.firstOrNull() ?: continue
                        val downPos = downChange.position
                        // Compute transform inline from current state
                        val downTransform = CanvasTransform(zoom, panOffset, density)
                        val logicalDown = downTransform.toLogical(downPos)
                        val hit = hitTest(logicalDown, currentGraph, zoom, currentContainers, currentActionBlocks)

                        var prevPos = downPos
                        var wasDragged = false

                        while (true) {
                            val moveEvent = awaitPointerEvent()
                            val moveChange = moveEvent.changes.firstOrNull() ?: break
                            // Detect release: check pressed state, event type, and exit.
                            // macOS trackpad may use different release signaling than mouse.
                            val isReleased = !moveChange.pressed ||
                                moveEvent.type == PointerEventType.Release ||
                                moveEvent.type == PointerEventType.Exit ||
                                moveChange.changedToUpIgnoreConsumed()
                            if (isReleased) {
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
                                        is HitTarget.ResizeHandleHit -> {
                                            if (isModifierHeld) onToggleBlockSelection(hit.blockId)
                                            else onSelectBlock(hit.blockId)
                                        }
                                        is HitTarget.EdgeHit -> onSelectEdge(hit.index, hit.containerId)
                                        is HitTarget.None -> {
                                            onSelectBlock(null)
                                            onSelectEdge(null, null)
                                        }
                                    }
                                } else {
                                    when (hit) {
                                        is HitTarget.BlockHit -> onCommitMove()
                                        is HitTarget.ResizeHandleHit -> onCommitResize()
                                        is HitTarget.OutputPort -> {
                                            val releaseTransform = CanvasTransform(zoom, panOffset, density)
                                            val releaseLogical = releaseTransform.toLogical(moveChange.position)
                                            val releaseHit = hitTest(releaseLogical, currentGraph, zoom, currentContainers, currentActionBlocks)
                                            if (releaseHit is HitTarget.InputPort) {
                                                val from = hit.blockId
                                                val to = releaseHit.blockId
                                                if (from == to) {
                                                    onEdgeRejected("Cannot connect a block to itself")
                                                } else if (wouldCreateCycle(currentGraph, from, to)) {
                                                    onEdgeRejected("Cannot create a cycle in the pipeline")
                                                } else {
                                                    onAddEdge(from, to)
                                                }
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
                                    is HitTarget.ResizeHandleHit -> {
                                        if (!isReadOnly) {
                                            val logicalDelta = moveTransform.toLogicalDelta(delta)
                                            onResizeBlock(hit.blockId, hit.edge, logicalDelta.x, logicalDelta.y)
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

        // Layer 1: Container blocks (deepest layer)
        for (container in containers) {
            val pos = graph.positions[container.id] ?: continue
            val isSelected = container.id in selectedBlockIds
            drawContainerBlock(
                drawTransform, container, pos, isSelected,
                isDropTarget = container.id == hoveredContainerId,
                isDetaching = container.id == detachingFromContainerId,
                textMeasurer, zoom, appColors,
            )

            // Draw children's edges within the container
            val contentOffsetX = pos.x
            val contentOffsetY = pos.y + BlockPosition.CONTAINER_HEADER_HEIGHT
            for ((childEdgeIndex, childEdge) in container.children.edges.withIndex()) {
                val fromChild = container.children.positions[childEdge.fromBlockId] ?: continue
                val toChild = container.children.positions[childEdge.toBlockId] ?: continue
                val absFrom = BlockPosition(contentOffsetX + fromChild.x, contentOffsetY + fromChild.y, fromChild.width, fromChild.height)
                val absTo = BlockPosition(contentOffsetX + toChild.x, contentOffsetY + toChild.y, toChild.width, toChild.height)
                val isChildEdgeSelected = selectedEdgeContainerId == container.id && childEdgeIndex == selectedEdgeIndex
                drawEdge(drawTransform, absFrom, absTo, isSelected = isChildEdgeSelected, appColors)
            }

            // Draw children blocks
            for (childBlock in container.children.blocks) {
                val childPos = container.children.positions[childBlock.id] ?: continue
                val absPos = BlockPosition(contentOffsetX + childPos.x, contentOffsetY + childPos.y, childPos.width, childPos.height)
                val isChildSelected = childBlock.id in selectedBlockIds
                drawBlock(drawTransform, childBlock, absPos, isChildSelected, textMeasurer, zoom, appColors, blockLabels[childBlock.id] ?: "")
            }

            // Draw children ports
            for (childBlock in container.children.blocks) {
                val childPos = container.children.positions[childBlock.id] ?: continue
                val absPos = BlockPosition(contentOffsetX + childPos.x, contentOffsetY + childPos.y, childPos.width, childPos.height)
                val currentHoveredPort = hoveredPort
                val isInputHovered = currentHoveredPort is HitTarget.InputPort && currentHoveredPort.blockId == childBlock.id
                val isOutputHovered = currentHoveredPort is HitTarget.OutputPort && currentHoveredPort.blockId == childBlock.id
                drawPorts(drawTransform, absPos, isInputHovered, isOutputHovered, appColors)
            }
        }

        // Layer 2: Top-level edges
        for ((index, edge) in graph.edges.withIndex()) {
            val fromPos = graph.positions[edge.fromBlockId] ?: continue
            val toPos = graph.positions[edge.toBlockId] ?: continue
            val isSelected = index == selectedEdgeIndex
            val fromIsContainer = graph.blocks.find { it.id == edge.fromBlockId } is Block.ContainerBlock
            val toIsContainer = graph.blocks.find { it.id == edge.toBlockId } is Block.ContainerBlock
            drawEdge(drawTransform, fromPos, toPos, isSelected, appColors, fromIsContainer, toIsContainer)
        }

        // Draft edge (red when hovering an incompatible port)
        connectionDraft?.let { draft ->
            val fromPos = graph.positions[draft.fromBlockId] ?: return@let
            val fromIsContainer = graph.blocks.find { it.id == draft.fromBlockId } is Block.ContainerBlock
            val startScreenX = drawTransform.toScreenX(fromPos.x + fromPos.width)
            val startScreenY = drawTransform.toScreenY(fromPos.y + portYOffset(fromPos, fromIsContainer))
            val currentHover = hoveredPort
            val isInvalidTarget = currentHover is HitTarget.InputPort && (
                parentLookup[draft.fromBlockId] != parentLookup[currentHover.blockId] ||
                draft.fromBlockId == currentHover.blockId ||
                wouldCreateCycle(currentGraph, draft.fromBlockId, currentHover.blockId)
            )
            val draftColor = if (isInvalidTarget) appColors.blockStatusFailed else null
            drawDraftEdge(Offset(startScreenX, startScreenY), draft.currentScreenPos, appColors, drawTransform, colorOverride = draftColor)
        }

        // Layer 3: Top-level action blocks (on top of containers)
        for (block in actionBlocks) {
            val pos = graph.positions[block.id] ?: continue
            val isSelected = block.id in selectedBlockIds
            drawBlock(drawTransform, block, pos, isSelected, textMeasurer, zoom, appColors, blockLabels[block.id] ?: "")
        }

        // Layer 4: Ports for top-level blocks (containers + action blocks)
        for (block in graph.blocks) {
            val pos = graph.positions[block.id] ?: continue
            val currentHoveredPort = hoveredPort
            val isInputHovered = currentHoveredPort is HitTarget.InputPort && currentHoveredPort.blockId == block.id
            val isOutputHovered = currentHoveredPort is HitTarget.OutputPort && currentHoveredPort.blockId == block.id
            drawPorts(drawTransform, pos, isInputHovered, isOutputHovered, appColors, isContainer = block is Block.ContainerBlock)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.testTag("canvas_hint_add_blocks"),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.testTag("canvas_hint_connect_blocks"),
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

/** Map resize edge to the appropriate cursor icon. */
internal expect fun resizeEdgeCursor(edge: ResizeEdge): PointerIcon

/** Check if adding an edge from→to would create a cycle in the DAG. */
private fun wouldCreateCycle(graph: com.github.mr3zee.model.DagGraph, from: com.github.mr3zee.model.BlockId, to: com.github.mr3zee.model.BlockId): Boolean {
    // BFS from `to` following existing edges. If we can reach `from`, adding from→to creates a cycle.
    // Collect all edges: top-level + all container children edges
    val allEdges = buildList {
        addAll(graph.edges)
        for (block in graph.blocks) {
            if (block is Block.ContainerBlock) addAll(block.children.edges)
        }
    }
    val adjacency = allEdges.groupBy({ it.fromBlockId }, { it.toBlockId })
    val visited = mutableSetOf(to)
    val queue = ArrayDeque<com.github.mr3zee.model.BlockId>()
    queue.add(to)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == from) return true
        adjacency[current]?.forEach { next ->
            if (visited.add(next)) queue.add(next)
        }
    }
    return false
}
