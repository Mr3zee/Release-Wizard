package com.github.mr3zee.connections

import com.github.mr3zee.api.ConnectionTestResult
import com.github.mr3zee.api.ExternalConfig
import com.github.mr3zee.api.ExternalConfigParameter
import com.github.mr3zee.api.ExternalConfigParametersResponse
import com.github.mr3zee.api.ExternalConfigsResponse
import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import com.github.mr3zee.AppJson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.InetAddress
import java.net.URI

/**
 * Tests connections by making lightweight API calls to verify credentials and reachability.
 */
class ConnectionTester(
    private val httpClient: HttpClient,
) {
    suspend fun test(config: ConnectionConfig): ConnectionTestResult = when (config) {
        is ConnectionConfig.SlackConfig -> testSlack(config)
        is ConnectionConfig.TeamCityConfig -> testTeamCity(config)
        is ConnectionConfig.GitHubConfig -> testGitHub(config)
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
            validateUrlNotPrivate(config.serverUrl)
            val response = httpClient.get("${config.serverUrl}/app/rest/server") {
                header("Authorization", "Bearer ${config.token}")
                header("Accept", "application/json")
            }
            if (response.status.isSuccess()) {
                ConnectionTestResult(success = true, message = "Connected to TeamCity server")
            } else {
                ConnectionTestResult(success = false, message = "TeamCity returned ${response.status}")
            }
        } catch (e: IllegalArgumentException) {
            ConnectionTestResult(success = false, message = e.message ?: "Invalid URL")
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

    suspend fun fetchTeamCityBuildTypes(config: ConnectionConfig.TeamCityConfig): ExternalConfigsResponse {
        validateUrlNotPrivate(config.serverUrl)
        val serverUrl = config.serverUrl

        // Fetch projects and build types in parallel — they are independent
        val (projectsResponse, buildTypesResponse) = coroutineScope {
            val projectsDeferred = async {
                httpClient.get("$serverUrl/app/rest/projects") {
                    parameter("locator", "archived:false")
                    parameter("fields", "project(id,name,parentProjectId)")
                    header("Authorization", "Bearer ${config.token}")
                    header("Accept", "application/json")
                }
            }
            val buildTypesDeferred = async {
                httpClient.get("$serverUrl/app/rest/buildTypes") {
                    parameter("locator", "project:(archived:false),templateFlag:false,count:5000")
                    parameter("fields", "buildType(id,name,projectId)")
                    header("Authorization", "Bearer ${config.token}")
                    header("Accept", "application/json")
                }
            }
            projectsDeferred.await() to buildTypesDeferred.await()
        }

        if (!projectsResponse.status.isSuccess()) {
            throw RuntimeException("Failed to fetch TeamCity projects (HTTP ${projectsResponse.status.value})")
        }
        if (!buildTypesResponse.status.isSuccess()) {
            throw RuntimeException("Failed to fetch TeamCity build types (HTTP ${buildTypesResponse.status.value})")
        }

        val projectsJson = AppJson.decodeFromString<JsonObject>(projectsResponse.bodyAsText())
        val projectsArray = projectsJson["project"]?.jsonArray ?: JsonArray(emptyList())

        data class TcProject(val id: String, val name: String, val parentProjectId: String?)
        val projectMap = mutableMapOf<String, TcProject>()
        for (p in projectsArray) {
            val obj = p.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: continue
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val parentId = obj["parentProjectId"]?.jsonPrimitive?.content
            projectMap[id] = TcProject(id, name, parentId)
        }

        fun buildProjectPath(projectId: String): String {
            val segments = mutableListOf<String>()
            var currentId: String? = projectId
            while (currentId != null && currentId != "_Root") {
                val project = projectMap[currentId] ?: break
                segments.add(project.name)
                currentId = project.parentProjectId
            }
            segments.reverse()
            return segments.joinToString(" / ")
        }

        val buildTypesJson = AppJson.decodeFromString<JsonObject>(buildTypesResponse.bodyAsText())
        val buildTypesArray = buildTypesJson["buildType"]?.jsonArray ?: JsonArray(emptyList())

        val configs = buildTypesArray.mapNotNull { bt ->
            val obj = bt.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val projectId = obj["projectId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val projectPath = buildProjectPath(projectId)
            val path = if (projectPath.isNotEmpty()) "$projectPath / $name" else name
            ExternalConfig(id = id, name = name, path = path)
        }

        return ExternalConfigsResponse(configs = configs)
    }

    suspend fun fetchTeamCityBuildTypeParameters(
        config: ConnectionConfig.TeamCityConfig,
        buildTypeId: String,
    ): ExternalConfigParametersResponse {
        validateUrlNotPrivate(config.serverUrl)

        val encodedBuildTypeId = encodePathSegment("id:$buildTypeId")
        val response = httpClient.get("${config.serverUrl}/app/rest/buildTypes/$encodedBuildTypeId/parameters") {
            parameter("fields", "property(name,value,own,type(rawValue))")
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/json")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to fetch parameters for build type $buildTypeId (HTTP ${response.status.value})")
        }
        val json = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        val properties = json["property"]?.jsonArray ?: JsonArray(emptyList())

        val parameters = properties.mapNotNull { prop ->
            val obj = prop.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.content ?: ""
            val own = obj["own"]?.jsonPrimitive?.booleanOrNull ?: false
            val typeRaw = obj["type"]?.jsonObject?.get("rawValue")?.jsonPrimitive?.content ?: ""

            // Filter: only own parameters, exclude passwords
            if (!own) return@mapNotNull null
            if (typeRaw.contains("password", ignoreCase = true)) return@mapNotNull null

            ExternalConfigParameter(name = name, value = value, type = typeRaw)
        }

        return ExternalConfigParametersResponse(parameters = parameters)
    }

    suspend fun fetchGitHubWorkflows(config: ConnectionConfig.GitHubConfig): ExternalConfigsResponse {
        val owner = encodePathSegment(config.owner)
        val repo = encodePathSegment(config.repo)
        val response = httpClient.get("https://api.github.com/repos/$owner/$repo/actions/workflows") {
            parameter("per_page", 100)
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to fetch GitHub workflows (HTTP ${response.status.value})")
        }
        val json = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        val workflowsArray = json["workflows"]?.jsonArray ?: JsonArray(emptyList())

        val configs = workflowsArray.mapNotNull { wf ->
            val obj = wf.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val state = obj["state"]?.jsonPrimitive?.content
            // Skip disabled workflows (manually, by inactivity, or in forks)
            if (state != null && state.startsWith("disabled")) return@mapNotNull null
            // Use the filename (e.g., "ci.yml") as the ID — this is what the executor expects as workflowFile
            val fileName = path.substringAfterLast("/")
            ExternalConfig(id = fileName, name = name, path = path)
        }

        return ExternalConfigsResponse(configs = configs)
    }

    suspend fun fetchGitHubWorkflowInputs(
        config: ConnectionConfig.GitHubConfig,
        workflowFile: String,
    ): ExternalConfigParametersResponse {
        val owner = encodePathSegment(config.owner)
        val repo = encodePathSegment(config.repo)
        val encodedFile = encodePathSegment(workflowFile)
        val response = httpClient.get("https://api.github.com/repos/$owner/$repo/contents/.github/workflows/$encodedFile") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to fetch workflow file $workflowFile (HTTP ${response.status.value})")
        }
        val json = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        val base64Content = json["content"]?.jsonPrimitive?.content
            ?: return ExternalConfigParametersResponse(parameters = emptyList())

        // GitHub returns base64 with newlines — strip them before decoding
        val yamlContent = try {
            val decoded = java.util.Base64.getMimeDecoder().decode(base64Content)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            return ExternalConfigParametersResponse(parameters = emptyList())
        }

        val parameters = parseWorkflowDispatchInputs(yamlContent)
        return ExternalConfigParametersResponse(parameters = parameters)
    }

    companion object {

        /**
         * Parses workflow_dispatch inputs from a GitHub Actions YAML file content.
         * Uses SnakeYAML to navigate: on → workflow_dispatch → inputs → {name: {default, type}}
         *
         * Note: SnakeYAML parses bare `on:` as boolean key `true` (YAML 1.1 spec),
         * so we check both "on" and `true` as map keys.
         */
        @Suppress("UNCHECKED_CAST")
        fun parseWorkflowDispatchInputs(yamlContent: String): List<ExternalConfigParameter> {
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val root = try {
                yaml.load<Any>(yamlContent) as? Map<*, *> ?: return emptyList()
            } catch (_: Exception) {
                return emptyList()
            }

            // Navigate: on -> workflow_dispatch -> inputs
            // SnakeYAML parses `on:` as boolean key `true` (YAML 1.1 behavior)
            val onValue = root["on"] ?: root[true] ?: return emptyList()
            // Handle both map form (on: {workflow_dispatch: ...}) and list form (on: [push, workflow_dispatch])
            val workflowDispatch = when (onValue) {
                is Map<*, *> -> onValue["workflow_dispatch"] as? Map<*, *>
                // List form means no inputs are configurable
                else -> null
            } ?: return emptyList()
            val inputsMap = workflowDispatch["inputs"] as? Map<*, *> ?: return emptyList()

            return inputsMap.entries.mapNotNull { (key, value) ->
                val name = key?.toString() ?: return@mapNotNull null
                val props = value as? Map<*, *> ?: return@mapNotNull null
                val default = props["default"]?.toString() ?: ""
                val type = props["type"]?.toString() ?: ""
                ExternalConfigParameter(name = name, value = default, type = type)
            }
        }
        /**
         * Validates that the given URL does not point to a private/loopback/link-local address
         * to prevent SSRF attacks.
         */
        fun validateUrlNotPrivate(url: String) {
            val host = try {
                URI(url).host ?: throw IllegalArgumentException("URL has no host: $url")
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid URL: $url")
            }

            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (_: Exception) {
                // If hostname can't be resolved, the actual HTTP request will fail anyway.
                // SSRF protection only needs to block resolvable private IPs.
                return
            }

            for (addr in addresses) {
                if (addr.isLoopbackAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isAnyLocalAddress
                ) {
                    throw IllegalArgumentException("Connections to private/internal network addresses are not allowed")
                }
            }
        }

        /** Percent-encodes a value for use as a single URL path segment (encodes / and other special chars). */
        fun encodePathSegment(value: String): String = buildString {
            for (c in value) {
                when {
                    c.isLetterOrDigit() || c in "-._~:@!$&'()*+,;=" -> append(c)
                    else -> {
                        for (b in c.toString().toByteArray(Charsets.UTF_8)) {
                            append('%')
                            append(String.format("%02X", b))
                        }
                    }
                }
            }
        }
    }
}
