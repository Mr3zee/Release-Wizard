package com.github.mr3zee.auth

import com.github.mr3zee.model.ClientType
import com.github.mr3zee.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val username: String,
    val userId: String,
    val role: UserRole,
    // AUTH-L1: No default — CSRF token must always be set explicitly at login/registration.
    // Legacy sessions with empty token are rejected by CsrfPlugin (AUTH-H6).
    val csrfToken: String,
    val clientType: ClientType = ClientType.BROWSER,
    val createdAt: Long = 0L,
    val lastAccessedAt: Long = 0L,
    // Default true: legacy sessions (pre-approval feature) are treated as approved.
    val approved: Boolean = true,
)
