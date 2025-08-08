package io.github.mr3zee.rwizard.integrations

import io.github.mr3zee.rwizard.api.GitHubRepository
import io.github.mr3zee.rwizard.domain.model.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import sun.jvm.hotspot.HelloWorld.e

/**
 * GitHub API integration client for Release Wizard
 * Provides functionality for repository management and release operations
 */
class GitHubClient(
    private val credentials: Credentials.GitHubCredentials
) {
    private val logger = LoggerFactory.getLogger(GitHubClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    companion object {
        const val BASE_URL = "https://api.github.com"
        const val RATE_LIMIT_DELAY = 1000L // 1 second between requests
    }

    /**
     * Test the GitHub connection by fetching authenticated user info
     */
    suspend fun testConnection(): Result<GitHubUser> = try {
        val response = client.get("$BASE_URL/user") {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
        }
        
        val user = response.body<GitHubUser>()
        logger.info("GitHub connection test successful for user: ${user.login}")
        Result.success(user)
    } catch (e: Exception) {
        logger.error("Failed to test GitHub connection", e)
        Result.failure(e)
    }

    /**
     * Fetch repositories accessible to the authenticated user
     */
    suspend fun getRepositories(
        visibility: GitHubRepoVisibility = GitHubRepoVisibility.ALL,
        affiliation: Set<GitHubRepoAffiliation> = setOf(GitHubRepoAffiliation.OWNER, GitHubRepoAffiliation.COLLABORATOR),
        sort: GitHubRepoSort = GitHubRepoSort.UPDATED
    ): Result<List<GitHubRepository>> = try {
        delay(RATE_LIMIT_DELAY) // Simple rate limiting

        val response = client.get("$BASE_URL/user/repos") {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
            url {
                parameters.append("visibility", visibility.api)
                parameters.append("affiliation", affiliation.joinToString(",") { it.api })
                parameters.append("sort", sort.api)
                parameters.append("per_page", "100")
            }
        }
        
        val repos = response.body<List<GitHubRepositoryInfo>>()
        val repositories = repos.map { repo ->
            GitHubRepository(
                name = repo.name,
                fullName = repo.full_name,
                isPrivate = repo.private,
                defaultBranch = repo.default_branch
            )
        }
        
        logger.info("Retrieved ${repositories.size} GitHub repositories")
        Result.success(repositories)
    } catch (e: Exception) {
        logger.error("Failed to fetch GitHub repositories", e)
        Result.failure(e)
    }

    /**
     * Get repository details by owner and name
     */
    suspend fun getRepository(owner: String, repo: String): Result<GitHubRepositoryInfo> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("$BASE_URL/repos/$owner/$repo") {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
        }
        
        val repository = response.body<GitHubRepositoryInfo>()
        logger.info("Retrieved repository details: ${repository.full_name}")
        Result.success(repository)
    } catch (e: Exception) {
        logger.error("Failed to fetch repository $owner/$repo", e)
        Result.failure(e)
    }

    /**
     * Get branches for a repository
     */
    suspend fun getBranches(owner: String, repo: String): Result<List<GitHubBranch>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("$BASE_URL/repos/$owner/$repo/branches") {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
            url {
                parameters.append("per_page", "100")
            }
        }
        
        val branches = response.body<List<GitHubBranch>>()
        logger.info("Retrieved ${branches.size} branches for $owner/$repo")
        Result.success(branches)
    } catch (e: Exception) {
        logger.error("Failed to fetch branches for $owner/$repo", e)
        Result.failure(e)
    }

    /**
     * Create a GitHub release
     */
    suspend fun createRelease(
        owner: String,
        repo: String,
        request: GitHubCreateReleaseRequest
    ): Result<GitHubRelease> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.post("$BASE_URL/repos/$owner/$repo/releases") {
            headers {
                append("Authorization", "token ${credentials.token"})
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
                append("Content-Type", MediaType.APPLICATION_JSON.value)
            }
            setBody(request)
        }
        
        val release = response.body<GitHubRelease>()
        logger.info("Created GitHub release: ${release.tag_name} for $owner/$repo")
        Result.success(release)
    } catch (e: Exception) {
        logger.error("Failed to create release for $owner/$repo", e)
        Result.failure(e)
    }

    /**
     * Get releases for a repository
     */
    suspend fun getReleases(owner: String, repo: String): Result<List<GitHubRelease>> = try {
        delay(RATE_LIMIT_DELAY)
        
        val response = client.get("$BASE_URL/repos/$owner/$repo/releases") {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Accept", MediaType.GITHUB_V3_JSON.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
            url {
                parameters.append("per_page", "50")
            }
        }
        
        val releases = response.body<List<GitHubRelease>>()
        logger.info("Retrieved ${releases.size} releases for $owner/$repo")
        Result.success(releases)
    } catch (e: Exception) {
        logger.error("Failed to fetch releases for $owner/$repo", e)
        Result.failure(e)
    }

    /**
     * Upload release asset
     */
    suspend fun uploadReleaseAsset(
        uploadUrl: String,
        fileName: String,
        fileContent: ByteArray,
        contentType: MediaType = MediaType.APPLICATION_OCTET_STREAM
    ): Result<GitHubReleaseAsset> = try {
        delay(RATE_LIMIT_DELAY)

        // GitHub upload URL needs to be modified to include the filename
        val finalUploadUrl = uploadUrl.replace("{?name,label}", "?name=$fileName")

        val response = client.post(finalUploadUrl) {
            headers {
                append("Authorization", "token ${credentials.token}")
                append("Content-Type", contentType.value)
                append("User-Agent", UserAgent.RELEASE_WIZARD.value)
            }
            setBody(fileContent)
        }
        
        val asset = response.body<GitHubReleaseAsset>()
        logger.info("Uploaded release asset: $fileName (${fileContent.size} bytes)")
        Result.success(asset)
    } catch (e: Exception) {
        logger.error("Failed to upload release asset: $fileName", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}

// GitHub API Data Models
@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    val avatar_url: String,
    val name: String?,
    val email: String?,
    val public_repos: Int,
    val followers: Int,
    val following: Int
)

