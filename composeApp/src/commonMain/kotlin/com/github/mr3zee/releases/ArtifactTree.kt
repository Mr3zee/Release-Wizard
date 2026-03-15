package com.github.mr3zee.releases

/**
 * Represents a node in the artifact file tree.
 */
data class ArtifactNode(
    val name: String,
    val children: List<ArtifactNode>,
    val isDirectory: Boolean,
    val size: Long? = null,
)

/**
 * Represents a single artifact entry with its path and optional file size.
 */
data class ArtifactEntry(val path: String, val size: Long? = null)

/**
 * Builds a hierarchical tree from flat relative paths (e.g., "lib/foo.jar", "docs/readme.txt").
 * Directories are sorted before files at each level.
 * Delegates to the [ArtifactEntry] overload for backward compatibility.
 */
fun buildArtifactTree(paths: List<String>): List<ArtifactNode> =
    buildArtifactTreeFromEntries(paths.map { ArtifactEntry(it) })

/**
 * Builds a hierarchical tree from [ArtifactEntry] items, propagating [ArtifactEntry.size]
 * to leaf nodes. Directories always have `size = null`.
 * Directories are sorted before files at each level.
 */
fun buildArtifactTreeFromEntries(entries: List<ArtifactEntry>): List<ArtifactNode> {
    if (entries.isEmpty()) return emptyList()

    // Internal structure to track remaining path segments and the original size
    data class Segment(val rest: List<String>, val size: Long?)

    val grouped = mutableMapOf<String, MutableList<Segment>>()

    for (entry in entries) {
        val parts = entry.path.split("/").filter { it.isNotEmpty() && it != "." && it != ".." }
        if (parts.isEmpty()) continue
        val first = parts.first()
        val rest = parts.drop(1)
        grouped.getOrPut(first) { mutableListOf() }
            .add(Segment(rest, entry.size))
    }

    return grouped.map { (name, segments) ->
        val hasChildren = segments.any { it.rest.isNotEmpty() }
        if (hasChildren) {
            // This is a directory — recurse into child entries
            val childEntries = segments.filter { it.rest.isNotEmpty() }.map {
                ArtifactEntry(it.rest.joinToString("/"), it.size)
            }
            ArtifactNode(
                name = name,
                children = buildArtifactTreeFromEntries(childEntries),
                isDirectory = true,
            )
        } else {
            // Leaf file — use the size from the first (and only) segment
            ArtifactNode(
                name = name,
                children = emptyList(),
                isDirectory = false,
                size = segments.firstOrNull()?.size,
            )
        }
    }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
}
