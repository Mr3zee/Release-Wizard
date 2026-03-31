package com.github.mr3zee.model

private val SlugSanitizeRegex = Regex("[^a-z0-9]+")

/**
 * Converts a human-readable name to a URL-safe slug suitable for use as a [BlockId].
 * Example: "Deploy to Prod" → "deploy-to-prod"
 */
fun slugify(name: String): String {
    return name
        .trim()
        .lowercase()
        .replace(SlugSanitizeRegex, "-")
        .trim('-')
        .ifEmpty { "block" }
}

/**
 * Generates a unique [BlockId] by slugifying the given [name] and appending a numeric
 * suffix if the slug already exists among [existingIds].
 */
fun generateBlockId(name: String, existingIds: Set<BlockId>): BlockId {
    val base = slugify(name)
    var candidate = BlockId(base)
    var counter = 2
    while (candidate in existingIds) {
        candidate = BlockId("$base-$counter")
        counter++
    }
    return candidate
}

/** Regex for valid block ID format: lowercase alphanumeric with hyphens, no leading/trailing hyphens. */
val BlockIdFormatRegex = Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$")

/** Maximum length for a block ID. */
const val MAX_BLOCK_ID_LENGTH = 100
