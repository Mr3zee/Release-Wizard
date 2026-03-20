package com.github.mr3zee.dag

import com.github.mr3zee.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagValidatorTest {

    private fun actionBlock(id: String, name: String = id) = Block.ActionBlock(
        id = BlockId(id),
        name = name,
        type = BlockType.TEAMCITY_BUILD,
    )

    private fun edge(from: String, to: String) = Edge(BlockId(from), BlockId(to))

    @Test
    fun `empty graph is valid`() {
        val errors = DagValidator.validate(DagGraph())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `linear graph is valid`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `diamond graph is valid`() {
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("a"), actionBlock("b"), actionBlock("c"), actionBlock("d"),
            ),
            edges = listOf(
                edge("a", "b"), edge("a", "c"), edge("b", "d"), edge("c", "d"),
            ),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `detects self loop`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is ValidationError.SelfLoop)
    }

    @Test
    fun `detects cycle`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
        val cycle = errors.filterIsInstance<ValidationError.CycleDetected>().first()
        assertEquals(setOf(BlockId("a"), BlockId("b"), BlockId("c")), cycle.involvedBlockIds)
    }

    @Test
    fun `detects duplicate block id`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.DuplicateBlockId })
    }

    @Test
    fun `detects invalid edge reference`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "missing")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.InvalidEdgeReference })
    }

    @Test
    fun `validates container children`() {
        val childGraph = DagGraph(
            blocks = listOf(actionBlock("c1"), actionBlock("c2")),
            edges = listOf(edge("c1", "c2"), edge("c2", "c1")), // cycle in children
        )
        val container = Block.ContainerBlock(
            id = BlockId("container"),
            name = "Group",
            children = childGraph,
        )
        val graph = DagGraph(blocks = listOf(container))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
    }

    @Test
    fun `deeply nested containers are valid`() {
        val innerChild = DagGraph(
            blocks = listOf(actionBlock("inner-a"), actionBlock("inner-b")),
            edges = listOf(edge("inner-a", "inner-b")),
        )
        val innerContainer = Block.ContainerBlock(
            id = BlockId("inner-container"),
            name = "Inner",
            children = innerChild,
        )
        val outerChild = DagGraph(
            blocks = listOf(innerContainer, actionBlock("outer-sibling")),
            edges = emptyList(),
        )
        val outerContainer = Block.ContainerBlock(
            id = BlockId("outer-container"),
            name = "Outer",
            children = outerChild,
        )
        val graph = DagGraph(blocks = listOf(outerContainer, actionBlock("root")))
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `cycle in deeply nested container detected`() {
        val innerChild = DagGraph(
            blocks = listOf(actionBlock("deep-a"), actionBlock("deep-b")),
            edges = listOf(edge("deep-a", "deep-b"), edge("deep-b", "deep-a")),
        )
        val innerContainer = Block.ContainerBlock(
            id = BlockId("inner-container"),
            name = "Inner",
            children = innerChild,
        )
        val outerChild = DagGraph(
            blocks = listOf(innerContainer),
        )
        val outerContainer = Block.ContainerBlock(
            id = BlockId("outer-container"),
            name = "Outer",
            children = outerChild,
        )
        val graph = DagGraph(blocks = listOf(outerContainer))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
    }

    @Test
    fun `cross-level edge references detected as invalid`() {
        val childGraph = DagGraph(
            blocks = listOf(actionBlock("child-a")),
        )
        val container = Block.ContainerBlock(
            id = BlockId("container"),
            name = "Group",
            children = childGraph,
        )
        // Edge from top-level block to child-level block (cross-level reference)
        val graph = DagGraph(
            blocks = listOf(actionBlock("top"), container),
            edges = listOf(edge("top", "child-a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.InvalidEdgeReference })
    }

    @Test
    fun `disconnected blocks are valid`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = emptyList(),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `detects too many blocks`() {
        val blocks = (1..DagValidator.MAX_BLOCKS + 1).map { actionBlock("b$it") }
        val graph = DagGraph(blocks = blocks)
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.TooManyBlocks })
        val err = errors.filterIsInstance<ValidationError.TooManyBlocks>().first()
        assertEquals(DagValidator.MAX_BLOCKS + 1, err.count)
        assertEquals(DagValidator.MAX_BLOCKS, err.max)
    }

    @Test
    fun `detects too many edges`() {
        val blocks = listOf(actionBlock("a"), actionBlock("b"))
        val edges = (1..DagValidator.MAX_EDGES + 1).map { edge("a", "b") }
        val graph = DagGraph(blocks = blocks, edges = edges)
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.TooManyEdges })
    }

    @Test
    fun `detects nesting too deep`() {
        fun buildNested(depth: Int): Block.ContainerBlock {
            if (depth == 0) {
                return Block.ContainerBlock(
                    id = BlockId("container-$depth"),
                    name = "c$depth",
                    children = DagGraph(blocks = listOf(actionBlock("leaf"))),
                )
            }
            return Block.ContainerBlock(
                id = BlockId("container-$depth"),
                name = "c$depth",
                children = DagGraph(blocks = listOf(buildNested(depth - 1))),
            )
        }

        val deepContainer = buildNested(DagValidator.MAX_NESTING_DEPTH + 1)
        val graph = DagGraph(blocks = listOf(deepContainer))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.NestingTooDeep })
    }

    @Test
    fun `detects block name too long`() {
        val longName = "x".repeat(DagValidator.MAX_BLOCK_NAME_LENGTH + 1)
        val graph = DagGraph(blocks = listOf(actionBlock("a", longName)))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.BlockNameTooLong })
        val err = errors.filterIsInstance<ValidationError.BlockNameTooLong>().first()
        assertEquals(BlockId("a"), err.blockId)
    }

    @Test
    fun `detects too many parameters`() {
        val params = (1..DagValidator.MAX_PARAMETERS_PER_BLOCK + 1).map {
            Parameter("key$it", "val$it")
        }
        val block = Block.ActionBlock(
            id = BlockId("a"),
            name = "a",
            type = BlockType.TEAMCITY_BUILD,
            parameters = params,
        )
        val graph = DagGraph(blocks = listOf(block))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.TooManyParameters })
    }

    @Test
    fun `detects parameter key too long`() {
        val longKey = "k".repeat(DagValidator.MAX_PARAM_KEY_LENGTH + 1)
        val block = Block.ActionBlock(
            id = BlockId("a"),
            name = "a",
            type = BlockType.TEAMCITY_BUILD,
            parameters = listOf(Parameter(longKey, "value")),
        )
        val graph = DagGraph(blocks = listOf(block))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.ParameterKeyTooLong })
    }

    @Test
    fun `detects parameter value too long`() {
        val longValue = "v".repeat(DagValidator.MAX_PARAM_VALUE_LENGTH + 1)
        val block = Block.ActionBlock(
            id = BlockId("a"),
            name = "a",
            type = BlockType.TEAMCITY_BUILD,
            parameters = listOf(Parameter("key", longValue)),
        )
        val graph = DagGraph(blocks = listOf(block))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.ParameterValueTooLong })
    }

    @Test
    fun `collects multiple errors without short-circuiting`() {
        val longName = "x".repeat(DagValidator.MAX_BLOCK_NAME_LENGTH + 1)
        val graph = DagGraph(
            blocks = listOf(actionBlock("a", longName), actionBlock("a", longName)),
            edges = listOf(edge("a", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.BlockNameTooLong })
        assertTrue(errors.any { it is ValidationError.DuplicateBlockId })
        assertTrue(errors.any { it is ValidationError.SelfLoop })
    }

    @Test
    fun `nesting at exact max depth is valid`() {
        fun buildNested(depth: Int): Block.ContainerBlock {
            if (depth == 0) {
                return Block.ContainerBlock(
                    id = BlockId("container-$depth"),
                    name = "c$depth",
                    children = DagGraph(blocks = listOf(actionBlock("leaf"))),
                )
            }
            return Block.ContainerBlock(
                id = BlockId("container-$depth"),
                name = "c$depth",
                children = DagGraph(blocks = listOf(buildNested(depth - 1))),
            )
        }

        val container = buildNested(DagValidator.MAX_NESTING_DEPTH - 1)
        val graph = DagGraph(blocks = listOf(container))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.none { it is ValidationError.NestingTooDeep })
    }

    @Test
    fun `detects block description too long`() {
        val longDescription = "x".repeat(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH + 1)
        val block = Block.ActionBlock(
            id = BlockId("a"),
            name = "a",
            description = longDescription,
            type = BlockType.TEAMCITY_BUILD,
        )
        val graph = DagGraph(blocks = listOf(block))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.BlockDescriptionTooLong })
        val err = errors.filterIsInstance<ValidationError.BlockDescriptionTooLong>().first()
        assertEquals(BlockId("a"), err.blockId)
        assertEquals(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH + 1, err.length)
        assertEquals(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH, err.max)
    }

    @Test
    fun `block description at max length is valid`() {
        val description = "x".repeat(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH)
        val block = Block.ActionBlock(
            id = BlockId("a"),
            name = "a",
            description = description,
            type = BlockType.TEAMCITY_BUILD,
        )
        val graph = DagGraph(blocks = listOf(block))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.none { it is ValidationError.BlockDescriptionTooLong })
    }

    @Test
    fun `detects container block description too long`() {
        val longDescription = "x".repeat(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH + 1)
        val container = Block.ContainerBlock(
            id = BlockId("c1"),
            name = "Group",
            description = longDescription,
            children = DagGraph(),
        )
        val graph = DagGraph(blocks = listOf(container))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.BlockDescriptionTooLong })
        val err = errors.filterIsInstance<ValidationError.BlockDescriptionTooLong>().first()
        assertEquals(BlockId("c1"), err.blockId)
        assertEquals(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH + 1, err.length)
        assertEquals(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH, err.max)
    }

    @Test
    fun `detects block description too long in nested container`() {
        val longDescription = "x".repeat(DagValidator.MAX_BLOCK_DESCRIPTION_LENGTH + 1)
        val nestedBlock = Block.ActionBlock(
            id = BlockId("nested"),
            name = "nested",
            description = longDescription,
            type = BlockType.TEAMCITY_BUILD,
        )
        val container = Block.ContainerBlock(
            id = BlockId("c1"),
            name = "Group",
            children = DagGraph(blocks = listOf(nestedBlock)),
        )
        val graph = DagGraph(blocks = listOf(container))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.BlockDescriptionTooLong })
        val err = errors.filterIsInstance<ValidationError.BlockDescriptionTooLong>().first()
        assertEquals(BlockId("nested"), err.blockId)
    }
}
