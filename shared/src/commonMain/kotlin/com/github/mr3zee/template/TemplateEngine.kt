package com.github.mr3zee.template

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.Parameter

/**
 * Resolves template expressions in parameter values.
 * Supported syntax:
 * - ${param.key} — project/release-level parameter
 * - ${block.blockId.outputName} — output from a specific block
 */
object TemplateEngine {

    const val MAX_RESOLUTION_DEPTH = 10

    private val TEMPLATE_PATTERN = Regex("""\$\{([^}]+)\}""")
    private val INVALID_KEY_CHARS = charArrayOf('$', '{', '}')

    fun validateParameterKey(key: String): Boolean {
        // Empty keys are allowed — they represent unfilled placeholders in the editor.
        // They are stripped before execution.
        return key.isEmpty() || INVALID_KEY_CHARS.none { it in key }
    }

    fun resolve(
        value: String,
        parameters: List<Parameter>,
        blockOutputs: Map<BlockId, Map<String, String>> = emptyMap(),
        currentDepth: Int = 0,
    ): String {
        if (currentDepth >= MAX_RESOLUTION_DEPTH) return value
        val resolved = TEMPLATE_PATTERN.replace(value) { match ->
            val expr = match.groupValues[1]
            resolveExpression(expr, parameters, blockOutputs) ?: match.value
        }
        if (resolved == value || !TEMPLATE_PATTERN.containsMatchIn(resolved)) return resolved
        return resolve(resolved, parameters, blockOutputs, currentDepth + 1)
    }

    fun resolveParameters(
        parameters: List<Parameter>,
        projectParameters: List<Parameter>,
        blockOutputs: Map<BlockId, Map<String, String>> = emptyMap(),
    ): List<Parameter> {
        return parameters.map { param ->
            param.copy(value = resolve(param.value, projectParameters, blockOutputs))
        }
    }

    private fun resolveExpression(
        expr: String,
        parameters: List<Parameter>,
        blockOutputs: Map<BlockId, Map<String, String>>,
    ): String? {
        // param.key — project-level parameter
        if (expr.startsWith("param.")) {
            val key = expr.removePrefix("param.")
            return parameters.find { it.key == key }?.value
        }

        // block.blockId.outputName — block output
        if (expr.startsWith("block.")) {
            val parts = expr.removePrefix("block.").split(".", limit = 2)
            if (parts.size == 2) {
                val blockId = BlockId(parts[0])
                val outputName = parts[1]
                return blockOutputs[blockId]?.get(outputName)
            }
        }

        return null
    }
}
