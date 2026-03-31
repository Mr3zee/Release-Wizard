package com.github.mr3zee.editor

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.configIdParameterKey
import com.github.mr3zee.model.knownOutputs

data class TemplateSuggestion(
    val label: String,
    val insertText: String,
    val description: String?,
    val category: SuggestionCategory,
)

enum class SuggestionCategory { PARAMETER, BLOCK_INPUT, BLOCK_OUTPUT }

data class InterpolationContext(
    val triggerOffset: Int,
    val prefix: String,
)

/**
 * Builds autocomplete suggestions for template expressions.
 *
 * @param parameters Project-level parameters → `${param.key}`
 * @param predecessors Predecessor blocks → inputs as `${block.<id>.<key>}`, outputs as `${block.<id>.<output>}`
 * @param selfBlock The block being edited (optional) → own inputs only (outputs don't exist yet)
 * @param excludeParamKeys Keys to exclude from selfBlock input suggestions (e.g., "text" for Slack message)
 */
fun buildSuggestions(
    parameters: List<Parameter>,
    predecessors: List<Block>,
    selfBlock: Block.ActionBlock? = null,
    excludeParamKeys: Set<String> = emptySet(),
    defaultValueFormat: (String) -> String = { "Default: $it" },
): List<TemplateSuggestion> {
    val suggestions = mutableListOf<TemplateSuggestion>()

    // Project parameters
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

    // Self block — own input params only (outputs don't exist until execution completes)
    if (selfBlock != null) {
        addBlockInputSuggestions(suggestions, selfBlock, excludeParamKeys)
    }

    // Predecessor blocks — both inputs and outputs
    val actionPredecessors = predecessors.filterIsInstance<Block.ActionBlock>()
    for (block in actionPredecessors) {
        addBlockInputSuggestions(suggestions, block)

        val known = block.type.knownOutputs()
        val knownNames = known.map { it.name }.toSet()
        val custom = block.outputs.filter { it.name !in knownNames }
        val allOutputs = known + custom
        for (output in allOutputs) {
            if (output.name.isBlank()) continue
            suggestions.add(
                TemplateSuggestion(
                    label = "${block.name} / ${output.name}",
                    insertText = $$"${block.$${block.id.value}.outputs.$${output.name}}",
                    description = output.description.takeIf { it.isNotEmpty() },
                    category = SuggestionCategory.BLOCK_OUTPUT,
                )
            )
        }
    }

    return suggestions.onEach {
        require(it.insertText.isNotEmpty()) { "Suggestion '${it.label}' has empty insertText" }
    }
}

private fun addBlockInputSuggestions(
    suggestions: MutableList<TemplateSuggestion>,
    block: Block.ActionBlock,
    excludeKeys: Set<String> = emptySet(),
) {
    val configKey = block.type.configIdParameterKey()
    for (param in block.parameters) {
        if (param.key.isBlank()) continue
        if (param.key in excludeKeys) continue
        if (param.key == configKey) continue
        suggestions.add(
            TemplateSuggestion(
                label = "${block.name} / ${param.key}",
                insertText = $$"${block.$${block.id.value}.inputs.$${param.key}}",
                description = param.value.takeIf { it.isNotEmpty() },
                category = SuggestionCategory.BLOCK_INPUT,
            )
        )
    }
}

/**
 * Inserts an expression into a value, replacing any unclosed partial `${...` at the end.
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
                (keySuffix.isEmpty() || s.insertText.removeSurrounding($$"${", "}").removePrefix("param.").startsWith(keySuffix))
        }
    }

    if (prefix.startsWith("block.")) {
        val blockPrefix = prefix.removePrefix("block.")
        return allSuggestions.filter { s ->
            if (s.category != SuggestionCategory.BLOCK_INPUT && s.category != SuggestionCategory.BLOCK_OUTPUT) return@filter false
            val path = s.insertText.removeSurrounding($$"${", "}").removePrefix("block.")
            blockPrefix.isEmpty() || path.startsWith(blockPrefix)
        }
    }

    // General prefix — match against inner expression path
    return allSuggestions.filter { s ->
        s.insertText.removeSurrounding($$"${", "}").startsWith(prefix)
    }
}
