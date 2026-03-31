package com.github.mr3zee

import com.github.mr3zee.editor.*
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.BlockOutput
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.Parameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TemplateSuggestionsTest {

    // ---- parseInterpolationContext ----

    @Test
    fun `returns null for empty text`() {
        assertNull(parseInterpolationContext("", 0))
    }

    @Test
    fun `returns null for cursor at position 0`() {
        assertNull(parseInterpolationContext("hello", 0))
    }

    @Test
    fun `returns null for cursor beyond text length`() {
        assertNull(parseInterpolationContext("hello", 10))
    }

    @Test
    fun `detects trigger immediately after dollar-brace`() {
        val ctx = parseInterpolationContext($$"${", 2)
        assertNotNull(ctx)
        assertEquals(0, ctx.triggerOffset)
        assertEquals("", ctx.prefix)
    }

    @Test
    fun `detects trigger with partial prefix`() {
        val ctx = parseInterpolationContext($$"${par", 5)
        assertNotNull(ctx)
        assertEquals(0, ctx.triggerOffset)
        assertEquals("par", ctx.prefix)
    }

    @Test
    fun `detects trigger with full param prefix`() {
        val ctx = parseInterpolationContext($$"${param.ver", 11)
        assertNotNull(ctx)
        assertEquals(0, ctx.triggerOffset)
        assertEquals("param.ver", ctx.prefix)
    }

    @Test
    fun `returns null for already closed expression`() {
        assertNull(parseInterpolationContext($$"${param.version}", 16))
    }

    @Test
    fun `returns null when cursor is inside a closed expression`() {
        // Cursor after "param" but the expression is closed with }
        assertNull(parseInterpolationContext($$"${param.version}", 7))
    }

    @Test
    fun `detects trigger with text before dollar-brace`() {
        val ctx = parseInterpolationContext($$"hello ${p", 9)
        assertNotNull(ctx)
        assertEquals(6, ctx.triggerOffset)
        assertEquals("p", ctx.prefix)
    }

    @Test
    fun `returns null for bare dollar without brace`() {
        assertNull(parseInterpolationContext($$"hello $p", 8))
    }

    @Test
    fun `handles nested expressions - outer closed inner open`() {
        // "prefix ${closed} ${open" — cursor in the second open expression
        val text = $$"prefix ${closed} ${"
        val ctx = parseInterpolationContext(text, text.length)
        assertNotNull(ctx)
        assertEquals(17, ctx.triggerOffset)
        assertEquals("", ctx.prefix)
    }

    @Test
    fun `returns null when all expressions are closed`() {
        assertNull(parseInterpolationContext($$"${a} ${b}", 9))
    }

    @Test
    fun `detects trigger at beginning of text`() {
        val ctx = parseInterpolationContext($$"${block.b1", 10)
        assertNotNull(ctx)
        assertEquals(0, ctx.triggerOffset)
        assertEquals("block.b1", ctx.prefix)
    }

    // ---- buildSuggestions ----

    @Test
    fun `builds parameter suggestions`() {
        val params = listOf(
            Parameter(key = "version", value = "1.0"),
            Parameter(key = "env", value = ""),
        )
        val suggestions = buildSuggestions(params, emptyList())

        assertEquals(2, suggestions.size)
        assertEquals("version", suggestions[0].label)
        assertEquals($$"${param.version}", suggestions[0].insertText)
        assertEquals("Default: 1.0", suggestions[0].description)
        assertEquals(SuggestionCategory.PARAMETER, suggestions[0].category)

        assertEquals("env", suggestions[1].label)
        assertNull(suggestions[1].description)
    }

    @Test
    fun `builds block output suggestions`() {
        val blocks = listOf(
            Block.ActionBlock(
                id = BlockId("build1"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
                outputs = listOf(BlockOutput("buildNumber"), BlockOutput("status")),
            ),
        )
        val suggestions = buildSuggestions(emptyList(), blocks)

        // knownOutputs() for TEAMCITY_BUILD: buildId, buildNumber, buildUrl, buildStatus (4)
        // custom "buildNumber" is deduplicated, custom "status" is kept = 4 + 1 = 5
        assertEquals(5, suggestions.size)
        // Known outputs come first
        assertEquals("Build / buildId", suggestions[0].label)
        assertEquals(SuggestionCategory.BLOCK_OUTPUT, suggestions[0].category)
        assertEquals("Build / buildNumber", suggestions[1].label)
        assertEquals($$"${block.build1.outputs.buildNumber}", suggestions[1].insertText)
        assertEquals("Build / buildUrl", suggestions[2].label)
        assertEquals("Build / buildStatus", suggestions[3].label)
        // Custom output "status" (not in known) comes last
        assertEquals("Build / status", suggestions[4].label)
        assertEquals($$"${block.build1.outputs.status}", suggestions[4].insertText)
    }

    @Test
    fun `blocks with no custom outputs still have known outputs`() {
        val blocks = listOf(
            Block.ActionBlock(
                id = BlockId("b1"),
                name = "Empty",
                type = BlockType.SLACK_MESSAGE,
                outputs = emptyList(),
            ),
        )
        // knownOutputs() for SLACK_MESSAGE: messageTs (1)
        val suggestions = buildSuggestions(emptyList(), blocks)
        assertEquals(1, suggestions.size)
        assertEquals("Empty / messageTs", suggestions[0].label)
    }

    @Test
    fun `skips container blocks`() {
        val blocks = listOf(
            Block.ContainerBlock(id = BlockId("c1"), name = "Container"),
        )
        assertEquals(0, buildSuggestions(emptyList(), blocks).size)
    }

    // ---- filterSuggestions ----

    private val testSuggestions = buildSuggestions(
        parameters = listOf(
            Parameter(key = "version", value = "1.0"),
            Parameter(key = "env", value = "prod"),
        ),
        predecessors = listOf(
            Block.ActionBlock(
                id = BlockId("build1"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
                outputs = listOf(BlockOutput("buildNumber"), BlockOutput("status")),
            ),
        ),
    )

    @Test
    fun `empty prefix returns all suggestions`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(testSuggestions.size, result.size)
    }

    @Test
    fun `param prefix filters to parameters only`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "param.")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(2, result.size)
        result.forEach { assertEquals(SuggestionCategory.PARAMETER, it.category) }
    }

    @Test
    fun `param prefix with key suffix filters by key`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "param.ver")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(1, result.size)
        assertEquals("version", result[0].label)
    }

    @Test
    fun `param prefix with non-matching suffix returns empty`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "param.xyz")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(0, result.size)
    }

    @Test
    fun `block prefix filters to outputs only`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "block.")
        val result = filterSuggestions(testSuggestions, ctx)
        // 4 known TEAMCITY_BUILD outputs + 1 custom "status" = 5
        assertEquals(5, result.size)
        result.forEach { assertEquals(SuggestionCategory.BLOCK_OUTPUT, it.category) }
    }

    @Test
    fun `block prefix with ID filters to specific block`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "block.build1.")
        val result = filterSuggestions(testSuggestions, ctx)
        // 4 known TEAMCITY_BUILD outputs + 1 custom "status" = 5
        assertEquals(5, result.size)
    }

    @Test
    fun `block prefix with ID and output suffix filters further`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "block.build1.outputs.build")
        val result = filterSuggestions(testSuggestions, ctx)
        // Matches known outputs starting with "build": buildId, buildNumber, buildUrl, buildStatus
        assertEquals(4, result.size)
        assertEquals("Build / buildId", result[0].label)
        assertEquals("Build / buildNumber", result[1].label)
        assertEquals("Build / buildUrl", result[2].label)
        assertEquals("Build / buildStatus", result[3].label)
    }

    @Test
    fun `general prefix matches across categories`() {
        // "p" matches "param.version" and "param.env" (both start with "p")
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "p")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(2, result.size)
        result.forEach { assertEquals(SuggestionCategory.PARAMETER, it.category) }
    }

    @Test
    fun `general prefix b matches block outputs`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "b")
        val result = filterSuggestions(testSuggestions, ctx)
        // 4 known TEAMCITY_BUILD outputs + 1 custom "status" = 5
        assertEquals(5, result.size)
        result.forEach { assertEquals(SuggestionCategory.BLOCK_OUTPUT, it.category) }
    }

    @Test
    fun `general prefix with no match returns empty`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "zzz")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(0, result.size)
    }

    @Test
    fun `filter is case sensitive`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "param.Version")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(0, result.size)
    }

    @Test
    fun `cursor mid-expression returns correct prefix`() {
        // Cursor between "par" and "am.version" in an open expression
        val ctx = parseInterpolationContext($$"${param.version", 5)
        assertNotNull(ctx)
        assertEquals(0, ctx.triggerOffset)
        assertEquals("par", ctx.prefix)
    }

    @Test
    fun `blank parameter key is skipped`() {
        val params = listOf(
            Parameter(key = "", value = "x"),
            Parameter(key = "version", value = "1.0"),
        )
        val suggestions = buildSuggestions(params, emptyList())
        assertEquals(1, suggestions.size)
        assertEquals("version", suggestions[0].label)
    }

    @Test
    fun `blank output is skipped`() {
        val blocks = listOf(
            Block.ActionBlock(
                id = BlockId("b1"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
                outputs = listOf(BlockOutput(""), BlockOutput("buildNumber")),
            ),
        )
        val suggestions = buildSuggestions(emptyList(), blocks)
        // knownOutputs() for TEAMCITY_BUILD: buildId, buildNumber, buildUrl, buildStatus (4)
        // Custom "" is blank (skipped), custom "buildNumber" is deduplicated = 4 total
        assertEquals(4, suggestions.size)
        assertEquals("Build / buildId", suggestions[0].label)
        assertEquals("Build / buildNumber", suggestions[1].label)
        assertEquals("Build / buildUrl", suggestions[2].label)
        assertEquals("Build / buildStatus", suggestions[3].label)
    }

    @Test
    fun `parameter description takes priority over default value`() {
        val params = listOf(
            Parameter(key = "version", value = "1.0", description = "The release version"),
        )
        val suggestions = buildSuggestions(params, emptyList())
        assertEquals(1, suggestions.size)
        assertEquals("The release version", suggestions[0].description)
    }

    @Test
    fun `parameter without description falls back to default value`() {
        val params = listOf(
            Parameter(key = "version", value = "1.0", description = ""),
        )
        val suggestions = buildSuggestions(params, emptyList())
        assertEquals("Default: 1.0", suggestions[0].description)
    }

    @Test
    fun `unknown block ID prefix returns empty`() {
        val ctx = InterpolationContext(triggerOffset = 0, prefix = "block.nonexistent.")
        val result = filterSuggestions(testSuggestions, ctx)
        assertEquals(0, result.size)
    }

    // ---- insertExpressionSafely ----

    @Test
    fun `insertExpressionSafely appends when no partial expression`() {
        val result = insertExpressionSafely("hello ", $$"${param.version}")
        assertEquals($$"hello ${param.version}", result)
    }

    @Test
    fun `insertExpressionSafely replaces partial expression`() {
        val result = insertExpressionSafely($$"hello ${pa", $$"${param.version}")
        assertEquals($$"hello ${param.version}", result)
    }

    @Test
    fun `insertExpressionSafely with empty value`() {
        val result = insertExpressionSafely("", $$"${param.version}")
        assertEquals($$"${param.version}", result)
    }

    @Test
    fun `insertExpressionSafely with closed expression appends`() {
        val result = insertExpressionSafely($$"${param.version}", $$"${param.env}")
        assertEquals($$"${param.version}${param.env}", result)
    }
}
