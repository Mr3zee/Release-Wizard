package com.github.mr3zee

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.github.mr3zee.editor.DagCanvas
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ExecutionDagCanvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DagCanvas drag-and-drop interactions and ExecutionDagCanvas click/pan.
 *
 * Coordinate assumptions (test environment density = 1.0, zoom = 1.0, panOffset = Zero):
 * - Block at BlockPosition(100, 100): top-left=(100,100), center=(190,135)
 *   - Output port: (280, 135)
 *   - Input port: (100, 135)
 * - BLOCK_WIDTH=180, BLOCK_HEIGHT=70
 */
@OptIn(ExperimentalTestApi::class)
class DagCanvasTest {

    // ---- DagCanvas: Click / Selection ----

    @Test
    fun `click on block selects it`() = runComposeUiTest {
        var selectedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = {},
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f)) // block center
        }

        waitForIdle()
        assertEquals(BlockId("b1"), selectedBlockId)
    }

    @Test
    fun `click on empty space deselects`() = runComposeUiTest {
        var selectedBlockId: BlockId? = BlockId("initial")
        var selectedEdgeIndex: Int? = 0
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = { selectedEdgeIndex = it },
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Click far from any block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(500f, 500f))
        }

        waitForIdle()
        assertNull(selectedBlockId)
        assertNull(selectedEdgeIndex)
    }

    // ---- DagCanvas: Block Drag ----

    @Test
    fun `drag block calls onMoveBlock and onCommitMove`() = runComposeUiTest {
        val movedDeltas = mutableListOf<Triple<BlockId, Float, Float>>()
        var commitCalled = false
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = {},
                    onSelectEdge = {},
                    onMoveBlock = { id, dx, dy -> movedDeltas.add(Triple(id, dx, dy)) },
                    onCommitMove = { commitCalled = true },
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(190f, 135f)) // block center
            // Move by enough to exceed 2px drag threshold
            moveTo(Offset(250f, 195f))
            up()
        }

        waitForIdle()
        assertTrue(movedDeltas.isNotEmpty(), "onMoveBlock should have been called")
        assertEquals(BlockId("b1"), movedDeltas.first().first)
        assertTrue(commitCalled, "onCommitMove should have been called on release")
    }

    @Test
    fun `small drag within threshold does not trigger move`() = runComposeUiTest {
        val movedDeltas = mutableListOf<Triple<BlockId, Float, Float>>()
        var selectedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = {},
                    onMoveBlock = { id, dx, dy -> movedDeltas.add(Triple(id, dx, dy)) },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Move only 1 pixel — within the 2px drag threshold
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(190f, 135f))
            moveTo(Offset(191f, 135f))
            up()
        }

        waitForIdle()
        // Should be treated as a click, not a drag
        assertTrue(movedDeltas.isEmpty(), "onMoveBlock should NOT be called for small movement")
        assertEquals(BlockId("b1"), selectedBlockId, "Block should be selected as click")
    }

    // ---- DagCanvas: Edge Creation (Port-to-Port Drag) ----

    @Test
    fun `drag from output port to input port creates edge`() = runComposeUiTest {
        var createdEdge: Pair<BlockId, BlockId>? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b2"), name = "Deploy", type = BlockType.GITHUB_ACTION),
            ),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 100f),
                BlockId("b2") to BlockPosition(400f, 100f),
            ),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = {},
                    onSelectEdge = {},
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { from, to -> createdEdge = from to to },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Drag from b1 output port (280, 135) to b2 input port (400, 135)
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(280f, 135f))
            moveTo(Offset(400f, 135f))
            up()
        }

        waitForIdle()
        val edge = createdEdge
        assertNotNull(edge, "Edge should be created")
        assertEquals(BlockId("b1"), edge.first)
        assertEquals(BlockId("b2"), edge.second)
    }

    @Test
    fun `drag from output port to empty space does not create edge`() = runComposeUiTest {
        var createdEdge: Pair<BlockId, BlockId>? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = {},
                    onSelectEdge = {},
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { from, to -> createdEdge = from to to },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Drag from output port to empty space
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(280f, 135f))
            moveTo(Offset(500f, 300f))
            up()
        }

        waitForIdle()
        assertNull(createdEdge, "Edge should NOT be created when dropping on empty space")
    }

    // ---- DagCanvas: Pan ----

    @Test
    fun `drag on empty space does not select or move anything`() = runComposeUiTest {
        var selectedBlockId: BlockId? = BlockId("untouched")
        val movedDeltas = mutableListOf<Triple<BlockId, Float, Float>>()
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = {},
                    onMoveBlock = { id, dx, dy -> movedDeltas.add(Triple(id, dx, dy)) },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Drag on empty space (far from any block)
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(600f, 400f))
            moveTo(Offset(650f, 450f))
            up()
        }

        waitForIdle()
        // No block should be selected (drag was on empty)
        assertTrue(movedDeltas.isEmpty(), "No block move should occur for empty space drag")
        // selectedBlockId should still have the initial sentinel, meaning onSelectBlock wasn't called
        // (drag triggers panning, not selection)
        assertEquals(BlockId("untouched"), selectedBlockId)
    }

    // ---- DagCanvas: Multiple blocks ----

    @Test
    fun `click selects correct block when blocks overlap in z-order`() = runComposeUiTest {
        var selectedBlockId: BlockId? = null
        // Two blocks at the same position — b2 is later in list so drawn on top
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Bottom", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b2"), name = "Top", type = BlockType.GITHUB_ACTION),
            ),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 100f),
                BlockId("b2") to BlockPosition(100f, 100f), // same position
            ),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = {},
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }

        waitForIdle()
        // Hit test checks blocks in reverse order — b2 (topmost) should be selected
        assertEquals(BlockId("b2"), selectedBlockId)
    }

    // ---- DagCanvas: Edge Selection ----

    @Test
    fun `click on edge selects it`() = runComposeUiTest {
        var selectedEdgeIndex: Int? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b2"), name = "Deploy", type = BlockType.GITHUB_ACTION),
            ),
            edges = listOf(Edge(fromBlockId = BlockId("b1"), toBlockId = BlockId("b2"))),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 100f),
                BlockId("b2") to BlockPosition(400f, 100f),
            ),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = {},
                    onSelectEdge = { selectedEdgeIndex = it },
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Click near the midpoint of the bezier curve between b1 output (280,135) and b2 input (400,135)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(340f, 135f))
        }

        waitForIdle()
        assertEquals(0, selectedEdgeIndex)
    }

    // ---- DagCanvas: Port Hit Testing ----

    @Test
    fun `click on input port selects block`() = runComposeUiTest {
        var selectedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = { selectedBlockId = it },
                    onSelectEdge = {},
                    onMoveBlock = { _, _, _ -> },
                    onCommitMove = {},
                    onAddEdge = { _, _ -> },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Click exactly at the input port position of b1
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(100f, 135f))
        }

        waitForIdle()
        assertEquals(BlockId("b1"), selectedBlockId)
    }

    @Test
    fun `drag from input port pans canvas`() = runComposeUiTest {
        var createdEdge: Pair<BlockId, BlockId>? = null
        val movedDeltas = mutableListOf<Triple<BlockId, Float, Float>>()
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b2"), name = "Deploy", type = BlockType.GITHUB_ACTION),
            ),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 100f),
                BlockId("b2") to BlockPosition(400f, 100f),
            ),
        )

        setContent {
            MaterialTheme {
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = emptySet(),
                    selectedEdgeIndex = null,
                    onSelectBlock = {},
                    onSelectEdge = {},
                    onMoveBlock = { id, dx, dy -> movedDeltas.add(Triple(id, dx, dy)) },
                    onCommitMove = {},
                    onAddEdge = { from, to -> createdEdge = from to to },
                    modifier = Modifier.size(800.dp, 600.dp).testTag("dag_canvas"),
                )
            }
        }

        // Drag starting from b1's input port — should trigger panning, not edge creation
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(100f, 135f))
            moveTo(Offset(150f, 185f))
            up()
        }

        waitForIdle()
        assertNull(createdEdge, "Dragging from input port should not create an edge")
        assertTrue(movedDeltas.isEmpty(), "Dragging from input port should not move any block")
    }

    // ---- ExecutionDagCanvas ----

    @Test
    fun `execution canvas click on block calls onBlockClick`() = runComposeUiTest {
        var clickedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                ExecutionDagCanvas(
                    graph = graph,
                    blockExecutions = listOf(
                        BlockExecution(blockId = BlockId("b1"), releaseId = ReleaseId("r1"), status = BlockStatus.RUNNING),
                    ),
                    onBlockClick = { clickedBlockId = it },
                    modifier = Modifier.size(800.dp, 600.dp),
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }

        waitForIdle()
        assertEquals(BlockId("b1"), clickedBlockId)
    }

    @Test
    fun `execution canvas click on empty space does not call onBlockClick`() = runComposeUiTest {
        var clickedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                ExecutionDagCanvas(
                    graph = graph,
                    blockExecutions = emptyList(),
                    onBlockClick = { clickedBlockId = it },
                    modifier = Modifier.size(800.dp, 600.dp),
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(500f, 500f))
        }

        waitForIdle()
        assertNull(clickedBlockId)
    }

    @Test
    fun `execution canvas drag does not call onBlockClick`() = runComposeUiTest {
        var clickedBlockId: BlockId? = null
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
        )

        setContent {
            MaterialTheme {
                ExecutionDagCanvas(
                    graph = graph,
                    blockExecutions = emptyList(),
                    onBlockClick = { clickedBlockId = it },
                    modifier = Modifier.size(800.dp, 600.dp),
                )
            }
        }

        // Drag starting on the block — should pan, not click
        onNodeWithTag("execution_dag_canvas").performTouchInput {
            down(Offset(190f, 135f))
            moveTo(Offset(250f, 200f))
            up()
        }

        waitForIdle()
        assertNull(clickedBlockId, "Dragging should not trigger block click")
    }
}
