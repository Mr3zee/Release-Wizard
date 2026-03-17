package com.github.mr3zee.github

import com.github.mr3zee.TestPropertiesLoader

/**
 * Configuration for GitHub integration tests.
 * Loaded from local.properties or environment variables.
 */
data class GitHubTestConfig(
    val token: String,
    val owner: String,
    val repo: String,
    val workflowFile: String = "test.yml",
    val ref: String = "main",
) {
    companion object {
        fun loadOrNull(): GitHubTestConfig? {
            val props = TestPropertiesLoader.loadProperties()
            if (props != null) {
                val token = props.getProperty("github.test.token")
                val owner = props.getProperty("github.test.owner")
                val repo = props.getProperty("github.test.repo")
                if (!token.isNullOrBlank() && !owner.isNullOrBlank() && !repo.isNullOrBlank()) {
                    return GitHubTestConfig(
                        token = token,
                        owner = owner,
                        repo = repo,

                        workflowFile = props.getProperty("github.test.workflowFile") ?: "test.yml",
                        ref = props.getProperty("github.test.ref") ?: "main",
                    )
                }
            }

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

                workflowFile = System.getenv("GITHUB_TEST_WORKFLOW_FILE") ?: "test.yml",
                ref = System.getenv("GITHUB_TEST_REF") ?: "main",
            )
        }
    }
}
