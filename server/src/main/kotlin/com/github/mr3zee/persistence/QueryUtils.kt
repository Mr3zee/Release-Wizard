package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.LikePattern

/**
 * Creates a LIKE pattern that matches rows containing [search] as a substring (case-insensitive).
 * Special characters (`\`, `%`, `_`) are escaped with backslash, and the SQL ESCAPE clause
 * is included via [LikePattern] so the database interprets them as literals.
 *
 * Usage: `column.lowerCase() like likeContains(search)`
 */
fun likeContains(search: String): LikePattern {
    val escaped = search.lowercase()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return LikePattern("%$escaped%", escapeChar = '\\')
}

/**
 * Coerces a potentially negative offset to zero, preventing errors in SQL OFFSET clauses.
 */
fun safeOffset(offset: Int): Long = offset.coerceAtLeast(0).toLong()
