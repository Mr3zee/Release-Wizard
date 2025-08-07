package io.github.mr3zee.rwizard.integrations

import io.github.mr3zee.rwizard.api.MavenArtifact
import io.github.mr3zee.rwizard.domain.model.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Maven Central (Sonatype OSSRH) API integration client for Release Wizard
 * Provides functionality for artifact publishing and repository management
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
        const val OSSRH_BASE_URL = "https://oss.sonatype.org"
        const val CENTRAL_SEARCH_URL = "https://search.maven.org/solrsearch"
        const val RATE_LIMIT_DELAY = 1000L // 1 second between requests
    }

    private val authHeader: String by lazy {
        val auth = "${credentials.username}:${credentials.password}"
        "Basic ${Base64.getEncoder().encodeToString(auth.toByteArray())}"
    }

    /**
     * Test the Maven Central connection by checking profile repositories
     */
    suspend fun testConnection(): Result<List<MavenRepository>> = try {
        val response = client.get("$OSSRH_BASE_URL/service/local/staging/profile_repositories") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
            }
        }
        
        val result = response.body<MavenProfileRepositoriesResult>()
        logger.info("Maven Central connection test successful: ${result.data.size} repositories found")
        Result.success(result.data)
    } catch (e: Exception) {
        logger.error("Failed to test Maven Central connection", e)
        Result.failure(e)
    }

    /**
     * Search for artifacts in Maven Central
     */
    suspend fun searchArtifacts(
        groupId: String? = null,
        artifactId: String? = null,
        version: String? = null,
        query: String? = null,
        rows: Int = 20
    ): Result<List<MavenArtifact>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val searchQuery = buildString {
            if (query != null) {
                append("q=$query")
            } else {
                val parts = mutableListOf<String>()
                if (groupId != null) parts.add("g:$groupId")
                if (artifactId != null) parts.add("a:$artifactId")
                if (version != null) parts.add("v:$version")
                append("q=${parts.joinToString(" AND ")}")
            }
        }
        
        val response = client.get("$CENTRAL_SEARCH_URL/select") {
            url {
                parameters.append("q", searchQuery.substringAfter("q="))
                parameters.append("rows", rows.toString())
                parameters.append("wt", "json")
            }
        }
        
        val result = response.body<MavenSearchResult>()
        val artifacts = result.response.docs.map { doc ->
            MavenArtifact(
                groupId = doc.g,
                artifactId = doc.a,
                version = doc.v,
                packaging = doc.p ?: "jar",
                timestamp = doc.timestamp
            )
        }
        
        logger.info("Found ${artifacts.size} artifacts in Maven Central search")
        Result.success(artifacts)
    } catch (e: Exception) {
        logger.error("Failed to search Maven Central artifacts", e)
        Result.failure(e)
    }

    /**
     * Get staging repositories
     */
    suspend fun getStagingRepositories(): Result<List<MavenStagingRepository>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("$OSSRH_BASE_URL/service/local/staging/repository_profiles") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
            }
        }
        
        val result = response.body<MavenStagingRepositoriesResult>()
        logger.info("Retrieved ${result.data.size} staging repositories")
        Result.success(result.data)
    } catch (e: Exception) {
        logger.error("Failed to fetch staging repositories", e)
        Result.failure(e)
    }

    /**
     * Create a staging repository
     */
    suspend fun createStagingRepository(
        profileId: String,
        description: String? = null
    ): Result<MavenStagingRepository> = try {
        delay(RATE_LIMIT_DELAY)
        
        val request = MavenCreateStagingRequest(
            data = MavenStagingRequestData(
                description = description ?: "Created by Release Wizard"
            )
        )
        
        val response = client.post("$OSSRH_BASE_URL/service/local/staging/profiles/$profileId/start") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        
        val result = response.body<MavenStagingRepositoryResult>()
        logger.info("Created staging repository: ${result.data.repositoryId}")
        Result.success(result.data)
    } catch (e: Exception) {
        logger.error("Failed to create staging repository for profile $profileId", e)
        Result.failure(e)
    }

    /**
     * Close a staging repository (prepare for release)
     */
    suspend fun closeStagingRepository(
        repositoryId: String,
        description: String? = null
    ): Result<Unit> = try {
        delay(RATE_LIMIT_DELAY)
        
        val request = MavenStagingActionRequest(
            data = MavenStagingActionData(
                stagedRepositoryIds = listOf(repositoryId),
                description = description ?: "Closed by Release Wizard"
            )
        )
        
        client.post("$OSSRH_BASE_URL/service/local/staging/bulk/close") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        
        logger.info("Closed staging repository: $repositoryId")
        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to close staging repository $repositoryId", e)
        Result.failure(e)
    }

    /**
     * Release a staging repository to Maven Central
     */
    suspend fun releaseStagingRepository(
        repositoryId: String,
        description: String? = null
    ): Result<Unit> = try {
        delay(RATE_LIMIT_DELAY)
        
        val request = MavenStagingActionRequest(
            data = MavenStagingActionData(
                stagedRepositoryIds = listOf(repositoryId),
                description = description ?: "Released by Release Wizard"
            )
        )
        
        client.post("$OSSRH_BASE_URL/service/local/staging/bulk/promote") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        
        logger.info("Released staging repository: $repositoryId")
        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to release staging repository $repositoryId", e)
        Result.failure(e)
    }

    /**
     * Drop a staging repository (discard)
     */
    suspend fun dropStagingRepository(
        repositoryId: String,
        description: String? = null
    ): Result<Unit> = try {
        delay(RATE_LIMIT_DELAY)
        
        val request = MavenStagingActionRequest(
            data = MavenStagingActionData(
                stagedRepositoryIds = listOf(repositoryId),
                description = description ?: "Dropped by Release Wizard"
            )
        )
        
        client.post("$OSSRH_BASE_URL/service/local/staging/bulk/drop") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        
        logger.info("Dropped staging repository: $repositoryId")
        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to drop staging repository $repositoryId", e)
        Result.failure(e)
    }

    /**
     * Get repository status and activity
     */
    suspend fun getRepositoryActivity(repositoryId: String): Result<List<MavenActivity>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("$OSSRH_BASE_URL/service/local/staging/repository/$repositoryId/activity") {
            headers {
                append("Authorization", authHeader)
                append("Accept", "application/json")
            }
        }
        
        val result = response.body<MavenActivityResult>()
        logger.info("Retrieved ${result.data.size} activities for repository $repositoryId")
        Result.success(result.data)
    } catch (e: Exception) {
        logger.error("Failed to get repository activity for $repositoryId", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}

// Maven Central API Data Models
@Serializable
data class MavenProfileRepositoriesResult(
    val data: List<MavenRepository>
)

@Serializable
data class MavenRepository(
    val repositoryId: String,
    val profileId: String,
    val profileName: String,
    val type: String,
    val policy: String,
    val userId: String? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val repositoryURI: String? = null,
    val created: String? = null,
    val createdDate: String? = null,
    val createdTimestamp: Long? = null,
    val updated: String? = null,
    val updatedDate: String? = null,
    val updatedTimestamp: Long? = null,
    val description: String? = null,
    val provider: String? = null,
    val releaseRepositoryId: String? = null,
    val transitioning: Boolean = false
)

@Serializable
data class MavenSearchResult(
    val responseHeader: MavenSearchHeader,
    val response: MavenSearchResponse
)

@Serializable
data class MavenSearchHeader(
    val status: Int,
    val QTime: Int
)

@Serializable
data class MavenSearchResponse(
    val numFound: Int,
    val start: Int,
    val docs: List<MavenSearchDoc>
)

@Serializable
data class MavenSearchDoc(
    val g: String, // groupId
    val a: String, // artifactId  
    val v: String, // version
    val p: String? = null, // packaging
    val timestamp: Long,
    val ec: List<String>? = null, // extension and classifier
    val tags: List<String>? = null
)

@Serializable
data class MavenStagingRepositoriesResult(
    val data: List<MavenStagingRepository>
)

@Serializable
data class MavenStagingRepository(
    val profileId: String,
    val profileName: String,
    val profileType: String,
    val repositoryId: String,
    val type: String,
    val policy: String,
    val userId: String?,
    val userAgent: String?,
    val ipAddress: String?,
    val repositoryURI: String,
    val created: String,
    val createdDate: String,
    val createdTimestamp: Long,
    val updated: String,
    val updatedDate: String,
    val updatedTimestamp: Long,
    val description: String,
    val provider: String,
    val releaseRepositoryId: String?,
    val transitioning: Boolean
)

@Serializable
data class MavenCreateStagingRequest(
    val data: MavenStagingRequestData
)

@Serializable
data class MavenStagingRequestData(
    val description: String
)

@Serializable
data class MavenStagingRepositoryResult(
    val data: MavenStagingRepository
)

@Serializable
data class MavenStagingActionRequest(
    val data: MavenStagingActionData
)

@Serializable
data class MavenStagingActionData(
    val stagedRepositoryIds: List<String>,
    val description: String? = null
)

@Serializable
data class MavenActivityResult(
    val data: List<MavenActivity>
)

@Serializable
data class MavenActivity(
    val name: String,
    val started: String,
    val stopped: String? = null,
    val events: List<MavenActivityEvent> = emptyList()
)

@Serializable
data class MavenActivityEvent(
    val timestamp: String,
    val name: String,
    val severity: Int,
    val properties: List<MavenActivityProperty> = emptyList()
)

@Serializable
data class MavenActivityProperty(
    val name: String,
    val value: String
)
