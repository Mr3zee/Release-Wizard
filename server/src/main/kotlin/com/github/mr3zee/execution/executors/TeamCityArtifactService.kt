package com.github.mr3zee.execution.executors

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import com.github.mr3zee.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Fetches artifact file names from TeamCity REST API and filters them by glob pattern.
 */
class TeamCityArtifactService(private val httpClient: HttpClient) {

    private val log = LoggerFactory.getLogger(TeamCityArtifactService::class.java)

    /**
     * Recursively fetches artifact names for a build, filters by glob, returns flat relative paths.
     */
    suspend fun fetchMatchingArtifacts(
        serverUrl: String,
        token: String,
        buildId: String,
        globPattern: String,
        maxDepth: Int,
        maxFiles: Int,
    ): List<String> {
        val fs = FileSystems.getDefault()
        val matcher = fs.getPathMatcher("glob:$globPattern")
        // Java's PathMatcher requires a '/' for **/ patterns to match root-level files.
        // E.g., "glob:**/*.jar" won't match "app.jar". Create a fallback matcher for this case.
        val rootMatcher = if (globPattern.startsWith("**/")) {
            fs.getPathMatcher("glob:${globPattern.removePrefix("**/")}")
        } else null
        val result = mutableListOf<String>()
        fetchRecursive(serverUrl, token, buildId, "", matcher, rootMatcher, maxDepth, maxFiles, result, 0)
        return result
    }

    private suspend fun fetchRecursive(
        serverUrl: String,
        token: String,
        buildId: String,
        subpath: String,
        matcher: PathMatcher,
        rootMatcher: PathMatcher?,
        maxDepth: Int,
        maxFiles: Int,
        result: MutableList<String>,
        currentDepth: Int,
    ) {
        if (currentDepth > maxDepth || result.size >= maxFiles) return

        val url = buildString {
            append("$serverUrl/app/rest/builds/id:$buildId/artifacts/children")
            if (subpath.isNotEmpty()) append("/$subpath")
        }

        val files = try {
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }
            if (!response.status.isSuccess()) {
                log.warn("Failed to fetch artifacts at '{}': HTTP {}", subpath, response.status.value)
                return
            }
            AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        } catch (e: CancellationException) {
            // EXEC-M2: Re-throw CancellationException so release cancellation propagates
            throw e
        } catch (e: Exception) {
            log.warn("Error fetching artifacts at '{}'", subpath, e)
            return
        }

        val fileArray = files["file"]?.jsonArray ?: return

        for (entry in fileArray) {
            if (result.size >= maxFiles) return
            val obj = entry.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val relativePath = if (subpath.isEmpty()) name else "$subpath/$name"
            val isDirectory = obj.containsKey("children")

            if (isDirectory) {
                fetchRecursive(serverUrl, token, buildId, relativePath, matcher, rootMatcher, maxDepth, maxFiles, result, currentDepth + 1)
            } else {
                val path = java.nio.file.Path.of(relativePath)
                if (matcher.matches(path) || rootMatcher?.matches(path) == true) {
                    result.add(relativePath)
                }
            }
        }
    }
}
