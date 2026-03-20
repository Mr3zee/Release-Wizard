package com.github.mr3zee.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

// "kind" discriminator instead of default "type" because ActionBlock has a `type: BlockType` property
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
sealed class Block {
    abstract val id: BlockId
    abstract val name: String
    abstract val description: String

    @Serializable
    @SerialName("container")
    data class ContainerBlock(
        override val id: BlockId,
        override val name: String,
        override val description: String = "",
        val children: DagGraph = DagGraph(),
    ) : Block()

    @Serializable
    @SerialName("action")
    data class ActionBlock(
        override val id: BlockId,
        override val name: String,
        override val description: String = "",
        val type: BlockType,
        val parameters: List<Parameter> = emptyList(),
        val outputs: List<String> = emptyList(),
        val timeoutSeconds: Long? = null,
        val connectionId: ConnectionId? = null,
        val preGate: Gate? = null,
        val postGate: Gate? = null,
        val injectWebhookUrl: Boolean = false,
    ) : Block()
}
