package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionType {
    SLACK,
    TEAMCITY,
    GITHUB,
    MAVEN_CENTRAL,
}
