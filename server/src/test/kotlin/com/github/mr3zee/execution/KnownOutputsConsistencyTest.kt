package com.github.mr3zee.execution

import com.github.mr3zee.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ensures that every output key constant referenced by server executors
 * is declared in [BlockType.knownOutputs]. If a new output is added to an
 * executor without updating [knownOutputs], this test fails — preventing
 * the UI from silently missing new outputs.
 */
class KnownOutputsConsistencyTest {

    @Test
    fun `TeamCity output constants match knownOutputs`() {
        val known = BlockType.TEAMCITY_BUILD.knownOutputs().map { it.name }.toSet()
        val constants = setOf(
            TeamCityOutputs.BUILD_ID,
            TeamCityOutputs.BUILD_NUMBER,
            TeamCityOutputs.BUILD_URL,
            TeamCityOutputs.BUILD_STATUS,
        )
        assertTrue(
            constants.all { it in known },
            "TeamCity output constants not in knownOutputs: ${constants - known}",
        )
        assertTrue(
            known.all { it in constants },
            "knownOutputs has entries without matching constants: ${known - constants}",
        )
    }

    @Test
    fun `GitHub Action output constants match knownOutputs`() {
        val known = BlockType.GITHUB_ACTION.knownOutputs().map { it.name }.toSet()
        val constants = setOf(
            GitHubActionOutputs.RUN_ID,
            GitHubActionOutputs.RUN_URL,
            GitHubActionOutputs.RUN_STATUS,
        )
        assertTrue(
            constants.all { it in known },
            "GitHub Action output constants not in knownOutputs: ${constants - known}",
        )
        assertTrue(
            known.all { it in constants },
            "knownOutputs has entries without matching constants: ${known - constants}",
        )
    }

    @Test
    fun `GitHub Publication output constants match knownOutputs`() {
        val known = BlockType.GITHUB_PUBLICATION.knownOutputs().map { it.name }.toSet()
        val constants = setOf(
            GitHubPublicationOutputs.RELEASE_URL,
            GitHubPublicationOutputs.TAG_NAME,
            GitHubPublicationOutputs.RELEASE_ID,
        )
        assertTrue(
            constants.all { it in known },
            "GitHub Publication output constants not in knownOutputs: ${constants - known}",
        )
        assertTrue(
            known.all { it in constants },
            "knownOutputs has entries without matching constants: ${known - constants}",
        )
    }

    @Test
    fun `Slack output constants match knownOutputs`() {
        val known = BlockType.SLACK_MESSAGE.knownOutputs().map { it.name }.toSet()
        val constants = setOf(
            SlackOutputs.MESSAGE_TS,
        )
        assertTrue(
            constants.all { it in known },
            "Slack output constants not in knownOutputs: ${constants - known}",
        )
        assertTrue(
            known.all { it in constants },
            "knownOutputs has entries without matching constants: ${known - constants}",
        )
    }

    @Test
    fun `every BlockType has knownOutputs defined`() {
        for (type in BlockType.entries) {
            val outputs = type.knownOutputs()
            assertTrue(
                outputs.isNotEmpty(),
                "BlockType.$type has no knownOutputs — add entries to BlockType.knownOutputs()",
            )
            // Verify no duplicate names
            val names = outputs.map { it.name }
            assertTrue(
                names.size == names.toSet().size,
                "BlockType.$type has duplicate output names: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}",
            )
            // Verify no blank names or descriptions
            for (output in outputs) {
                assertTrue(output.name.isNotBlank(), "BlockType.$type has output with blank name")
                assertTrue(output.description.isNotBlank(), "BlockType.$type output '${output.name}' has blank description")
            }
        }
    }
}
