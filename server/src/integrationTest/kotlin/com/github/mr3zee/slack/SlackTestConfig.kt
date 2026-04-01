package com.github.mr3zee.slack

import com.github.mr3zee.TestPropertiesLoader

/**
 * Configuration for Slack integration tests.
 * Loaded from local.properties or environment variables.
 */
data class SlackTestConfig(
    val botToken: String,
    val channelId: String,
) {
    companion object {
        fun loadOrNull(): SlackTestConfig? {
            val props = TestPropertiesLoader.loadProperties()
            if (props != null) {
                val botToken = props.getProperty("slack.test.botToken")
                val channelId = props.getProperty("slack.test.channelId")
                if (!botToken.isNullOrBlank() && !channelId.isNullOrBlank()) {
                    return SlackTestConfig(
                        botToken = botToken,
                        channelId = channelId,
                    )
                }
            }

            val botToken = System.getenv("SLACK_TEST_BOT_TOKEN")
            val channelId = System.getenv("SLACK_TEST_CHANNEL_ID")
            if (botToken.isNullOrBlank() || channelId.isNullOrBlank()) {
                return null
            }

            return SlackTestConfig(
                botToken = botToken,
                channelId = channelId,
            )
        }
    }
}
