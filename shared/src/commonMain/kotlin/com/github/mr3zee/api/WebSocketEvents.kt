package com.github.mr3zee.api

import com.github.mr3zee.model.*
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ReleaseEvent {
    abstract val releaseId: ReleaseId
    abstract val sequenceNumber: Long

    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        override val releaseId: ReleaseId,
        val release: Release,
        val blockExecutions: List<BlockExecution>,
        override val sequenceNumber: Long = 0,
    ) : ReleaseEvent()

    @Serializable
    @SerialName("release_status_changed")
    data class ReleaseStatusChanged(
        override val releaseId: ReleaseId,
        val status: ReleaseStatus,
        val startedAt: Instant? = null,
        val finishedAt: Instant? = null,
        override val sequenceNumber: Long = 0,
    ) : ReleaseEvent()

    @Serializable
    @SerialName("block_execution_updated")
    data class BlockExecutionUpdated(
        override val releaseId: ReleaseId,
        val blockExecution: BlockExecution,
        override val sequenceNumber: Long = 0,
    ) : ReleaseEvent()

    @Serializable
    @SerialName("release_completed")
    data class ReleaseCompleted(
        override val releaseId: ReleaseId,
        val status: ReleaseStatus,
        val finishedAt: Instant,
        override val sequenceNumber: Long = 0,
    ) : ReleaseEvent()
}

/**
 * Returns a copy of this event with the given sequence number.
 */
fun ReleaseEvent.withSequenceNumber(seq: Long): ReleaseEvent = when (this) {
    is ReleaseEvent.Snapshot -> copy(sequenceNumber = seq)
    is ReleaseEvent.ReleaseStatusChanged -> copy(sequenceNumber = seq)
    is ReleaseEvent.BlockExecutionUpdated -> copy(sequenceNumber = seq)
    is ReleaseEvent.ReleaseCompleted -> copy(sequenceNumber = seq)
}
