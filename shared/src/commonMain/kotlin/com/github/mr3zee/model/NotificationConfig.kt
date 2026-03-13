package com.github.mr3zee.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NotificationConfig {
    @Serializable
    @SerialName("slack")
    data class SlackNotification(
        val webhookUrl: String,
        val channel: String,
    ) : NotificationConfig()
}
