package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object OAuthAccountTable : UUIDTable("oauth_accounts") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val provider = varchar("provider", 32)
    val providerUserId = varchar("provider_user_id", 255)
    val email = varchar("email", 255).nullable()
    val displayName = varchar("display_name", 255).nullable()
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex("oauth_accounts_provider_provider_user_id_unique", provider, providerUserId)
        uniqueIndex("oauth_accounts_user_id_provider_unique", userId, provider)
    }
}
