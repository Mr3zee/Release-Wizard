package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType {
    TEAMCITY_BUILD,
    GITHUB_ACTION,
    GITHUB_PUBLICATION,
    SLACK_MESSAGE,
}

fun BlockType.requiredConnectionType(): ConnectionType? = when (this) {
    BlockType.TEAMCITY_BUILD -> ConnectionType.TEAMCITY
    BlockType.GITHUB_ACTION -> ConnectionType.GITHUB
    BlockType.GITHUB_PUBLICATION -> ConnectionType.GITHUB
    BlockType.SLACK_MESSAGE -> ConnectionType.SLACK
}

/** Returns true for block types that require a timeout to be set. */
fun BlockType.requiresTimeout(): Boolean = when (this) {
    BlockType.TEAMCITY_BUILD, BlockType.GITHUB_ACTION -> true
    BlockType.GITHUB_PUBLICATION, BlockType.SLACK_MESSAGE -> false
}

/**
 * Known runtime outputs produced by each block type.
 * Output name constants are defined in companion-like objects below so server executors
 * can reference the same names, ensuring the UI and server stay in sync.
 */
fun BlockType.knownOutputs(): List<BlockOutput> = when (this) {
    BlockType.TEAMCITY_BUILD -> listOf(
        BlockOutput(TeamCityOutputs.BUILD_ID, "TeamCity build ID"),
        BlockOutput(TeamCityOutputs.BUILD_NUMBER, "Build number assigned by TeamCity"),
        BlockOutput(TeamCityOutputs.BUILD_URL, "URL to the build log in TeamCity"),
        BlockOutput(TeamCityOutputs.BUILD_STATUS, "Final build status (e.g., SUCCESS, FAILURE)"),
    )
    BlockType.GITHUB_ACTION -> listOf(
        BlockOutput(GitHubActionOutputs.RUN_ID, "GitHub Actions workflow run ID"),
        BlockOutput(GitHubActionOutputs.RUN_URL, "URL to the workflow run on GitHub"),
        BlockOutput(GitHubActionOutputs.RUN_STATUS, "Workflow conclusion (e.g., success, failure)"),
    )
    BlockType.GITHUB_PUBLICATION -> listOf(
        BlockOutput(GitHubPublicationOutputs.RELEASE_URL, "URL to the created GitHub release"),
        BlockOutput(GitHubPublicationOutputs.TAG_NAME, "Git tag name of the release"),
        BlockOutput(GitHubPublicationOutputs.RELEASE_ID, "GitHub release ID"),
    )
    BlockType.SLACK_MESSAGE -> listOf(
        BlockOutput(SlackOutputs.MESSAGE_TS, "Slack message timestamp identifier"),
        BlockOutput(SlackOutputs.CHANNEL, "Slack channel the message was posted to"),
    )
}

object TeamCityOutputs {
    const val BUILD_ID = "buildId"
    const val BUILD_NUMBER = "buildNumber"
    const val BUILD_URL = "buildUrl"
    const val BUILD_STATUS = "buildStatus"
}

object GitHubActionOutputs {
    const val RUN_ID = "runId"
    const val RUN_URL = "runUrl"
    const val RUN_STATUS = "runStatus"
}

object GitHubPublicationOutputs {
    const val RELEASE_URL = "releaseUrl"
    const val TAG_NAME = "tagName"
    const val RELEASE_ID = "releaseId"
}

object SlackOutputs {
    const val MESSAGE_TS = "messageTs"
    const val CHANNEL = "channel"
}

/** The parameter key that holds the external config ID for this block type, or null if not applicable. */
fun BlockType.configIdParameterKey(): String? = when (this) {
    BlockType.TEAMCITY_BUILD -> "buildTypeId"
    BlockType.GITHUB_ACTION -> "workflowFile"
    BlockType.GITHUB_PUBLICATION, BlockType.SLACK_MESSAGE -> null
}
