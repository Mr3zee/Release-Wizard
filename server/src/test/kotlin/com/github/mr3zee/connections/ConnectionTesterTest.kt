package com.github.mr3zee.connections

import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionTesterTest {

    private fun createTester(handler: MockRequestHandler): ConnectionTester {
        return ConnectionTester(HttpClient(MockEngine(handler)))
    }

    // Slack

    @Test
    fun `slack valid webhook URL succeeds`() = runTest {
        val tester = createTester { respondOk() }
        val result = tester.test(ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/services/T00/B00/xxx"))
        assertTrue(result.success)
    }

    @Test
    fun `slack invalid webhook URL fails`() = runTest {
        val tester = createTester { respondOk() }
        val result = tester.test(ConnectionConfig.SlackConfig(webhookUrl = "https://example.com/hook"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid Slack webhook URL"))
    }

    // TeamCity

    @Test
    fun `teamcity successful connection`() = runTest {
        val tester = createTester { respond("""{"version":"2023.11"}""", HttpStatusCode.OK) }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"))
        assertTrue(result.success)
        assertEquals("Connected to TeamCity server", result.message)
    }

    @Test
    fun `teamcity unauthorized returns failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.Unauthorized) }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "bad"))
        assertFalse(result.success)
        assertTrue(result.message.contains("401"))
    }

    @Test
    fun `teamcity connection error returns failure`() = runTest {
        val tester = createTester { throw RuntimeException("Connection refused") }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Connection refused"))
    }

    // GitHub

    @Test
    fun `github successful connection`() = runTest {
        val tester = createTester { respond("""{"id":1}""", HttpStatusCode.OK) }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "mr3zee", repo = "rw"))
        assertTrue(result.success)
        assertTrue(result.message.contains("mr3zee/rw"))
    }

    @Test
    fun `github not found returns failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.NotFound) }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "bad", repo = "repo"))
        assertFalse(result.success)
        assertTrue(result.message.contains("404"))
    }

    @Test
    fun `github connection error returns failure`() = runTest {
        val tester = createTester { throw RuntimeException("DNS resolution failed") }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "o", repo = "r"))
        assertFalse(result.success)
        assertTrue(result.message.contains("DNS resolution failed"))
    }

    // TeamCity Build Types Discovery

    private val tcConfig = ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token")

    private val projectsJson = """{"project":[
        {"id":"_Root","name":"<Root project>","parentProjectId":null},
        {"id":"Org","name":"Organization","parentProjectId":"_Root"},
        {"id":"Org_Team","name":"Team Alpha","parentProjectId":"Org"},
        {"id":"Org_Team_Product","name":"Product X","parentProjectId":"Org_Team"}
    ]}"""

    private val buildTypesJson = """{"buildType":[
        {"id":"Org_Team_Build","name":"Build","projectId":"Org_Team"},
        {"id":"Org_Team_Product_Deploy","name":"Deploy","projectId":"Org_Team_Product"}
    ]}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `fetchTeamCityBuildTypes returns configs with project paths`() = runTest {
        val tester = createTester { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/projects") -> respond(projectsJson, HttpStatusCode.OK, jsonHeaders())
                url.contains("/app/rest/buildTypes") -> respond(buildTypesJson, HttpStatusCode.OK, jsonHeaders())
                else -> respondOk()
            }
        }
        val result = tester.fetchTeamCityBuildTypes(tcConfig)
        assertEquals(2, result.configs.size)

        val build = result.configs.find { it.id == "Org_Team_Build" }
            ?: error("Expected config Org_Team_Build")
        assertEquals("Build", build.name)
        assertEquals("Organization / Team Alpha / Build", build.path)

        val deploy = result.configs.find { it.id == "Org_Team_Product_Deploy" }
            ?: error("Expected config Org_Team_Product_Deploy")
        assertEquals("Deploy", deploy.name)
        assertEquals("Organization / Team Alpha / Product X / Deploy", deploy.path)
    }

    @Test
    fun `fetchTeamCityBuildTypes returns empty list when no build types`() = runTest {
        val tester = createTester { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/projects") -> respond("""{"project":[]}""", HttpStatusCode.OK, jsonHeaders())
                url.contains("/app/rest/buildTypes") -> respond("""{"buildType":[]}""", HttpStatusCode.OK, jsonHeaders())
                else -> respondOk()
            }
        }
        val result = tester.fetchTeamCityBuildTypes(tcConfig)
        assertTrue(result.configs.isEmpty())
    }

    @Test
    fun `fetchTeamCityBuildTypes throws on projects API failure`() = runTest {
        val tester = createTester { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/projects") -> respond("", HttpStatusCode.Unauthorized, jsonHeaders())
                url.contains("/app/rest/buildTypes") -> respond(buildTypesJson, HttpStatusCode.OK, jsonHeaders())
                else -> respondOk()
            }
        }
        val e = assertFailsWith<RuntimeException> {
            tester.fetchTeamCityBuildTypes(tcConfig)
        }
        assertTrue(e.message?.contains("401") == true)
    }

    @Test
    fun `fetchTeamCityBuildTypes throws on build types API failure`() = runTest {
        val tester = createTester { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/projects") -> respond(projectsJson, HttpStatusCode.OK, jsonHeaders())
                url.contains("/app/rest/buildTypes") -> respond("", HttpStatusCode.InternalServerError, jsonHeaders())
                else -> respondOk()
            }
        }
        val e = assertFailsWith<RuntimeException> {
            tester.fetchTeamCityBuildTypes(tcConfig)
        }
        assertTrue(e.message?.contains("500") == true)
    }

    // TeamCity Build Type Parameters

    private val parametersJson = """{"property":[
        {"name":"env.VERSION","value":"1.0.0","own":true,"type":{"rawValue":"text"}},
        {"name":"env.DEPLOY_TARGET","value":"staging","own":true,"type":{"rawValue":"select"}},
        {"name":"env.INHERITED","value":"inherited-val","own":false,"type":{"rawValue":"text"}},
        {"name":"env.SECRET_TOKEN","value":"******","own":true,"type":{"rawValue":"password display='hidden'"}},
        {"name":"env.NO_TYPE","value":"plain","own":true}
    ]}"""

    @Test
    fun `fetchTeamCityBuildTypeParameters returns only own non-password params`() = runTest {
        val tester = createTester { respond(parametersJson, HttpStatusCode.OK, jsonHeaders()) }
        val result = tester.fetchTeamCityBuildTypeParameters(tcConfig, "bt1")

        // Should include own=true non-password params only
        assertEquals(3, result.parameters.size)
        assertTrue(result.parameters.any { it.name == "env.VERSION" && it.value == "1.0.0" })
        assertTrue(result.parameters.any { it.name == "env.DEPLOY_TARGET" && it.value == "staging" })
        assertTrue(result.parameters.any { it.name == "env.NO_TYPE" && it.value == "plain" })

        // Should exclude inherited and password params
        assertFalse(result.parameters.any { it.name == "env.INHERITED" })
        assertFalse(result.parameters.any { it.name == "env.SECRET_TOKEN" })
    }

    @Test
    fun `fetchTeamCityBuildTypeParameters returns empty list for no properties`() = runTest {
        val tester = createTester { respond("""{"property":[]}""", HttpStatusCode.OK, jsonHeaders()) }
        val result = tester.fetchTeamCityBuildTypeParameters(tcConfig, "bt1")
        assertTrue(result.parameters.isEmpty())
    }

    @Test
    fun `fetchTeamCityBuildTypeParameters throws on API failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.NotFound, jsonHeaders()) }
        val e = assertFailsWith<RuntimeException> {
            tester.fetchTeamCityBuildTypeParameters(tcConfig, "nonexistent")
        }
        assertTrue(e.message?.contains("404") == true)
    }

    @Test
    fun `fetchTeamCityBuildTypeParameters encodes buildTypeId with special chars`() = runTest {
        var capturedUrl: String? = null
        val tester = createTester { request ->
            capturedUrl = request.url.toString()
            respond("""{"property":[]}""", HttpStatusCode.OK, jsonHeaders())
        }
        tester.fetchTeamCityBuildTypeParameters(tcConfig, "Project/Sub_Build")
        val url = capturedUrl ?: error("URL should have been captured")
        // The slash in the buildTypeId should be percent-encoded in the path
        assertTrue(url.contains("id:Project%2FSub_Build"), "Expected encoded slash in URL, got: $url")
    }

    @Test
    fun `fetchTeamCityBuildTypes handles build type with unknown projectId gracefully`() = runTest {
        val buildTypesWithMissingProject = """{"buildType":[
            {"id":"Orphan_Build","name":"Orphan","projectId":"NonExistent"}
        ]}"""
        val tester = createTester { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/projects") -> respond("""{"project":[]}""", HttpStatusCode.OK, jsonHeaders())
                url.contains("/app/rest/buildTypes") -> respond(buildTypesWithMissingProject, HttpStatusCode.OK, jsonHeaders())
                else -> respondOk()
            }
        }
        val result = tester.fetchTeamCityBuildTypes(tcConfig)
        assertEquals(1, result.configs.size)
        assertEquals("Orphan", result.configs[0].name)
        // Path should just be the name when project is not in the map
        assertEquals("Orphan", result.configs[0].path)
    }

    // GitHub Workflow Discovery

    private val ghConfig = ConnectionConfig.GitHubConfig(token = "ghp_test", owner = "mr3zee", repo = "release-wizard")

    private val workflowsJson = """{"total_count":5,"workflows":[
        {"id":1,"name":"CI","path":".github/workflows/ci.yml","state":"active"},
        {"id":2,"name":"Deploy","path":".github/workflows/deploy.yml","state":"active"},
        {"id":3,"name":"Old","path":".github/workflows/old.yml","state":"disabled_manually"},
        {"id":4,"name":"Inactive","path":".github/workflows/inactive.yml","state":"disabled_inactivity"},
        {"id":5,"name":"Fork","path":".github/workflows/fork.yml","state":"disabled_fork"}
    ]}"""

    @Test
    fun `fetchGitHubWorkflows returns active workflows and filters all disabled states`() = runTest {
        val tester = createTester { respond(workflowsJson, HttpStatusCode.OK, jsonHeaders()) }
        val result = tester.fetchGitHubWorkflows(ghConfig)
        // Should exclude all disabled workflows (manually, inactivity, fork)
        assertEquals(2, result.configs.size)

        val ci = result.configs.find { it.name == "CI" } ?: error("Expected CI workflow")
        assertEquals("ci.yml", ci.id)
        assertEquals(".github/workflows/ci.yml", ci.path)

        val deploy = result.configs.find { it.name == "Deploy" } ?: error("Expected Deploy workflow")
        assertEquals("deploy.yml", deploy.id)
    }

    @Test
    fun `fetchGitHubWorkflows returns empty for no workflows`() = runTest {
        val tester = createTester { respond("""{"total_count":0,"workflows":[]}""", HttpStatusCode.OK, jsonHeaders()) }
        val result = tester.fetchGitHubWorkflows(ghConfig)
        assertTrue(result.configs.isEmpty())
    }

    @Test
    fun `fetchGitHubWorkflows throws on API failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.Unauthorized, jsonHeaders()) }
        val e = assertFailsWith<RuntimeException> {
            tester.fetchGitHubWorkflows(ghConfig)
        }
        assertTrue(e.message?.contains("401") == true)
    }

    // GitHub Workflow Input Parsing (YAML)

    @Test
    fun `parseWorkflowDispatchInputs extracts inputs from workflow YAML`() {
        val yaml = """
            name: Deploy
            on:
              workflow_dispatch:
                inputs:
                  environment:
                    description: 'Target environment'
                    required: true
                    default: staging
                    type: choice
                  version:
                    description: 'Version to deploy'
                    required: false
                    type: string
                  dry_run:
                    description: 'Dry run mode'
                    default: 'true'
                    type: boolean
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertEquals(3, inputs.size)

        val env = inputs.find { it.name == "environment" } ?: error("Expected environment input")
        assertEquals("staging", env.value)
        assertEquals("choice", env.type)

        val version = inputs.find { it.name == "version" } ?: error("Expected version input")
        assertEquals("", version.value)
        assertEquals("string", version.type)

        val dryRun = inputs.find { it.name == "dry_run" } ?: error("Expected dry_run input")
        assertEquals("true", dryRun.value)
        assertEquals("boolean", dryRun.type)
    }

    @Test
    fun `parseWorkflowDispatchInputs returns empty for workflow without dispatch inputs`() {
        val yaml = """
            name: CI
            on:
              push:
                branches: [main]
              pull_request:
                branches: [main]
            jobs:
              build:
                runs-on: ubuntu-latest
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertTrue(inputs.isEmpty())
    }

    @Test
    fun `parseWorkflowDispatchInputs returns empty for workflow_dispatch without inputs`() {
        val yaml = """
            name: Manual
            on:
              workflow_dispatch:
            jobs:
              run:
                runs-on: ubuntu-latest
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertTrue(inputs.isEmpty())
    }

    @Test
    fun `parseWorkflowDispatchInputs handles invalid YAML gracefully`() {
        val inputs = ConnectionTester.parseWorkflowDispatchInputs("{{invalid yaml content")
        assertTrue(inputs.isEmpty())
    }

    @Test
    fun `parseWorkflowDispatchInputs handles list-form on trigger`() {
        val yaml = """
            name: CI
            on: [push, workflow_dispatch]
            jobs:
              build:
                runs-on: ubuntu-latest
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertTrue(inputs.isEmpty(), "List-form 'on' has no configurable inputs")
    }

    @Test
    fun `parseWorkflowDispatchInputs handles mixed triggers with workflow_dispatch inputs`() {
        val yaml = """
            name: Deploy
            on:
              push:
                branches: [main]
              workflow_dispatch:
                inputs:
                  target:
                    default: staging
                    type: string
            jobs:
              deploy:
                runs-on: ubuntu-latest
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertEquals(1, inputs.size)
        assertEquals("target", inputs[0].name)
        assertEquals("staging", inputs[0].value)
    }

    @Test
    fun `parseWorkflowDispatchInputs handles unquoted boolean default`() {
        val yaml = """
            name: Test
            on:
              workflow_dispatch:
                inputs:
                  dry_run:
                    default: true
                    type: boolean
        """.trimIndent()

        val inputs = ConnectionTester.parseWorkflowDispatchInputs(yaml)
        assertEquals(1, inputs.size)
        assertEquals("true", inputs[0].value)
    }

    @Test
    fun `fetchGitHubWorkflowInputs fetches and parses workflow file`() = runTest {
        val workflowYaml = """
            name: Deploy
            on:
              workflow_dispatch:
                inputs:
                  target:
                    default: prod
                    type: string
        """.trimIndent()
        val base64Content = java.util.Base64.getEncoder().encodeToString(workflowYaml.toByteArray())

        val tester = createTester { respond("""{"content":"$base64Content"}""", HttpStatusCode.OK, jsonHeaders()) }
        val result = tester.fetchGitHubWorkflowInputs(ghConfig, "deploy.yml")
        assertEquals(1, result.parameters.size)
        assertEquals("target", result.parameters[0].name)
        assertEquals("prod", result.parameters[0].value)
        assertEquals("string", result.parameters[0].type)
    }

    @Test
    fun `fetchGitHubWorkflowInputs returns empty on API failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.NotFound, jsonHeaders()) }
        val e = assertFailsWith<RuntimeException> {
            tester.fetchGitHubWorkflowInputs(ghConfig, "nonexistent.yml")
        }
        assertTrue(e.message?.contains("404") == true)
    }
}
