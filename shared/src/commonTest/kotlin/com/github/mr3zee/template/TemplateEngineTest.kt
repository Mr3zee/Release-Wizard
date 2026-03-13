package com.github.mr3zee.template

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.Parameter
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateEngineTest {

    @Test
    fun resolveProjectParameter() {
        val result = TemplateEngine.resolve(
            "Release version \${param.version}",
            listOf(Parameter("version", "1.0.0")),
        )
        assertEquals("Release version 1.0.0", result)
    }

    @Test
    fun resolveBlockOutput() {
        val result = TemplateEngine.resolve(
            "Build #\${block.build1.buildNumber}",
            emptyList(),
            mapOf(BlockId("build1") to mapOf("buildNumber" to "42")),
        )
        assertEquals("Build #42", result)
    }

    @Test
    fun unresolvedExpressionKeptAsIs() {
        val result = TemplateEngine.resolve(
            "Unknown \${param.missing}",
            emptyList(),
        )
        assertEquals("Unknown \${param.missing}", result)
    }

    @Test
    fun multipleExpressions() {
        val result = TemplateEngine.resolve(
            "\${param.name} v\${param.version}",
            listOf(Parameter("name", "MyLib"), Parameter("version", "2.0")),
        )
        assertEquals("MyLib v2.0", result)
    }

    @Test
    fun resolveParametersList() {
        val blockParams = listOf(
            Parameter("message", "Release \${param.version} is ready"),
            Parameter("channel", "#releases"),
        )
        val projectParams = listOf(Parameter("version", "3.0.0"))

        val resolved = TemplateEngine.resolveParameters(blockParams, projectParams)
        assertEquals("Release 3.0.0 is ready", resolved[0].value)
        assertEquals("#releases", resolved[1].value)
    }

    @Test
    fun noTemplateExpressionsUnchanged() {
        val result = TemplateEngine.resolve("plain text", emptyList())
        assertEquals("plain text", result)
    }

    @Test
    fun blockOutputWithMultipleParts() {
        val result = TemplateEngine.resolve(
            "URL: \${block.gh-action.runUrl}",
            emptyList(),
            mapOf(BlockId("gh-action") to mapOf("runUrl" to "https://github.com/run/123")),
        )
        assertEquals("URL: https://github.com/run/123", result)
    }
}
