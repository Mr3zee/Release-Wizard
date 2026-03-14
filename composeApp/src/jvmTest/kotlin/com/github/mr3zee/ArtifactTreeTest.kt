package com.github.mr3zee

import com.github.mr3zee.releases.buildArtifactTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactTreeTest {

    @Test
    fun `empty list produces empty tree`() {
        val tree = buildArtifactTree(emptyList())
        assertTrue(tree.isEmpty())
    }

    @Test
    fun `single file produces single leaf node`() {
        val tree = buildArtifactTree(listOf("app.jar"))
        assertEquals(1, tree.size)
        assertEquals("app.jar", tree[0].name)
        assertFalse(tree[0].isDirectory)
        assertTrue(tree[0].children.isEmpty())
    }

    @Test
    fun `flat list produces sorted leaf nodes`() {
        val tree = buildArtifactTree(listOf("b.jar", "a.jar", "c.txt"))
        assertEquals(3, tree.size)
        assertEquals("a.jar", tree[0].name)
        assertEquals("b.jar", tree[1].name)
        assertEquals("c.txt", tree[2].name)
        assertTrue(tree.all { !it.isDirectory })
    }

    @Test
    fun `nested structure creates directory hierarchy`() {
        val tree = buildArtifactTree(listOf("lib/app.jar", "lib/utils.jar"))
        assertEquals(1, tree.size)
        assertEquals("lib", tree[0].name)
        assertTrue(tree[0].isDirectory)
        assertEquals(2, tree[0].children.size)
        assertEquals("app.jar", tree[0].children[0].name)
        assertEquals("utils.jar", tree[0].children[1].name)
    }

    @Test
    fun `deep nesting builds correct tree`() {
        val tree = buildArtifactTree(listOf("a/b/c/file.txt"))
        assertEquals(1, tree.size)
        assertEquals("a", tree[0].name)
        assertTrue(tree[0].isDirectory)
        assertEquals("b", tree[0].children[0].name)
        assertTrue(tree[0].children[0].isDirectory)
        assertEquals("c", tree[0].children[0].children[0].name)
        assertTrue(tree[0].children[0].children[0].isDirectory)
        assertEquals("file.txt", tree[0].children[0].children[0].children[0].name)
        assertFalse(tree[0].children[0].children[0].children[0].isDirectory)
    }

    @Test
    fun `mixed depths with directories first`() {
        val tree = buildArtifactTree(listOf("readme.txt", "lib/app.jar", "docs/guide.md"))
        assertEquals(3, tree.size)
        // Directories come first, sorted by name
        assertEquals("docs", tree[0].name)
        assertTrue(tree[0].isDirectory)
        assertEquals("lib", tree[1].name)
        assertTrue(tree[1].isDirectory)
        // Files after directories
        assertEquals("readme.txt", tree[2].name)
        assertFalse(tree[2].isDirectory)
    }

    @Test
    fun `multiple files in multiple directories`() {
        val tree = buildArtifactTree(listOf(
            "src/main.kt",
            "src/util.kt",
            "test/main_test.kt",
            "build.gradle",
        ))
        assertEquals(3, tree.size)
        // Directories first
        assertEquals("src", tree[0].name)
        assertEquals(2, tree[0].children.size)
        assertEquals("test", tree[1].name)
        assertEquals(1, tree[1].children.size)
        // File last
        assertEquals("build.gradle", tree[2].name)
    }
}
