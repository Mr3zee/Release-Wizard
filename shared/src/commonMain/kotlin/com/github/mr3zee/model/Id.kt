package com.github.mr3zee.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProjectId(val value: String)

@Serializable
@JvmInline
value class ReleaseId(val value: String)

@Serializable
@JvmInline
value class BlockId(val value: String)

@Serializable
@JvmInline
value class ConnectionId(val value: String)
