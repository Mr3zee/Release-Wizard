package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN,
    USER,
}
