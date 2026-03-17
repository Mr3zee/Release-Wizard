package com.github.mr3zee.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class BlockExecution(
    val blockId: BlockId,
    val releaseId: ReleaseId,
    val status: BlockStatus,
    val outputs: Map<String, String> = emptyMap(),
    val error: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val approvals: List<BlockApproval> = emptyList(),
    val gatePhase: GatePhase? = null,
    val gateMessage: String? = null,
    val webhookStatus: WebhookStatusUpdate? = null,
    val subBuilds: List<SubBuild> = emptyList(),
) {
    companion object {
        /** Key in [outputs] for JSON-encoded artifact paths list. */
        const val ARTIFACTS_OUTPUT_KEY = "artifacts"
    }
}
