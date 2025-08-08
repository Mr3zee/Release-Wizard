@file:OptIn(ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.api

import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Rpc
interface ProjectService {
    
    // Project management
    suspend fun createProject(request: CreateProjectRequest): ProjectResponse
    suspend fun getProject(projectId: Uuid): ProjectResponse
    suspend fun updateProject(request: UpdateProjectRequest): ProjectResponse
    suspend fun deleteProject(projectId: Uuid): SuccessResponse
    suspend fun listProjects(request: ListProjectsRequest = ListProjectsRequest()): ProjectListResponse
    
    // Project templates and validation
    suspend fun validateProject(project: Project): ValidationResponse
    suspend fun getProjectTemplates(): ProjectTemplateListResponse
    suspend fun createProjectFromTemplate(request: CreateFromTemplateRequest): ProjectResponse
    
    // Message templates
    suspend fun createMessageTemplate(request: CreateMessageTemplateRequest): MessageTemplateResponse
    suspend fun updateMessageTemplate(request: UpdateMessageTemplateRequest): MessageTemplateResponse
    suspend fun deleteMessageTemplate(templateId: Uuid): SuccessResponse
    suspend fun listMessageTemplates(projectId: Uuid): MessageTemplateListResponse
}

// Request/Response DTOs
@Serializable
data class CreateProjectRequest(
    val name: String,
    val description: String,
    val parameters: List<ProjectParameter> = emptyList(),
    val connections: List<Uuid> = emptyList(), // Connection IDs
    val blockGraph: BlockGraph = BlockGraph(emptyList(), emptyList())
)

@Serializable
data class UpdateProjectRequest(
    val projectId: Uuid,
    val name: String? = null,
    val description: String? = null,
    val parameters: List<ProjectParameter>? = null,
    val connections: List<Uuid>? = null,
    val blockGraph: BlockGraph? = null
)

@Serializable
data class ListProjectsRequest(
    val search: String? = null,
    val limit: Int = 50,
    val offset: Int = 0,
    val sortBy: ProjectSortBy = ProjectSortBy.UPDATED_AT,
    val sortOrder: SortOrder = SortOrder.DESC
)

@Serializable
enum class ProjectSortBy {
    NAME, CREATED_AT, UPDATED_AT
}

@Serializable
enum class SortOrder {
    ASC, DESC
}

@Serializable
data class CreateFromTemplateRequest(
    val templateId: Uuid,
    val name: String,
    val description: String,
    val parameterOverrides: Map<String, String> = emptyMap()
)

@Serializable
data class CreateMessageTemplateRequest(
    val projectId: Uuid,
    val name: String,
    val description: String,
    val template: String
)

@Serializable
data class UpdateMessageTemplateRequest(
    val templateId: Uuid,
    val name: String? = null,
    val description: String? = null,
    val template: String? = null
)

// Response DTOs
@Serializable
data class ProjectResponse(
    val success: Boolean,
    val project: Project? = null,
    val error: String? = null
)

@Serializable
data class ProjectListResponse(
    val success: Boolean,
    val projects: List<Project> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class ValidationResponse(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
)

@Serializable
data class ValidationError(
    val field: String,
    val message: String,
    val code: String
)

@Serializable
data class ProjectTemplate(
    val id: Uuid,
    val name: String,
    val description: String,
    val category: String,
    val project: Project
)

@Serializable
data class ProjectTemplateListResponse(
    val success: Boolean,
    val templates: List<ProjectTemplate> = emptyList(),
    val error: String? = null
)

@Serializable
data class MessageTemplateResponse(
    val success: Boolean,
    val template: MessageTemplate? = null,
    val error: String? = null
)

@Serializable
data class MessageTemplateListResponse(
    val success: Boolean,
    val templates: List<MessageTemplate> = emptyList(),
    val error: String? = null
)

@Serializable
data class SuccessResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)
