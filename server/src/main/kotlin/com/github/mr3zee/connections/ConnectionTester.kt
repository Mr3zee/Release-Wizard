package com.github.mr3zee.connections

import com.github.mr3zee.api.ConnectionTestResult
import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Tests connections by making lightweight API calls to verify credentials and reachability.
 */
// todo claude: move to tests
class ConnectionTester(
    private val httpClient: HttpClient,
) {
    suspend fun test(config: ConnectionConfig): ConnectionTestResult = when (config) {
        is ConnectionConfig.SlackConfig -> testSlack(config)
        is ConnectionConfig.TeamCityConfig -> testTeamCity(config)
        is ConnectionConfig.GitHubConfig -> testGitHub(config)
        is ConnectionConfig.MavenCentralConfig -> testMavenCentral(config)
    }

    private fun testSlack(config: ConnectionConfig.SlackConfig): ConnectionTestResult {
        val url = config.webhookUrl
        return if (url.startsWith("https://hooks.slack.com/")) {
            ConnectionTestResult(success = true, message = "Webhook URL format is valid")
        } else {
            ConnectionTestResult(success = false, message = "Invalid Slack webhook URL: must start with https://hooks.slack.com/")
        }
    }

    private suspend fun testTeamCity(config: ConnectionConfig.TeamCityConfig): ConnectionTestResult {
        return try {
            val response = httpClient.get("${config.serverUrl}/app/rest/server") {
                header("Authorization", "Bearer ${config.token}")
                header("Accept", "application/json")
            }
            if (response.status.isSuccess()) {
                ConnectionTestResult(success = true, message = "Connected to TeamCity server")
            } else {
                ConnectionTestResult(success = false, message = "TeamCity returned ${response.status}")
            }
        } catch (e: Exception) {
            ConnectionTestResult(success = false, message = "Failed to connect: ${e.message}")
        }
    }

    private suspend fun testGitHub(config: ConnectionConfig.GitHubConfig): ConnectionTestResult {
        return try {
            val response = httpClient.get("https://api.github.com/repos/${config.owner}/${config.repo}") {
                header("Authorization", "Bearer ${config.token}")
                header("Accept", "application/vnd.github+json")
            }
            if (response.status.isSuccess()) {
                ConnectionTestResult(success = true, message = "Connected to GitHub repository ${config.owner}/${config.repo}")
            } else {
                ConnectionTestResult(success = false, message = "GitHub returned ${response.status}")
            }
        } catch (e: Exception) {
            ConnectionTestResult(success = false, message = "Failed to connect: ${e.message}")
        }
    }

    private suspend fun testMavenCentral(config: ConnectionConfig.MavenCentralConfig): ConnectionTestResult {
        return try {
            val response = httpClient.get("${config.baseUrl}/api/v1/publisher/status") {
                basicAuth(config.username, config.password)
            }
            if (response.status.isSuccess()) {
                ConnectionTestResult(success = true, message = "Connected to Maven Central")
            } else {
                ConnectionTestResult(success = false, message = "Maven Central returned ${response.status}")
            }
        } catch (e: Exception) {
            ConnectionTestResult(success = false, message = "Failed to connect: ${e.message}")
        }
    }
}
