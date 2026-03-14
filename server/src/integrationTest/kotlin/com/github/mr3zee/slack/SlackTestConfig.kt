package com.github.mr3zee.slack

import com.github.mr3zee.TestPropertiesLoader

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
        fun loadOrNull(): SlackTestConfig? {
            val props = TestPropertiesLoader.loadProperties()
            if (props != null) {
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
