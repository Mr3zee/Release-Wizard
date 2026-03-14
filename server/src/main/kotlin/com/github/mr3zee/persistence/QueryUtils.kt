package com.github.mr3zee.persistence

/**
 * Escapes special characters in a string intended for use inside a SQL LIKE pattern.
 * The characters `\`, `%`, and `_` are escaped with a backslash prefix so they are
 * treated as literals rather than wildcards.
 */
fun escapeLikePattern(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * Coerces a potentially negative offset to zero, preventing errors in SQL OFFSET clauses.
 */
fun safeOffset(offset: Int): Long = offset.coerceAtLeast(0).toLong()
