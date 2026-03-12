package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType {
    TEAMCITY_BUILD,
    GITHUB_ACTION,
    GITHUB_PUBLICATION,
    MAVEN_CENTRAL_PUBLICATION,
    SLACK_MESSAGE,
    USER_ACTION,
}
