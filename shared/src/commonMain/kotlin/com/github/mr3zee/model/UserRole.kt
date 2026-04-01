package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    SUPERADMIN,
    ADMIN,
    USER,
}

val UserRole.isAdmin: Boolean
    get() = this == UserRole.SUPERADMIN || this == UserRole.ADMIN
