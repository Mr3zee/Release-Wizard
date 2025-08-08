package io.github.mr3zee.rwizard.integrations

import io.github.mr3zee.rwizard.api.TeamCityBuildConfig
import io.github.mr3zee.rwizard.domain.model.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * TeamCity API integration client for Release Wizard
 * Provides functionality for triggering builds and monitoring build status
 */
class TeamCityClient(
    private val credentials: Credentials.TeamCityCredentials,
    private val serverUrl: String
) {
    private val logger = LoggerFactory.getLogger(TeamCityClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    companion object {
        const val RATE_LIMIT_DELAY = 1000L // 1 second between requests
    }

    private val authHeader: String by lazy {
        val auth = "${credentials.username}:${credentials.password}"
        "Basic ${Base64.getEncoder().encodeToString(auth.toByteArray())}"
    }

    /**
     * Test the TeamCity connection by fetching server information
     */
    suspend fun testConnection(): Result<TeamCityServerInfo> = try {
        val response = client.get("${serverUrl}/app/rest/server") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
            }
        }
        
        val serverInfo = response.body<TeamCityServerInfo>()
        logger.info("TeamCity connection test successful: ${serverInfo.version}")
        Result.success(serverInfo)
    } catch (e: Exception) {
        logger.error("Failed to test TeamCity connection", e)
        Result.failure(e)
    }

    /**
     * Fetch build configurations (build types) from TeamCity
     */
    suspend fun getBuildConfigurations(projectId: String? = null): Result<List<TeamCityBuildConfig>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val url = if (projectId != null) {
            "${serverUrl}/app/rest/buildTypes?locator=project:$projectId"
        } else {
            "${serverUrl}/app/rest/buildTypes"
        }
        
        val response = client.get(url) {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
            }
        }
        
        val result = response.body<TeamCityBuildTypesResult>()
        val buildConfigurations: List<TeamCityBuildConfig> = result.buildType?.map { buildType ->
            TeamCityBuildConfig(
                id = buildType.id,
                name = buildType.name,
                projectId = buildType.projectId ?: "",
                href = buildType.href
            )
        } ?: emptyList()
        
        logger.info("Retrieved ${buildConfigurations.size} TeamCity build configurations")
        Result.success(buildConfigurations)
    } catch (e: Exception) {
        logger.error("Failed to fetch TeamCity build configurations", e)
        Result.failure(e)
    }

    /**
     * Get projects from TeamCity
     */
    suspend fun getProjects(): Result<List<TeamCityProject>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("${serverUrl}/app/rest/projects") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
            }
        }
        
        val result = response.body<TeamCityProjectsResult>()
        val projects = result.project ?: emptyList()
        
        logger.info("Retrieved ${projects.size} TeamCity projects")
        Result.success(projects)
    } catch (e: Exception) {
        logger.error("Failed to fetch TeamCity projects", e)
        Result.failure(e)
    }

    /**
     * Trigger a build in TeamCity
     */
    suspend fun triggerBuild(
        buildTypeId: String,
        branchName: String? = null,
        properties: Map<String, String> = emptyMap(),
        comment: String? = null
    ): Result<TeamCityBuild> = try {
        delay(RATE_LIMIT_DELAY)
        
        val buildRequest = TeamCityTriggerBuildRequest(
            buildType = TeamCityBuildTypeRef(id = buildTypeId),
            branchName = branchName,
            properties = if (properties.isNotEmpty()) {
                TeamCityProperties(
                    property = properties.map { (name, value) ->
                        TeamCityProperty(name = name, value = value)
                    }
                )
            } else null,
            comment = if (comment != null) TeamCityComment(text = comment) else null
        )
        
        val response = client.post("${serverUrl}/app/rest/buildQueue") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
                append("Content-Type", ContentType.Application.Json.toString())
            }
            setBody(buildRequest)
        }
        
        val build = response.body<TeamCityBuild>()
        logger.info("Triggered TeamCity build: ${build.id} for build type: $buildTypeId")
        Result.success(build)
    } catch (e: Exception) {
        logger.error("Failed to trigger TeamCity build for $buildTypeId", e)
        Result.failure(e)
    }

    /**
     * Get build status by build ID
     */
    suspend fun getBuildStatus(buildId: String): Result<TeamCityBuild> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("${serverUrl}/app/rest/builds/id:$buildId") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
            }
        }
        
        val build = response.body<TeamCityBuild>()
        logger.info("Retrieved build status: ${build.id} - ${build.status}")
        Result.success(build)
    } catch (e: Exception) {
        logger.error("Failed to get build status for $buildId", e)
        Result.failure(e)
    }

    /**
     * Get recent builds for a build configuration
     */
    suspend fun getRecentBuilds(buildTypeId: String, count: Int = 10): Result<List<TeamCityBuild>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("${serverUrl}/app/rest/builds") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
            }
            url {
                parameters.append("locator", "buildType:$buildTypeId,count:$count")
            }
        }
        
        val result = response.body<TeamCityBuildsResult>()
        val builds = result.build ?: emptyList()
        
        logger.info("Retrieved ${builds.size} recent builds for $buildTypeId")
        Result.success(builds)
    } catch (e: Exception) {
        logger.error("Failed to get recent builds for $buildTypeId", e)
        Result.failure(e)
    }

    /**
     * Cancel a running build
     */
    suspend fun cancelBuild(buildId: String, comment: String? = null): Result<Unit> = try {
        delay(RATE_LIMIT_DELAY)
        
        val cancelRequest = TeamCityCancelBuildRequest(
            buildCancelRequest = TeamCityBuildCancelRequest(
                comment = comment ?: "Cancelled by Release Wizard",
                readdIntoQueue = false
            )
        )
        
        client.post("${serverUrl}/app/rest/builds/id:$buildId") {
            headers {
                append("Authorization", authHeader)
                append("Accept", ContentType.Application.Json.toString())
                append("Content-Type", ContentType.Application.Json.toString())
            }
            setBody(cancelRequest)
        }
        
        logger.info("Cancelled TeamCity build: $buildId")
        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to cancel build $buildId", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}

// TeamCity API Data Models
@Serializable
data class TeamCityServerInfo(
    val version: String,
    val buildNumber: String,
    val buildDate: String,
    val internalId: String,
    val webUrl: String
)

@Serializable
data class TeamCityProjectsResult(
    val count: Int,
    val project: List<TeamCityProject>? = null
)

@Serializable
data class TeamCityProject(
    val id: String,
    val name: String,
    val description: String? = null,
    val href: String,
    val webUrl: String,
    val archived: Boolean = false
)

@Serializable
data class TeamCityBuildTypesResult(
    val count: Int,
    val buildType: List<TeamCityBuildType>? = null
)

@Serializable
data class TeamCityBuildType(
    val id: String,
    val name: String,
    val description: String? = null,
    val projectName: String? = null,
    val projectId: String? = null,
    val href: String,
    val webUrl: String,
    val paused: Boolean = false
)

@Serializable
data class TeamCityTriggerBuildRequest(
    val buildType: TeamCityBuildTypeRef,
    val branchName: String? = null,
    val properties: TeamCityProperties? = null,
    val comment: TeamCityComment? = null
)

@Serializable
data class TeamCityBuildTypeRef(
    val id: String
)

@Serializable
data class TeamCityProperties(
    val property: List<TeamCityProperty>
)

@Serializable
data class TeamCityProperty(
    val name: String,
    val value: String
)

@Serializable
data class TeamCityComment(
    val text: String
)

@Serializable
data class TeamCityBuildsResult(
    val count: Int,
    val build: List<TeamCityBuild>? = null
)

@Serializable
data class TeamCityBuild(
    val id: String,
    val buildTypeId: String,
    val number: String? = null,
    val status: String? = null, // SUCCESS, FAILURE, ERROR, etc.
    val state: String? = null,  // queued, running, finished
    val branchName: String? = null,
    val href: String? = null,
    val webUrl: String? = null,
    val statusText: String? = null,
    val queuedDate: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val buildType: TeamCityBuildType? = null,
    val lastChanges: TeamCityLastChanges? = null
)

@Serializable
data class TeamCityLastChanges(
    val count: Int,
    val change: List<TeamCityChange>? = null
)

@Serializable
data class TeamCityChange(
    val id: String,
    val version: String,
    val username: String? = null,
    val date: String,
    val href: String,
    val webUrl: String,
    val comment: String? = null
)

@Serializable
data class TeamCityCancelBuildRequest(
    val buildCancelRequest: TeamCityBuildCancelRequest
)

@Serializable
data class TeamCityBuildCancelRequest(
    val comment: String,
    val readdIntoQueue: Boolean = false
)
