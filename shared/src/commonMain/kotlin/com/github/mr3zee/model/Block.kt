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

    @Serializable
    @SerialName("container")
    data class ContainerBlock(
        override val id: BlockId,
        override val name: String,
        val children: DagGraph = DagGraph(),
    ) : Block()

    @Serializable
    @SerialName("action")
    data class ActionBlock(
        override val id: BlockId,
        override val name: String,
        val type: BlockType,
        val parameters: List<Parameter> = emptyList(),
        val outputs: List<String> = emptyList(),
        val timeoutSeconds: Long? = null,
        val connectionId: ConnectionId? = null,
        val approvalRule: ApprovalRule? = null,
    ) : Block()
}
