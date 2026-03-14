package com.github.mr3zee.github

import java.io.File
import java.util.Properties

/**
 * Configuration for GitHub integration tests.
 * Loaded from local.properties or environment variables.
 */
data class GitHubTestConfig(
    val token: String,
    val owner: String,
    val repo: String,
    val webhookSecret: String = "",
    val workflowFile: String = "test.yml",
    val ref: String = "main",
) {
    companion object {
        private fun findLocalProperties(): File {
            // Check CWD first, then walk up to find the root project (where .git lives)
            // todo claude: duplicate 9 lines
            val cwd = File("local.properties")
            if (cwd.exists()) return cwd
            var dir = File(".").absoluteFile.parentFile
            while (dir != null) {
                val candidate = File(dir, "local.properties")
                if (candidate.exists()) return candidate
                dir = dir.parentFile
            }
            return cwd // return non-existent file, will be handled by exists() check
        }

        fun loadOrNull(): GitHubTestConfig? {
            // Try local.properties at project root (Gradle may set CWD to subproject dir)
            val propsFile = findLocalProperties()
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                val token = props.getProperty("github.test.token")
                val owner = props.getProperty("github.test.owner")
                val repo = props.getProperty("github.test.repo")
                if (!token.isNullOrBlank() && !owner.isNullOrBlank() && !repo.isNullOrBlank()) {
                    return GitHubTestConfig(
                        token = token,
                        owner = owner,
                        repo = repo,
                        webhookSecret = props.getProperty("github.test.webhookSecret") ?: "",
                        workflowFile = props.getProperty("github.test.workflowFile") ?: "test.yml",
                        ref = props.getProperty("github.test.ref") ?: "main",
                    )
                }
            }

            // Fall back to environment variables
            val token = System.getenv("GITHUB_TEST_TOKEN")
            val owner = System.getenv("GITHUB_TEST_OWNER")
            val repo = System.getenv("GITHUB_TEST_REPO")
            if (token.isNullOrBlank() || owner.isNullOrBlank() || repo.isNullOrBlank()) {
                return null
            }

            return GitHubTestConfig(
                token = token,
                owner = owner,
                repo = repo,
                webhookSecret = System.getenv("GITHUB_TEST_WEBHOOK_SECRET") ?: "",
                workflowFile = System.getenv("GITHUB_TEST_WORKFLOW_FILE") ?: "test.yml",
                ref = System.getenv("GITHUB_TEST_REF") ?: "main",
            )
        }
    }
}
