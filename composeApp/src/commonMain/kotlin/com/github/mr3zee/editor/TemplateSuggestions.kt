package com.github.mr3zee.editor

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter

data class TemplateSuggestion(
    val label: String,
    val insertText: String,
    val description: String?,
    val category: SuggestionCategory,
)

enum class SuggestionCategory { PARAMETER, BLOCK_OUTPUT }

data class InterpolationContext(
    val triggerOffset: Int,
    val prefix: String,
)

fun buildSuggestions(
    parameters: List<Parameter>,
    predecessors: List<Block>,
    defaultValueFormat: (String) -> String = { "Default: $it" },
): List<TemplateSuggestion> {
    val suggestions = mutableListOf<TemplateSuggestion>()

    for (param in parameters) {
        if (param.key.isBlank()) continue
        suggestions.add(
            TemplateSuggestion(
                label = if (param.label.isNotEmpty()) "${param.label} (${param.key})" else param.key,
                insertText = $$"${param.$${param.key}}",
                description = when {
                    param.description.isNotEmpty() -> param.description
                    param.value.isNotEmpty() -> defaultValueFormat(param.value)
                    else -> null
                },
                category = SuggestionCategory.PARAMETER,
            )
        )
    }

    val actionPredecessors = predecessors.filterIsInstance<Block.ActionBlock>()
        .filter { it.outputs.isNotEmpty() }
    for (block in actionPredecessors) {
        for (output in block.outputs) {
            if (output.isBlank()) continue
            suggestions.add(
                TemplateSuggestion(
                    label = "${block.name} / $output",
                    insertText = $$"${block.$${block.id.value}.$$output}",
                    description = null,
                    category = SuggestionCategory.BLOCK_OUTPUT,
                )
            )
        }
    }

    return suggestions.onEach {
        require(it.insertText.isNotEmpty()) { "Suggestion '${it.label}' has empty insertText" }
    }
}

/**
 * Inserts an expression into a value, replacing any unclosed partial `${...` at the end.
 * Used by the TemplatePickerDialog to avoid appending on top of a partial expression.
 */
fun insertExpressionSafely(currentValue: String, expression: String): String {
    val ctx = parseInterpolationContext(currentValue, currentValue.length)
    return if (ctx != null) {
        currentValue.substring(0, ctx.triggerOffset) + expression
    } else {
        currentValue + expression
    }
}

/** Max characters to scan backward from cursor — template expressions are short */
private const val MAX_SCAN_WINDOW = 512

fun parseInterpolationContext(text: String, cursorPos: Int): InterpolationContext? {
    if (text.isEmpty() || cursorPos <= 0 || cursorPos > text.length) return null

    // Scan backward from cursor for an unclosed ${.
    // Brace-depth tracking: encountering } increments depth (we're inside a closed region),
    // encountering ${ at depth 0 means we found the active trigger,
    // encountering ${ at depth > 0 decrements depth (matching a prior }).
    var depth = 0
    val scanStart = maxOf(0, cursorPos - MAX_SCAN_WINDOW)
    var i = cursorPos - 1
    while (i >= scanStart) {
        if (text[i] == '}') {
            depth++
            i--
        } else if (i > 0 && text[i - 1] == '$' && text[i] == '{') {
            if (depth == 0) {
                // Found our trigger — check it's not already closed
                val nextClose = text.indexOf('}', cursorPos)
                val nextOpen = text.indexOf($$"${", cursorPos)
                if (nextClose >= 0 && (nextOpen !in 0..nextClose)) {
                    return null
                }
                val triggerOffset = i - 1
                val prefix = text.substring(i + 1, cursorPos)
                return InterpolationContext(triggerOffset, prefix)
            }
            depth--
            i -= 2
        } else {
            i--
        }
    }
    return null
}

fun filterSuggestions(
    allSuggestions: List<TemplateSuggestion>,
    context: InterpolationContext,
): List<TemplateSuggestion> {
    val prefix = context.prefix
    if (prefix.isEmpty()) return allSuggestions

    if (prefix.startsWith("param.")) {
        val keySuffix = prefix.removePrefix("param.")
        return allSuggestions.filter { s ->
            s.category == SuggestionCategory.PARAMETER &&
                (keySuffix.isEmpty() || s.label.startsWith(keySuffix))
        }
    }

    if (prefix.startsWith("block.")) {
        val rest = prefix.removePrefix("block.")
        val dotIdx = rest.indexOf('.')
        if (dotIdx >= 0) {
            val blockId = rest.substring(0, dotIdx)
            val outputSuffix = rest.substring(dotIdx + 1)
            val blockPrefix = "block.$blockId."
            return allSuggestions.filter { s ->
                if (s.category != SuggestionCategory.BLOCK_OUTPUT) return@filter false
                val path = s.insertText.removeSurrounding($$"${", "}")
                path.startsWith(blockPrefix) &&
                    (outputSuffix.isEmpty() || path.substringAfterLast('.').startsWith(outputSuffix))
            }
        }
        return allSuggestions.filter { s ->
            s.category == SuggestionCategory.BLOCK_OUTPUT &&
                (rest.isEmpty() || s.insertText.removeSurrounding($$"${", "}").removePrefix("block.").startsWith(rest))
        }
    }

    // General prefix — match against inner expression path
    return allSuggestions.filter { s ->
        s.insertText.removeSurrounding($$"${", "}").startsWith(prefix)
    }
}
