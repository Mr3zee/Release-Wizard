package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Gate(
    val message: String = "",
    val approvalRule: ApprovalRule = ApprovalRule(),
)

@Serializable
enum class GatePhase { PRE, POST }
