package com.github.mr3zee.teamcity

import com.github.mr3zee.TestPropertiesLoader

/**
 * Configuration for TeamCity integration tests.
 * Loaded from local.properties or environment variables.
 */
data class TeamCityTestConfig(
    val serverUrl: String,
    val token: String,
    val buildTypeId: String,
    /** ID of a completed build known to have artifacts. Used by artifact listing tests. */
    val artifactBuildId: String? = null,
) {
    companion object {
        fun loadOrNull(): TeamCityTestConfig? {
            val props = TestPropertiesLoader.loadProperties()
            if (props != null) {
                val serverUrl = props.getProperty("teamcity.test.serverUrl")
                val token = props.getProperty("teamcity.test.token")
                val buildTypeId = props.getProperty("teamcity.test.buildTypeId")
                val artifactBuildId = props.getProperty("teamcity.test.artifactBuildId")
                if (!serverUrl.isNullOrBlank() && !token.isNullOrBlank() && !buildTypeId.isNullOrBlank()) {
                    return TeamCityTestConfig(
                        serverUrl = serverUrl.trimEnd('/'),
                        token = token,
                        buildTypeId = buildTypeId,
                        artifactBuildId = artifactBuildId?.takeIf { it.isNotBlank() },
                    )
                }
            }

            val serverUrl = System.getenv("TEAMCITY_TEST_SERVER_URL")
            val token = System.getenv("TEAMCITY_TEST_TOKEN")
            val buildTypeId = System.getenv("TEAMCITY_TEST_BUILD_TYPE_ID")
            val artifactBuildId = System.getenv("TEAMCITY_TEST_ARTIFACT_BUILD_ID")
            if (serverUrl.isNullOrBlank() || token.isNullOrBlank() || buildTypeId.isNullOrBlank()) {
                return null
            }

            return TeamCityTestConfig(
                serverUrl = serverUrl.trimEnd('/'),
                token = token,
                buildTypeId = buildTypeId,
                artifactBuildId = artifactBuildId?.takeIf { it.isNotBlank() },
            )
        }
    }
}
