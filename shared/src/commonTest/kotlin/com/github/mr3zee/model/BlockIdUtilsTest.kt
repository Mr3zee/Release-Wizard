package com.github.mr3zee.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockIdUtilsTest {

    @Test
    fun `slugify converts name to lowercase hyphenated slug`() {
        assertEquals("deploy-to-prod", slugify("Deploy to Prod"))
    }

    @Test
    fun `slugify trims whitespace`() {
        assertEquals("hello-world", slugify("  hello world  "))
    }

    @Test
    fun `slugify collapses consecutive non-alphanumeric chars`() {
        assertEquals("foo-bar-baz", slugify("foo   bar!!baz"))
    }

    @Test
    fun `slugify trims leading and trailing hyphens`() {
        assertEquals("deploy", slugify("--deploy--"))
    }

    @Test
    fun `slugify falls back to block for empty string`() {
        assertEquals("block", slugify(""))
    }

    @Test
    fun `slugify falls back to block for all-special-chars`() {
        assertEquals("block", slugify("!!!"))
    }

    @Test
    fun `slugify handles unicode by stripping non-ascii`() {
        val result = slugify("Déploy 🚀")
        // Non-ASCII chars are replaced by hyphens and collapsed
        assertEquals("d-ploy", result)
    }

    @Test
    fun `slugify preserves already valid slug`() {
        assertEquals("deploy-to-prod", slugify("deploy-to-prod"))
    }

    @Test
    fun `generateBlockId returns bare slug when no collision`() {
        assertEquals(BlockId("build"), generateBlockId("Build", emptySet()))
    }

    @Test
    fun `generateBlockId appends suffix on collision`() {
        val existing = setOf(BlockId("build"))
        assertEquals(BlockId("build-2"), generateBlockId("Build", existing))
    }

    @Test
    fun `generateBlockId increments through multiple collisions`() {
        val existing = setOf(BlockId("build"), BlockId("build-2"), BlockId("build-3"))
        assertEquals(BlockId("build-4"), generateBlockId("Build", existing))
    }

    @Test
    fun `generateBlockId handles empty name`() {
        val existing = setOf(BlockId("block"), BlockId("block-2"))
        assertEquals(BlockId("block-3"), generateBlockId("", existing))
    }

    @Test
    fun `generateBlockId returns base when only suffixed versions exist`() {
        val existing = setOf(BlockId("build-2"))
        assertEquals(BlockId("build"), generateBlockId("Build", existing))
    }
}