@Serializable
data class GitHubRepositoryInfo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val html_url: String,
    val description: String?,
    val fork: Boolean,
    val created_at: String,
    val updated_at: String,
    val pushed_at: String?,
    val git_url: String,
    val ssh_url: String,
    val clone_url: String,
    val homepage: String?,
    val size: Int,
    val stargazers_count: Int,
    val watchers_count: Int,
    val language: String?,
    val has_issues: Boolean,
    val has_projects: Boolean,
    val has_wiki: Boolean,
    val has_pages: Boolean,
    val forks_count: Int,
    val open_issues_count: Int,
    val forks: Int,
    val open_issues: Int,
    val watchers: Int,
    val default_branch: String,
    val archived: Boolean = false,
    val disabled: Boolean = false
)

@Serializable
data class GitHubBranch(
    val name: String,
    val commit: GitHubCommit,
    val protected: Boolean = false
)

@Serializable
data class GitHubCommit(
    val sha: String,
    val url: String
)

@Serializable
data class GitHubCreateReleaseRequest(
    val tag_name: String,
    val target_commitish: String = "main",
    val name: String,
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val generate_release_notes: Boolean = false
)

@Serializable
data class GitHubRelease(
    val id: Long,
    val url: String,
    val html_url: String,
    val assets_url: String,
    val upload_url: String,
    val tarball_url: String,
    val zipball_url: String,
    val tag_name: String,
    val target_commitish: String,
    val name: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val created_at: String,
    val published_at: String?,
    val author: GitHubUser,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val url: String,
    val browser_download_url: String,
    val name: String,
    val label: String?,
    val state: String,
    val content_type: String,
    val size: Long,
    val download_count: Int,
    val created_at: String,
    val updated_at: String,
    val uploader: GitHubUser
)
