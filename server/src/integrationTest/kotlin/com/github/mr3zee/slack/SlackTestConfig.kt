package com.github.mr3zee.slack

import java.io.File
import java.util.Properties

/**
 * Configuration for Slack integration tests.
 * Loaded from local.properties or environment variables.
 */
data class SlackTestConfig(
    val webhookUrl: String,
    val botToken: String,
    val channelId: String,
) {
    companion object {
        private fun findLocalProperties(): File {
            val cwd = File("local.properties")
            if (cwd.exists()) return cwd
            var dir = File(".").absoluteFile.parentFile
            while (dir != null) {
                val candidate = File(dir, "local.properties")
                if (candidate.exists()) return candidate
                dir = dir.parentFile
            }
            return cwd
        }

        fun loadOrNull(): SlackTestConfig? {
            val propsFile = findLocalProperties()
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                val webhookUrl = props.getProperty("slack.test.webhookUrl")
                val botToken = props.getProperty("slack.test.botToken")
                val channelId = props.getProperty("slack.test.channelId")
                if (!webhookUrl.isNullOrBlank() && !botToken.isNullOrBlank() && !channelId.isNullOrBlank()) {
                    return SlackTestConfig(
                        webhookUrl = webhookUrl,
                        botToken = botToken,
                        channelId = channelId,
                    )
                }
            }

            val webhookUrl = System.getenv("SLACK_TEST_WEBHOOK_URL")
            val botToken = System.getenv("SLACK_TEST_BOT_TOKEN")
            val channelId = System.getenv("SLACK_TEST_CHANNEL_ID")
            if (webhookUrl.isNullOrBlank() || botToken.isNullOrBlank() || channelId.isNullOrBlank()) {
                return null
            }

            return SlackTestConfig(
                webhookUrl = webhookUrl,
                botToken = botToken,
                channelId = channelId,
            )
        }
    }
}
