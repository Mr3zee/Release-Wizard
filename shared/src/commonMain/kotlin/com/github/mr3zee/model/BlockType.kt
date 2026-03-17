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

/** The parameter key that holds the external config ID for this block type, or null if not applicable. */
fun BlockType.configIdParameterKey(): String? = when (this) {
    BlockType.TEAMCITY_BUILD -> "buildTypeId"
    BlockType.GITHUB_ACTION -> "workflowFile"
    BlockType.GITHUB_PUBLICATION, BlockType.SLACK_MESSAGE -> null
}
