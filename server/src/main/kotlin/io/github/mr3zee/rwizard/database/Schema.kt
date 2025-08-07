package io.github.mr3zee.rwizard.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// Users and Authentication
object Users : UUIDTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val role = enumerationByName("role", 50, io.github.mr3zee.rwizard.domain.model.UserRole::class)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val lastLoginAt = timestamp("last_login_at").nullable()
}

object UserCredentials : UUIDTable("user_credentials") {
    val userId = reference("user_id", Users)
    val passwordHash = varchar("password_hash", 255)
    val salt = varchar("salt", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val lastPasswordChange = timestamp("last_password_change").nullable()
}

object UserSessions : UUIDTable("user_sessions") {
    val userId = reference("user_id", Users)
    val token = varchar("token", 500)
    val refreshToken = varchar("refresh_token", 500).nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val lastUsedAt = timestamp("last_used_at").defaultExpression(CurrentTimestamp)
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val isRevoked = bool("is_revoked").default(false)
}

// Connections
object Connections : UUIDTable("connections") {
    val name = varchar("name", 255)
    val description = text("description")
    val type = enumerationByName("type", 50, io.github.mr3zee.rwizard.domain.model.ConnectionType::class)
    val config = text("config") // JSON string with connection-specific configuration
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object ConnectionCredentials : UUIDTable("connection_credentials") {
    val connectionId = reference("connection_id", Connections)
    val encryptedData = text("encrypted_data") // Encrypted JSON containing sensitive data
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

// Projects
object Projects : UUIDTable("projects") {
    val name = varchar("name", 255)
    val description = text("description")
    val parameters = text("parameters") // JSON string with project parameters
    val blockGraph = text("block_graph") // JSON string with block graph definition
    val version = integer("version").default(1)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object ProjectConnections : UUIDTable("project_connections") {
    val projectId = reference("project_id", Projects)
    val connectionId = reference("connection_id", Connections)
}

object MessageTemplates : UUIDTable("message_templates") {
    val projectId = reference("project_id", Projects)
    val name = varchar("name", 255)
    val description = text("description")
    val template = text("template")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

// Releases
object Releases : UUIDTable("releases") {
    val projectId = reference("project_id", Projects)
    val name = varchar("name", 255)
    val description = text("description")
    val parameterValues = text("parameter_values") // JSON string
    val status = enumerationByName("status", 50, io.github.mr3zee.rwizard.domain.model.ReleaseStatus::class)
    val startedBy = varchar("started_by", 255) // User ID or system
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object BlockExecutions : UUIDTable("block_executions") {
    val releaseId = reference("release_id", Releases)
    val blockId = varchar("block_id", 255) // Reference to block definition in project
    val blockType = varchar("block_type", 255)
    val status = enumerationByName("status", 50, io.github.mr3zee.rwizard.domain.model.BlockExecutionStatus::class)
    val parameterValues = text("parameter_values") // JSON string
    val outputValues = text("output_values") // JSON string
    val metadata = text("metadata") // JSON string
    val retryCount = integer("retry_count").default(0)
    val maxRetries = integer("max_retries").default(3)
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object ExecutionLogs : UUIDTable("execution_logs") {
    val blockExecutionId = reference("block_execution_id", BlockExecutions)
    val level = enumerationByName("level", 50, io.github.mr3zee.rwizard.domain.model.LogLevel::class)
    val message = text("message")
    val sourceField = varchar("source", 255).nullable()
    val metadata = text("metadata") // JSON string
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp)
}

object UserInputs : UUIDTable("user_inputs") {
    val blockExecutionId = reference("block_execution_id", BlockExecutions)
    val prompt = text("prompt")
    val inputType = enumerationByName("input_type", 50, io.github.mr3zee.rwizard.domain.model.UserInputType::class)
    val options = text("options") // JSON string
    val isRequired = bool("is_required").default(true)
    val submittedValue = text("submitted_value").nullable()
    val submittedBy = varchar("submitted_by", 255).nullable()
    val submittedAt = timestamp("submitted_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
