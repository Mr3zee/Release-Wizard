package com.github.mr3zee.template

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.Parameter

/**
 * Resolves template expressions in parameter values.
 * Supported syntax:
 * - ${param.key} — project/release-level parameter
 * - ${block.blockId.inputs.key} — input parameter of a specific block
 * - ${block.blockId.outputs.key} — output produced by a specific block during execution
 * - ${block.blockId.key} — backward compat, resolves against outputs then inputs
 *
 * At runtime, the block outputs map stores entries under namespaced keys:
 * - "inputs.<key>" for block input parameters
 * - "outputs.<key>" for block execution outputs
 * - "<key>" as a plain alias for outputs (backward compat)
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
        blockId: BlockId? = null,
    ): List<Parameter> {
        // Pass 1: resolve against project params + predecessor outputs/inputs
        var resolved = parameters.map { param ->
            param.copy(value = resolve(param.value, projectParameters, blockOutputs))
        }

        if (blockId == null) return resolved

        // Iterative self-block resolution: add own params as inputs, re-resolve until stable.
        // Handles chains like A→B→C where all are on the same block.
        repeat(MAX_RESOLUTION_DEPTH) {
            val ownInputs = resolved
                .filter { it.key.isNotBlank() }
                .associate { "inputs.${it.key}" to it.value }
            val enrichedOutputs = blockOutputs + (blockId to (blockOutputs[blockId].orEmpty() + ownInputs))
            val next = resolved.map { param ->
                param.copy(value = resolve(param.value, projectParameters, enrichedOutputs))
            }
            if (next == resolved) return next
            resolved = next
        }
        return resolved
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

        // block.blockId[.inputs|outputs].key
        if (expr.startsWith("block.")) {
            val parts = expr.removePrefix("block.").split(".", limit = 2)
            if (parts.size != 2) return null
            val blockId = BlockId(parts[0])
            val rest = parts[1]
            val blockData = blockOutputs[blockId] ?: return null

            // Explicit namespace: block.<id>.inputs.<key> or block.<id>.outputs.<key>
            if (rest.startsWith("inputs.")) {
                return blockData["inputs.${rest.removePrefix("inputs.")}"]
            }
            if (rest.startsWith("outputs.")) {
                val key = rest.removePrefix("outputs.")
                return blockData["outputs.$key"] ?: blockData[key]
            }

            // Backward compat: block.<id>.<key> — try plain key (outputs alias)
            return blockData[rest]
        }

        return null
    }
}
