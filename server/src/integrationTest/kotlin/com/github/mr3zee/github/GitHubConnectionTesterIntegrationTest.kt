package com.github.mr3zee.github

import com.github.mr3zee.connections.ConnectionTester
import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubConnectionTesterIntegrationTest {

    companion object {
        private var config: GitHubTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = GitHubTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createGitHubTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    @Test
    fun `valid token and repo succeeds`() = runBlocking {
        val cfg = config!!
        val tester = ConnectionTester(client!!)
        val result = tester.test(
            ConnectionConfig.GitHubConfig(
                token = cfg.token,
                owner = cfg.owner,
                repo = cfg.repo,
            )
        )
        assertTrue(result.success, "Expected success but got: ${result.message}")
        assertTrue(result.message.contains("${cfg.owner}/${cfg.repo}"), "Message should contain owner/repo")
    }

    @Test
    fun `invalid token fails`() = runBlocking {
        val cfg = config!!
        val tester = ConnectionTester(client!!)
        val result = tester.test(
            ConnectionConfig.GitHubConfig(
                token = "ghp_invalid_token_000000000000000000",
                owner = cfg.owner,
                repo = cfg.repo,
            )
        )
        assertFalse(result.success, "Expected failure for invalid token")
        assertTrue(result.message.contains("401"), "Message should contain 401 status: ${result.message}")
    }

    @Test
    fun `nonexistent repo fails`() = runBlocking {
        val cfg = config!!
        val tester = ConnectionTester(client!!)
        val result = tester.test(
            ConnectionConfig.GitHubConfig(
                token = cfg.token,
                owner = cfg.owner,
                repo = "nonexistent-repo-${System.currentTimeMillis()}",
            )
        )
        assertFalse(result.success, "Expected failure for nonexistent repo")
        assertTrue(result.message.contains("404"), "Message should contain 404 status: ${result.message}")
    }
}
