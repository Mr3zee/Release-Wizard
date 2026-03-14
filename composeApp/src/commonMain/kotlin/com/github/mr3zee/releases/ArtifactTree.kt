package com.github.mr3zee.releases

/**
 * Represents a node in the artifact file tree.
 */
data class ArtifactNode(
    val name: String,
    val children: List<ArtifactNode>,
    val isDirectory: Boolean,
)

/**
 * Builds a hierarchical tree from flat relative paths (e.g., "lib/foo.jar", "docs/readme.txt").
 * Directories are sorted before files at each level.
 */
fun buildArtifactTree(paths: List<String>): List<ArtifactNode> {
    if (paths.isEmpty()) return emptyList()

    // Group by first path segment
    data class Entry(val rest: List<String>, val isFile: Boolean)

    val grouped = mutableMapOf<String, MutableList<Entry>>()

    for (path in paths) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) continue
        val first = parts.first()
        val rest = parts.drop(1)
        grouped.getOrPut(first) { mutableListOf() }
            .add(Entry(rest, rest.isEmpty()))
    }

    return grouped.map { (name, entries) ->
        val hasChildren = entries.any { !it.isFile }
        if (hasChildren || entries.any { it.rest.isNotEmpty() }) {
            // This is a directory — recurse into child paths
            val childPaths = entries.filter { it.rest.isNotEmpty() }.map { it.rest.joinToString("/") }
            ArtifactNode(
                name = name,
                children = buildArtifactTree(childPaths),
                isDirectory = true,
            )
        } else {
            // Leaf file
            ArtifactNode(name = name, children = emptyList(), isDirectory = false)
        }
    }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
}
