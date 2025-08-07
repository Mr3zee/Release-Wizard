package io.github.mr3zee.rwizard.integrations

import io.github.mr3zee.rwizard.domain.model.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Minimal Maven Central Publisher API client
 * Supports verifying deployment status.
 */
class MavenCentralClient(
    private val credentials: Credentials.MavenCentralPortalCredentials
) {
    private val logger = LoggerFactory.getLogger(MavenCentralClient::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    companion object {
        const val PUBLISHER_BASE_URL = "https://central.sonatype.com/api/v1/publisher"
    }

    private val authHeader: String by lazy {
        val auth = "${credentials.username}:${credentials.password}"
        "Basic ${Base64.getEncoder().encodeToString(auth.toByteArray())}"
    }

    /**
     * Verify status of a deployment created in the Central Publisher Portal.
     * Reference: https://central.sonatype.org/publish/publish-portal-api/#verify-status-of-the-deployment
     */
    suspend fun verifyDeploymentStatus(deploymentId: String): Result<DeploymentStatusResponse> = try {
        val response = client.get("$PUBLISHER_BASE_URL/deployments/$deploymentId/status") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
            }
        }
        val body = response.body<DeploymentStatusResponse>()
        logger.info("Deployment $deploymentId status: ${body.status}")
        Result.success(body)
    } catch (e: Exception) {
        logger.error("Failed to verify deployment status for $deploymentId", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}

@Serializable
data class DeploymentStatusResponse(
    val deploymentId: String? = null,
    val status: String,
    val statusUrl: String? = null,
    val message: String? = null
)
