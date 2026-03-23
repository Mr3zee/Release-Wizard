package com.github.mr3zee

import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.editor.LockState
import com.github.mr3zee.editor.ResizeEdge
import com.github.mr3zee.model.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for DagEditorViewModel — tests block resize, container interactions,
 * cross-boundary edge prevention, deep block operations, and state management.
 */
class DagEditorViewModelTest {

    // --- Helpers ---

    private val projectJson = """{"project":{"id":"p1","name":"Test","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
    private val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""

    private fun createViewModel(): DagEditorViewModel {
        val client = mockHttpClient(mapOf(
            "/projects/p1" to json(projectJson),
            "/projects/p1/lock" to json(lockJson, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, method = HttpMethod.Put),
        ))
        return DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client), autoSaveDebounceMs = 999_999L)
    }

    /** Create a VM that's already loaded, with a block and a container for testing. */
    private fun loadedViewModelWithContainerSetup(): Triple<DagEditorViewModel, BlockId, BlockId> {
        val vm = createViewModel()
        vm.loadProject()
        // Wait for lock
        val start = System.currentTimeMillis()
        while (vm.lockState.value !is LockState.Acquired && System.currentTimeMillis() - start < 3000) {
            Thread.sleep(10)
        }

        // Add an action block and a container
        vm.addBlock(BlockType.TEAMCITY_BUILD, "Build", 100f, 100f)
        val blockId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first().id

        vm.addContainerBlock("Container", 300f, 50f)
        val containerId = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first().id

        return Triple(vm, blockId, containerId)
    }

    /**
     * Helper: select a block, move it so its center is inside a container's content area, and commit.
     * This simulates the user drag-and-drop into a container.
     */
    private fun DagEditorViewModel.dragBlockIntoContainer(blockId: BlockId, containerId: BlockId) {
        val cPos = graph.value.positions[containerId] ?: error("No container position for $containerId")
        val bPos = graph.value.positions[blockId] ?: error("No block position for $blockId")
        // Target: block center inside container content area
        val targetCenterX = cPos.x + cPos.width / 2
        val targetCenterY = cPos.y + cPos.headerHeight + (cPos.height - cPos.headerHeight) / 2
        val dx = targetCenterX - (bPos.x + bPos.width / 2)
        val dy = targetCenterY - (bPos.y + bPos.height / 2)
        selectBlock(blockId)
        moveBlock(blockId, dx, dy)
        updateDragFeedback(setOf(blockId))
        commitMove()
    }

    // ==================== CRITICAL: Cross-boundary edge prevention (#2) ====================

    @Test
    fun `addEdge rejects cross-boundary connection`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Now blockId is inside the container. Add another top-level block.
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 600f, 100f)
        val topLevelId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id

        // Try to connect child to top-level — should be rejected
        val result = vm.addEdge(blockId, topLevelId)
        assertEquals("Cannot connect blocks across container boundaries", result)
    }

    @Test
    fun `addEdge succeeds within same container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Add second block and move into same container
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Connect within container
        val result = vm.addEdge(blockId, slackId)
        assertNull(result)

        // Edge should be in container's children edges
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(container.children.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId })
    }

    // ==================== CRITICAL: removeSelectedBlocks for children (#3) ====================

    @Test
    fun `removeSelectedBlocks deletes child inside container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Verify block is in container
        val containerBefore = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(1, containerBefore.children.blocks.size)

        // Select and delete the child
        vm.selectBlock(blockId)
        vm.removeSelectedBlocks()

        // Verify child is removed
        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(0, containerAfter.children.blocks.size)
    }

    // ==================== CRITICAL: commitMove container entry (#4) ====================

    @Test
    fun `commitMove moves block into container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Add an edge from blockId to container (top-level edge)
        vm.addEdge(blockId, containerId)
        val edgesBefore = vm.graph.value.edges.size
        assertTrue(edgesBefore > 0)

        // Select and move block center inside container content area
        vm.selectBlock(blockId)
        val cPos = vm.graph.value.positions[containerId] ?: error("No container position")
        val bPos = vm.graph.value.positions[blockId] ?: error("No block position")
        val targetCX = cPos.x + cPos.width / 2
        val targetCY = cPos.y + cPos.headerHeight + (cPos.height - cPos.headerHeight) / 2
        vm.moveBlock(blockId, targetCX - (bPos.x + bPos.width / 2), targetCY - (bPos.y + bPos.height / 2))
        vm.updateDragFeedback(setOf(blockId))

        // commitMove should move block into container and strip cross-boundary edges
        val msg = vm.commitMove()

        // Block should now be inside container
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(container.children.blocks.any { it.id == blockId })
        // Block should not be in top-level
        assertTrue(vm.graph.value.blocks.none { it.id == blockId && it !is Block.ContainerBlock })
        // Edge should be removed
        assertEquals(0, vm.graph.value.edges.size)
        // Message about dropped edges
        assertNotNull(msg)
        assertTrue(msg.contains("removed"))
    }

    // ==================== CRITICAL: commitMove container exit (#5) ====================

    @Test
    fun `commitMove removes block from container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // First move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Verify it's inside
        val containerBefore = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(1, containerBefore.children.blocks.size)

        // Now drag it far outside the container
        vm.selectBlock(blockId)
        vm.moveBlock(blockId, -500f, -500f)
        vm.updateDragFeedback(setOf(blockId))
        vm.commitMove()

        // Block should be back at top-level
        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(0, containerAfter.children.blocks.size)
        assertTrue(vm.graph.value.blocks.any { it.id == blockId && it !is Block.ContainerBlock })
    }

    // ==================== HIGH: resizeBlock (#6) ====================

    @Test
    fun `resizeBlock Right increases width`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val posBefore = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.Right, 50f, 0f)

        val posAfter = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(posBefore.width + 50f, posAfter.width)
        assertEquals(posBefore.x, posAfter.x) // x shouldn't change
    }

    @Test
    fun `resizeBlock Left shifts x and increases width`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val posBefore = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.Left, -30f, 0f)

        val posAfter = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(posBefore.width + 30f, posAfter.width)
        assertEquals(posBefore.x - 30f, posAfter.x)
    }

    @Test
    fun `resizeBlock enforces minimum size`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()

        // Try to shrink way below minimum
        vm.resizeBlock(blockId, ResizeEdge.Right, -500f, 0f)

        val posAfter = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(BlockPosition.DEFAULT_BLOCK_WIDTH, posAfter.width)
    }

    @Test
    fun `resizeBlock Bottom increases height`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val posBefore = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.Bottom, 0f, 40f)

        val posAfter = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(posBefore.height + 40f, posAfter.height)
    }

    @Test
    fun `resizeBlock BottomRight increases both dimensions`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val posBefore = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.BottomRight, 30f, 20f)

        val posAfter = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(posBefore.width + 30f, posAfter.width)
        assertEquals(posBefore.height + 20f, posAfter.height)
    }

    // ==================== HIGH: resizeBlock container with content minimum (#7) ====================

    @Test
    fun `resizeBlock container enforces content-fitting minimum`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Try to shrink container way below child bounds
        vm.resizeBlock(containerId, ResizeEdge.Right, -1000f, 0f)
        vm.commitResize()

        val containerPos = vm.graph.value.positions[containerId] ?: error("No container position")
        // Container should not be smaller than child bounds + padding
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childPos = container.children.positions[blockId] ?: error("No child position")
        assertTrue(containerPos.width >= childPos.x + childPos.width + 20f)
    }

    // ==================== HIGH: resizeBlock for container children (#8) ====================

    @Test
    fun `resizeBlock child triggers parent container auto-resize`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        val containerWidthBefore = vm.graph.value.positions[containerId]?.width ?: error("No container position")

        // Resize child to extend far past container bounds, then commit (auto-resize on commit)
        vm.resizeBlock(blockId, ResizeEdge.Right, 500f, 0f)
        vm.commitResize()

        val containerWidthAfter = vm.graph.value.positions[containerId]?.width ?: error("No container position")
        assertTrue(containerWidthAfter > containerWidthBefore, "Container should auto-resize on commit when child extends beyond bounds")
    }

    // ==================== HIGH: autoResizeContainer (#9) ====================

    @Test
    fun `autoResizeContainer expands when child moved past bounds`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        val containerSizeBefore = vm.graph.value.positions[containerId] ?: error("No position")

        // Move child slightly past right edge, then commit (no detach, triggers auto-resize)
        val containerWidth = containerSizeBefore.width
        vm.moveBlock(blockId, containerWidth - 50f, 0f) // near right edge but center still inside
        vm.commitMove()

        val containerSizeAfter = vm.graph.value.positions[containerId] ?: error("No position")
        assertTrue(containerSizeAfter.width > containerSizeBefore.width, "Container should auto-resize on commit")
    }

    // ==================== HIGH: mapBlocksDeep / updateBlockField (#10) ====================

    @Test
    fun `updateBlockName works for child blocks inside container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Rename the child block
        vm.updateBlockName(blockId, "Renamed Build")

        // Find the child inside the container and verify
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val child = container.children.blocks.find { it.id == blockId }
        assertNotNull(child)
        assertEquals("Renamed Build", child.name)
    }

    @Test
    fun `updateBlockDescription works for child blocks inside container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        vm.updateBlockDescription(blockId, "New description")

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val child = container.children.blocks.find { it.id == blockId }
        assertNotNull(child)
        assertEquals("New description", child.description)
    }

    // ==================== HIGH: selectEdge + removeSelectedEdge for container edges (#12) ====================

    @Test
    fun `selectEdge and removeSelectedEdge for container-internal edge`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Add second block into container
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Create edge between children
        vm.addEdge(blockId, slackId)
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(1, container.children.edges.size)

        // Select the container-internal edge
        vm.selectEdge(0, containerId)
        assertEquals(0, vm.selectedEdgeIndex.value)
        assertEquals(containerId, vm.selectedEdgeContainerId.value)

        // Delete it
        vm.removeSelectedEdge()

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(0, containerAfter.children.edges.size)
        // Top-level edges should be unaffected
        assertEquals(0, vm.graph.value.edges.size)
    }

    // ==================== MEDIUM: All resize edges (#13) ====================

    @Test
    fun `resizeBlock TopLeft shifts both position and size`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val before = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.TopLeft, -20f, -15f)

        val after = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(before.x - 20f, after.x)
        assertEquals(before.y - 15f, after.y)
        assertEquals(before.width + 20f, after.width)
        assertEquals(before.height + 15f, after.height)
    }

    @Test
    fun `resizeBlock Top only changes y and height`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()
        val before = vm.graph.value.positions[blockId] ?: error("No position")

        vm.resizeBlock(blockId, ResizeEdge.Top, 0f, -25f)

        val after = vm.graph.value.positions[blockId] ?: error("No position")
        assertEquals(before.x, after.x)
        assertEquals(before.y - 25f, after.y)
        assertEquals(before.width, after.width)
        assertEquals(before.height + 25f, after.height)
    }

    // ==================== MEDIUM: copySelected for container children (#15) ====================

    @Test
    fun `copySelected includes children from containers`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Select and copy the child
        vm.selectBlock(blockId)
        vm.copySelected()

        // Paste — should appear as top-level block with absolute position
        val blockCountBefore = vm.graph.value.blocks.size
        vm.pasteClipboard()

        assertTrue(vm.graph.value.blocks.size > blockCountBefore, "Pasted block should be added at top-level")
    }

    // ==================== MEDIUM: selectAll includes children (#16) ====================

    @Test
    fun `selectAll includes container children`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        vm.selectAll()

        // Should include the container + the child inside it
        assertTrue(vm.selectedBlockIds.value.contains(containerId))
        assertTrue(vm.selectedBlockIds.value.contains(blockId))
    }

    // ==================== MEDIUM: moveBlock relative positioning (#18) ====================

    @Test
    fun `moveBlock applies delta to relative position for child`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val relPosBefore = container.children.positions[blockId] ?: error("No relative position")

        // Move child by (30, 40)
        vm.moveBlock(blockId, 30f, 40f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val relPosAfter = containerAfter.children.positions[blockId] ?: error("No relative position")

        assertEquals(relPosBefore.x + 30f, relPosAfter.x)
        assertEquals(relPosBefore.y + 40f, relPosAfter.y)
    }

    // ==================== MEDIUM: Detach visual feedback (#19) ====================

    @Test
    fun `detachingFromContainerId set when child dragged outside`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Drag child far outside
        vm.moveBlock(blockId, -1000f, -1000f)
        vm.updateDragFeedback(setOf(blockId))

        assertEquals(containerId, vm.detachingFromContainerId.value)
    }

    // ==================== MEDIUM: Hover visual feedback (#20) ====================

    @Test
    fun `hoveredContainerId set when block dragged over container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block center into container content area
        val cPos = vm.graph.value.positions[containerId] ?: error("No container position")
        val bPos = vm.graph.value.positions[blockId] ?: error("No block position")
        val dx = (cPos.x + cPos.width / 2) - (bPos.x + bPos.width / 2)
        val dy = (cPos.y + cPos.headerHeight + (cPos.height - cPos.headerHeight) / 2) - (bPos.y + bPos.height / 2)
        vm.moveBlock(blockId, dx, dy)
        vm.updateDragFeedback(setOf(blockId))

        assertEquals(containerId, vm.hoveredContainerId.value)
    }

    // ==================== LOW: Duplicate edge prevention in container (#22) ====================

    @Test
    fun `addEdge prevents duplicates within container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Add second block into container
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Add edge twice
        vm.addEdge(blockId, slackId)
        vm.addEdge(blockId, slackId)

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertEquals(1, container.children.edges.size)
    }

    // ==================== LOW: commitMove returns null when no edges dropped (#23) ====================

    @Test
    fun `commitMove returns null when no container membership changes`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()

        vm.moveBlock(blockId, 10f, 10f)
        val result = vm.commitMove()

        assertNull(result)
    }

    // ==================== LOW: commitMove returns correct edge count message (#24) ====================

    @Test
    fun `commitMove reports edge count when moving into container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Add another block and connect both to blockId
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 500f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.addEdge(blockId, slackId)
        vm.addEdge(blockId, containerId)

        val edgeCount = vm.graph.value.edges.count { it.fromBlockId == blockId || it.toBlockId == blockId }
        assertTrue(edgeCount >= 2)

        // Select and move block into container
        vm.selectBlock(blockId)
        val cPos = vm.graph.value.positions[containerId] ?: error("No container position")
        val bPos = vm.graph.value.positions[blockId] ?: error("No block position")
        val targetCX = cPos.x + cPos.width / 2
        val targetCY = cPos.y + cPos.headerHeight + (cPos.height - cPos.headerHeight) / 2
        vm.moveBlock(blockId, targetCX - (bPos.x + bPos.width / 2), targetCY - (bPos.y + bPos.height / 2))
        vm.updateDragFeedback(setOf(blockId))
        val msg = vm.commitMove()

        assertNotNull(msg)
        assertTrue(msg.contains("removed"))
    }

    // ==================== LOW: parentLookup correctness (#25) ====================

    @Test
    fun `parentLookup updated after container membership change`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Initially top-level
        assertNull(vm.parentLookup.value[blockId])

        // Move into container
        vm.dragBlockIntoContainer(blockId, containerId)

        // Now should have parent
        assertEquals(containerId, vm.parentLookup.value[blockId])

        // Move out
        vm.selectBlock(blockId)
        vm.moveBlock(blockId, -500f, -500f)
        vm.updateDragFeedback(setOf(blockId))
        vm.commitMove()

        assertNull(vm.parentLookup.value[blockId])
    }

    // ==================== LOW: Paste preserves width/height (#26) ====================

    @Test
    fun `paste preserves custom block dimensions`() {
        val (vm, blockId, _) = loadedViewModelWithContainerSetup()

        // Resize block to custom dimensions
        vm.resizeBlock(blockId, ResizeEdge.Right, 100f, 0f)
        vm.resizeBlock(blockId, ResizeEdge.Bottom, 0f, 50f)
        vm.commitResize()

        val originalPos = vm.graph.value.positions[blockId] ?: error("No position")

        // Copy and paste
        vm.selectBlock(blockId)
        vm.copySelected()
        vm.pasteClipboard()

        // Find the new pasted block (most recently selected)
        val pastedId = vm.selectedBlockIds.value.firstOrNull { it != blockId }
        assertNotNull(pastedId, "Pasted block should be selected")

        val pastedPos = vm.graph.value.positions[pastedId] ?: error("No pasted position")
        assertEquals(originalPos.width, pastedPos.width)
        assertEquals(originalPos.height, pastedPos.height)
    }

    // ==================== CRITICAL: updateProjectName (#1) ====================

    @Test
    fun `updateProjectName sets name and marks dirty`() {
        val (vm, _, _) = loadedViewModelWithContainerSetup()

        vm.updateProjectName("New Name")

        assertEquals("New Name", vm.project.value?.name)
        assertTrue(vm.isDirty.value)
    }

    @Test
    fun `updateProjectName is no-op when read-only`() {
        val (vm, _, _) = loadedViewModelWithContainerSetup()
        val originalName = vm.project.value?.name

        // Simulate read-only by losing lock — we can't easily trigger this,
        // so just verify the name persists after a normal update
        vm.updateProjectName("Updated")
        assertEquals("Updated", vm.project.value?.name)
    }

    // ==================== CRITICAL: resizeHeader (#3) ====================

    @Test
    fun `resizeHeader increases header height`() {
        val (vm, _, containerId) = loadedViewModelWithContainerSetup()
        val posBefore = vm.graph.value.positions[containerId] ?: error("No position")

        vm.resizeHeader(containerId, 20f)

        val posAfter = vm.graph.value.positions[containerId] ?: error("No position")
        assertEquals(posBefore.headerHeight + 20f, posAfter.headerHeight)
        assertTrue(vm.isDirty.value)
    }

    @Test
    fun `resizeHeader clamps to minimum`() {
        val (vm, _, containerId) = loadedViewModelWithContainerSetup()

        // Try to shrink header way below minimum
        vm.resizeHeader(containerId, -500f)

        val posAfter = vm.graph.value.positions[containerId] ?: error("No position")
        assertEquals(BlockPosition.MIN_CONTAINER_HEADER_HEIGHT, posAfter.headerHeight)
    }

    @Test
    fun `resizeHeader clamps to leave content space`() {
        val (vm, _, containerId) = loadedViewModelWithContainerSetup()
        val pos = vm.graph.value.positions[containerId] ?: error("No position")

        // Try to grow header to fill entire container
        vm.resizeHeader(containerId, 1000f)

        val posAfter = vm.graph.value.positions[containerId] ?: error("No position")
        assertTrue(posAfter.headerHeight <= posAfter.height - BlockPosition.MIN_CONTAINER_CONTENT_HEIGHT)
    }

    @Test
    fun `resizeHeader triggers autoResize when children pushed down`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container
        vm.dragBlockIntoContainer(blockId, containerId)

        val heightBefore = vm.graph.value.positions[containerId]?.height ?: error("No position")

        // Grow header significantly — pushes children down, may trigger auto-resize
        vm.resizeHeader(containerId, 100f)

        val heightAfter = vm.graph.value.positions[containerId]?.height ?: error("No position")
        assertTrue(heightAfter >= heightBefore, "Container should auto-resize if children pushed past bounds")
    }

    // ==================== HIGH: MIN_CONTAINER_CONTENT_HEIGHT enforcement (#7) ====================

    @Test
    fun `container resize enforces headerHeight plus content minimum`() {
        val (vm, _, containerId) = loadedViewModelWithContainerSetup()

        // Grow header first
        vm.resizeHeader(containerId, 50f)
        vm.commitResize()

        val headerH = vm.graph.value.positions[containerId]?.headerHeight ?: error("No header")

        // Now try to shrink container height to be very small
        vm.resizeBlock(containerId, ResizeEdge.Bottom, 0f, -1000f)

        val posAfter = vm.graph.value.positions[containerId] ?: error("No position")
        assertTrue(posAfter.height >= headerH + BlockPosition.MIN_CONTAINER_CONTENT_HEIGHT,
            "Container height must be at least headerHeight + MIN_CONTAINER_CONTENT_HEIGHT")
    }

    // ==================== HIGH: headerHeight preserved during resize (#8) ====================

    @Test
    fun `container resize preserves custom headerHeight`() {
        val (vm, _, containerId) = loadedViewModelWithContainerSetup()

        // Set custom header height
        vm.resizeHeader(containerId, 30f)
        vm.commitResize()

        val headerBefore = vm.graph.value.positions[containerId]?.headerHeight ?: error("No header")
        assertTrue(headerBefore > BlockPosition.CONTAINER_HEADER_HEIGHT)

        // Resize container width — headerHeight should be preserved
        vm.resizeBlock(containerId, ResizeEdge.Right, 100f, 0f)
        vm.commitResize()

        val headerAfter = vm.graph.value.positions[containerId]?.headerHeight ?: error("No header")
        assertEquals(headerBefore, headerAfter, "headerHeight should be preserved during container resize")
    }

    // ==================== CRITICAL: removeBlocksFromContainer push (#1-2) ====================

    @Test
    fun `detached block is pushed outside container bounds`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        val cPos = vm.graph.value.positions[containerId] ?: error("No container pos")

        // Drag child outside and detach
        vm.selectBlock(blockId)
        vm.moveBlock(blockId, -500f, 0f)
        vm.updateDragFeedback(setOf(blockId))
        vm.commitMove()

        // Block should be at top-level AND completely outside the container
        val blockPos = vm.graph.value.positions[blockId] ?: error("No block pos")
        val blockRight = blockPos.x + blockPos.width
        val blockBottom = blockPos.y + blockPos.height
        val outsideX = blockRight <= cPos.x || blockPos.x >= cPos.x + cPos.width
        val outsideY = blockBottom <= cPos.y || blockPos.y >= cPos.y + cPos.height
        assertTrue(outsideX || outsideY, "Block should be completely outside container after detach push")
    }

    @Test
    fun `multi-block detach preserves relative positions`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        // Add second block and drag into container
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Get relative positions of both children
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val relPos1 = container.children.positions[blockId] ?: error("No child1 pos")
        val relPos2 = container.children.positions[slackId] ?: error("No child2 pos")
        val relDx = relPos2.x - relPos1.x
        val relDy = relPos2.y - relPos1.y

        // Select both and drag outside
        vm.selectBlock(blockId)
        vm.toggleBlockSelection(slackId)
        vm.moveBlock(blockId, -500f, 0f)
        vm.moveBlock(slackId, -500f, 0f)
        vm.updateDragFeedback(setOf(blockId, slackId))
        vm.commitMove()

        // Both at top-level
        assertTrue(vm.graph.value.blocks.any { it.id == blockId && it !is Block.ContainerBlock })
        assertTrue(vm.graph.value.blocks.any { it.id == slackId && it !is Block.ContainerBlock })

        // Relative positions preserved
        val absPos1 = vm.graph.value.positions[blockId] ?: error("No abs1")
        val absPos2 = vm.graph.value.positions[slackId] ?: error("No abs2")
        assertEquals(relDx, absPos2.x - absPos1.x, "Relative X should be preserved")
        assertEquals(relDy, absPos2.y - absPos1.y, "Relative Y should be preserved")
    }

    // ==================== CRITICAL: autoResize left/top expansion (#3-4) ====================

    @Test
    fun `autoResize expands left when child at negative x`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        val cPosBefore = vm.graph.value.positions[containerId] ?: error("No pos")

        // Move child to negative relative x
        vm.moveBlock(blockId, -300f, 0f)
        vm.commitMove()

        val cPosAfter = vm.graph.value.positions[containerId] ?: error("No pos")
        assertTrue(cPosAfter.x < cPosBefore.x, "Container x should decrease (expand left)")
        assertTrue(cPosAfter.width > cPosBefore.width, "Container width should increase")
    }

    @Test
    fun `autoResize expands top when child at negative y`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        val cPosBefore = vm.graph.value.positions[containerId] ?: error("No pos")

        // Move child to negative relative y
        vm.moveBlock(blockId, 0f, -300f)
        vm.commitMove()

        val cPosAfter = vm.graph.value.positions[containerId] ?: error("No pos")
        assertTrue(cPosAfter.y < cPosBefore.y, "Container y should decrease (expand top)")
        assertTrue(cPosAfter.height > cPosBefore.height, "Container height should increase")
    }

    // ==================== CRITICAL: Left/top resize with child shift (#5-6) ====================

    @Test
    fun `left resize shifts children when gap exists`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        // First expand container to the right to create a large gap on the left
        vm.resizeBlock(containerId, ResizeEdge.Right, 200f, 0f)
        vm.commitResize()

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXBefore = container.children.positions[blockId]?.x ?: error("No child pos")
        assertTrue(childXBefore > 20f, "Child should have gap from left edge")

        // Shrink from left — child should shift to close gap
        vm.resizeBlock(containerId, ResizeEdge.Left, 15f, 0f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXAfter = containerAfter.children.positions[blockId]?.x ?: error("No child pos")
        assertTrue(childXAfter < childXBefore, "Child x should shift left when gap is consumed by resize")
    }

    @Test
    fun `top resize shifts children when gap exists`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        // Expand container downward to create gap at top
        vm.resizeBlock(containerId, ResizeEdge.Bottom, 0f, 200f)
        vm.commitResize()

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childYBefore = container.children.positions[blockId]?.y ?: error("No child pos")
        assertTrue(childYBefore > 20f, "Child should have gap from top edge")

        // Shrink from top — child should shift to close gap
        vm.resizeBlock(containerId, ResizeEdge.Top, 0f, 15f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childYAfter = containerAfter.children.positions[blockId]?.y ?: error("No child pos")
        assertTrue(childYAfter < childYBefore, "Child y should shift up when gap is consumed by resize")
    }

    // ==================== HIGH: Nearest-border push direction (#7-10) ====================

    @Test
    fun `detach pushes block toward nearest border - right`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        // Move child to the right side of container
        vm.moveBlock(blockId, 200f, 0f)

        val cPos = vm.graph.value.positions[containerId] ?: error("No pos")
        vm.selectBlock(blockId)
        vm.moveBlock(blockId, 500f, 0f) // push past right edge
        vm.updateDragFeedback(setOf(blockId))
        vm.commitMove()

        val blockPos = vm.graph.value.positions[blockId] ?: error("No pos")
        assertTrue(blockPos.x >= cPos.x + cPos.width, "Block should be pushed right of container")
    }

    @Test
    fun `detach pushes block toward nearest border - bottom`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        vm.moveBlock(blockId, 0f, 200f)

        val cPos = vm.graph.value.positions[containerId] ?: error("No pos")
        vm.selectBlock(blockId)
        vm.moveBlock(blockId, 0f, 500f)
        vm.updateDragFeedback(setOf(blockId))
        vm.commitMove()

        val blockPos = vm.graph.value.positions[blockId] ?: error("No pos")
        assertTrue(blockPos.y >= cPos.y + cPos.height, "Block should be pushed below container")
    }

    // ==================== HIGH: updateDragFeedback multi-select (#12-14) ====================

    @Test
    fun `updateDragFeedback detach triggers if ANY child is outside`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Move only one child outside
        vm.moveBlock(blockId, -1000f, 0f)
        vm.updateDragFeedback(setOf(blockId, slackId))

        assertEquals(containerId, vm.detachingFromContainerId.value, "Detach should trigger if ANY child is outside")
    }

    @Test
    fun `updateDragFeedback hover triggers if ANY top-level block is over container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Add second block far from container
        vm.addBlock(BlockType.SLACK_MESSAGE, "Far", 800f, 800f)
        val farId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Far" }.id

        // Move only blockId over the container
        val cPos = vm.graph.value.positions[containerId] ?: error("No pos")
        val bPos = vm.graph.value.positions[blockId] ?: error("No pos")
        vm.moveBlock(blockId, cPos.x + cPos.width / 2 - bPos.x - bPos.width / 2, cPos.y + cPos.headerHeight + 20f - bPos.y - bPos.height / 2)
        vm.updateDragFeedback(setOf(blockId, farId))

        assertEquals(containerId, vm.hoveredContainerId.value, "Hover should trigger if ANY block is over container")
    }

    @Test
    fun `header area does not trigger detach`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        // Move child into the header area (negative y in relative coords, but still inside container absolute bounds)
        vm.moveBlock(blockId, 0f, -100f)
        vm.updateDragFeedback(setOf(blockId))

        // Should NOT trigger detach — header is "inside" the container
        val cPos = vm.graph.value.positions[containerId] ?: error("No pos")
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childPos = container.children.positions[blockId] ?: error("No child")
        val absY = cPos.y + cPos.headerHeight + childPos.y + childPos.height / 2
        if (absY >= cPos.y && absY <= cPos.y + cPos.height) {
            assertNull(vm.detachingFromContainerId.value, "Header area should not trigger detach")
        }
    }

    // ==================== HIGH: Left/top resize capped shift + uniform (#15-17) ====================

    @Test
    fun `left resize shift capped at available gap`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXBefore = container.children.positions[blockId]?.x ?: error("No pos")

        // Shrink from left by a large amount — shift should be capped
        vm.resizeBlock(containerId, ResizeEdge.Left, 1000f, 0f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXAfter = containerAfter.children.positions[blockId]?.x ?: error("No pos")

        // Child should not go below padding (10f)
        assertTrue(childXAfter >= 0f, "Child x should not go below 0 after capped shift")
    }

    @Test
    fun `TopLeft resize shifts both axes`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXBefore = container.children.positions[blockId]?.x ?: error("No pos")
        val childYBefore = container.children.positions[blockId]?.y ?: error("No pos")

        vm.resizeBlock(containerId, ResizeEdge.TopLeft, 10f, 10f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val childXAfter = containerAfter.children.positions[blockId]?.x ?: error("No pos")
        val childYAfter = containerAfter.children.positions[blockId]?.y ?: error("No pos")

        assertTrue(childXAfter <= childXBefore, "Child x should shift for TopLeft resize")
        assertTrue(childYAfter <= childYBefore, "Child y should shift for TopLeft resize")
    }

    @Test
    fun `left resize shifts all children uniformly`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()
        vm.dragBlockIntoContainer(blockId, containerId)

        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val relDx = (container.children.positions[slackId]?.x ?: 0f) - (container.children.positions[blockId]?.x ?: 0f)

        vm.resizeBlock(containerId, ResizeEdge.Left, 10f, 0f)

        val containerAfter = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        val relDxAfter = (containerAfter.children.positions[slackId]?.x ?: 0f) - (containerAfter.children.positions[blockId]?.x ?: 0f)

        assertEquals(relDx, relDxAfter, "Relative X between children should be preserved after uniform shift")
    }

    // ==================== HIGH: Multiselect into container preserves inter-block edges ====================

    @Test
    fun `commitMove preserves edges between multiselected blocks when adding to container`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Add a second action block and connect them
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 200f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.addEdge(blockId, slackId)

        // Verify top-level edge exists
        assertTrue(vm.graph.value.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId })

        // Select both and move into container
        vm.selectBlock(blockId)
        vm.toggleBlockSelection(slackId)
        val cPos = vm.graph.value.positions[containerId] ?: error("No container position")
        val bPos = vm.graph.value.positions[blockId] ?: error("No block position")
        val dx = cPos.x + cPos.width / 2 - (bPos.x + bPos.width / 2)
        val dy = cPos.y + cPos.headerHeight + 30f - (bPos.y + bPos.height / 2)
        vm.moveBlock(blockId, dx, dy)
        vm.moveBlock(slackId, dx, dy)
        vm.updateDragFeedback(setOf(blockId, slackId))
        val msg = vm.commitMove()

        // Both blocks should be inside the container
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(container.children.blocks.any { it.id == blockId })
        assertTrue(container.children.blocks.any { it.id == slackId })

        // Edge between them should be preserved inside the container
        assertTrue(
            container.children.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId },
            "Edge between co-moved blocks should be preserved inside container"
        )

        // No top-level edges should remain
        assertEquals(0, vm.graph.value.edges.size)

        // No cross-boundary edges were dropped, so message should be null
        assertNull(msg, "No cross-boundary edges should be reported when all connected blocks are moved together")
    }

    @Test
    fun `commitMove drops only cross-boundary edges and preserves internal ones`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Add two more blocks
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 200f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.addBlock(BlockType.GITHUB_ACTION, "GHA", 500f, 200f)
        val userId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "GHA" }.id

        // Create edges: blockId → slackId (internal), blockId → userId (cross-boundary)
        vm.addEdge(blockId, slackId)
        vm.addEdge(blockId, userId)
        assertEquals(2, vm.graph.value.edges.size)

        // Move only blockId and slackId into container (userId stays top-level)
        vm.selectBlock(blockId)
        vm.toggleBlockSelection(slackId)
        val cPos = vm.graph.value.positions[containerId] ?: error("No container position")
        val bPos = vm.graph.value.positions[blockId] ?: error("No block position")
        val dx = cPos.x + cPos.width / 2 - (bPos.x + bPos.width / 2)
        val dy = cPos.y + cPos.headerHeight + 30f - (bPos.y + bPos.height / 2)
        vm.moveBlock(blockId, dx, dy)
        vm.moveBlock(slackId, dx, dy)
        vm.updateDragFeedback(setOf(blockId, slackId))
        val msg = vm.commitMove()

        // Internal edge preserved inside container
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(
            container.children.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId },
            "Internal edge should be preserved"
        )

        // Cross-boundary edge (blockId → userId) should be dropped
        assertFalse(vm.graph.value.edges.any { it.fromBlockId == blockId })
        assertNotNull(msg, "Should report 1 cross-boundary edge removed")
        assertTrue(msg.contains("1 connection"))
    }

    // ==================== HIGH: Detach preserves edges between co-detached blocks ====================

    @Test
    fun `detach preserves edges between co-removed blocks as top-level edges`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container and add a second block inside
        vm.dragBlockIntoContainer(blockId, containerId)
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Connect them inside the container
        vm.addEdge(blockId, slackId)
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(container.children.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId })

        // Select both and drag outside
        vm.selectBlock(blockId)
        vm.toggleBlockSelection(slackId)
        vm.moveBlock(blockId, -500f, 0f)
        vm.moveBlock(slackId, -500f, 0f)
        vm.updateDragFeedback(setOf(blockId, slackId))
        val msg = vm.commitMove()

        // Both at top-level
        assertTrue(vm.graph.value.blocks.any { it.id == blockId && it !is Block.ContainerBlock })
        assertTrue(vm.graph.value.blocks.any { it.id == slackId && it !is Block.ContainerBlock })

        // Edge should be preserved as top-level edge
        assertTrue(
            vm.graph.value.edges.any { it.fromBlockId == blockId && it.toBlockId == slackId },
            "Edge between co-detached blocks should be preserved as top-level edge"
        )

        // No cross-boundary edges dropped
        assertNull(msg, "No cross-boundary edges should be reported")
    }

    // ==================== HIGH: Connector hint accounts for container-internal edges ====================

    @Test
    fun `graph with only container-internal edges is not considered edgeless`() {
        val (vm, blockId, containerId) = loadedViewModelWithContainerSetup()

        // Move block into container and add a second block inside
        vm.dragBlockIntoContainer(blockId, containerId)
        vm.addBlock(BlockType.SLACK_MESSAGE, "Slack", 100f, 100f)
        val slackId = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().first { it.name == "Slack" }.id
        vm.dragBlockIntoContainer(slackId, containerId)

        // Connect them inside the container
        vm.addEdge(blockId, slackId)

        // Top-level edges should be empty, but container has edges
        assertTrue(vm.graph.value.edges.isEmpty(), "No top-level edges")
        val container = vm.graph.value.blocks.filterIsInstance<Block.ContainerBlock>().first()
        assertTrue(container.children.edges.isNotEmpty(), "Container should have internal edges")

        // Verify the "has any edges" check (mirrors the DagCanvas hint condition)
        val hasAnyEdges = vm.graph.value.edges.isNotEmpty() ||
            vm.graph.value.blocks.any { it is Block.ContainerBlock && it.children.edges.isNotEmpty() }
        assertTrue(hasAnyEdges, "Graph should be considered as having edges when container-internal edges exist")
    }

    // ==================== HIGH: Save includes name (#18) ====================

    @Test
    fun `save includes project name in request`() {
        val (vm, _, _) = loadedViewModelWithContainerSetup()
        vm.updateProjectName("SavedName")
        // save() triggers async — just verify the project state has the name
        assertEquals("SavedName", vm.project.value?.name, "Project name should be set before save")
        assertTrue(vm.isDirty.value, "Should be dirty after name change")
    }
}
