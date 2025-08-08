@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package io.github.mr3zee.rwizard.services

import io.github.mr3zee.rwizard.api.*
import io.github.mr3zee.rwizard.database.*
import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpsertSqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder as ExposedSortOrder

class ProjectServiceImpl : ProjectService {
    
    override suspend fun createProject(request: CreateProjectRequest): ProjectResponse {
        return try {
            tr {
                val projectId = Uuid.random()
                val now = Clock.System.now()
                
                val project = Project(
                    id = projectId,
                    name = request.name,
                    description = request.description,
                    parameters = request.parameters,
                    connections = emptyList(), // Will be populated from join table
                    blockGraph = request.blockGraph,
                    createdAt = now,
                    updatedAt = now
                )
                
                Projects.insert {
                    it[id] = projectId.toJavaUuid()
                    it[name] = request.name
                    it[description] = request.description
                    it[parameters] = Json.encodeToString(request.parameters.map { param ->
                        mapOf(
                            "name" to param.name,
                            "description" to param.description,
                            "type" to param.type.name,
                            "defaultValue" to param.defaultValue,
                            "isOptional" to param.isOptional
                        )
                    })
                    it[blockGraph] = Json.encodeToString(mapOf(
                        "blocks" to request.blockGraph.blocks,
                        "connections" to request.blockGraph.connections
                    ))
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                
                // Insert project connections
                request.connections.forEach { connectionId ->
                    ProjectConnections.insert {
                        it[this.projectId] = projectId.toJavaUuid()
                        it[this.connectionId] = connectionId.toJavaUuid()
                    }
                }
                
                ProjectResponse(success = true, project = project)
            }
        } catch (e: Exception) {
            ProjectResponse(success = false, error = "Failed to create project: ${e.message}")
        }
    }
    
    override suspend fun getProject(projectId: Uuid): ProjectResponse {
        return try {
            tr {
                val javaUuid = projectId.toJavaUuid()

                val row = Projects.selectAll()
                    .where { Projects.id eq javaUuid }
                    .singleOrNull()
                    
                if (row == null) {
                    return@tr ProjectResponse(
                        success = false, 
                        error = "Project not found"
                    )
                }
                
                // Get project connections
                val connectionIds = ProjectConnections
                    .selectAll()
                    .where { ProjectConnections.projectId eq javaUuid }
                    .map { it[ProjectConnections.connectionId].value.toKotlinUuid() }
                
                val project = Project(
                    id = projectId,
                    name = row[Projects.name],
                    description = row[Projects.description],
                    parameters = emptyList(), // TODO: Deserialize from JSON
                    connections = emptyList(), // TODO: Load actual connections
                    blockGraph = BlockGraph(emptyList(), emptyList()), // TODO: Deserialize from JSON
                    createdAt = row[Projects.createdAt],
                    updatedAt = row[Projects.updatedAt],
                    version = row[Projects.version]
                )
                
                ProjectResponse(success = true, project = project)
            }
        } catch (e: Exception) {
            ProjectResponse(success = false, error = "Failed to get project: ${e.message}")
        }
    }
    
    override suspend fun updateProject(request: UpdateProjectRequest): ProjectResponse {
        return try {
            tr {
                val now = Clock.System.now()

                val projectId = request.projectId
                val javaUuid = projectId.toJavaUuid()

                Projects.update({ Projects.id eq javaUuid }) {
                    request.name?.let { name -> it[Projects.name] = name }
                    request.description?.let { description -> it[Projects.description] = description }
                    it[updatedAt] = now
                }
                
                // TODO: Update parameters, connections, and block graph
                
                getProject(projectId)
            }
        } catch (e: Exception) {
            ProjectResponse(success = false, error = "Failed to update project: ${e.message}")
        }
    }
    
    override suspend fun deleteProject(projectId: Uuid): SuccessResponse {
        return try {
            tr {
                val javaUuid = projectId.toJavaUuid()
                // Delete project connections first
                ProjectConnections.deleteWhere { 
                    ProjectConnections.projectId eq javaUuid
                }
                
                // Delete message templates
                MessageTemplates.deleteWhere { 
                    MessageTemplates.projectId eq javaUuid
                }
                
                // Delete the project
                Projects.deleteWhere { 
                    Projects.id eq javaUuid
                }
            }
            SuccessResponse(success = true, message = "Project deleted successfully")
        } catch (e: Exception) {
            SuccessResponse(success = false, error = "Failed to delete project: ${e.message}")
        }
    }
    
    override suspend fun listProjects(request: ListProjectsRequest): ProjectListResponse {
        return try {
            tr {
                var query = Projects.selectAll()
                
                // Apply search filter
                request.search?.let { search ->
                    query = query.andWhere { 
                        Projects.name.lowerCase() like "%${search.lowercase()}%" or
                        (Projects.description.lowerCase() like "%${search.lowercase()}%")
                    }
                }
                
                // Apply sorting
                query = when (request.sortBy) {
                    ProjectSortBy.NAME -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.name to ExposedSortOrder.ASC)
                        } else {
                            query.orderBy(Projects.name, ExposedSortOrder.DESC)
                        }
                    }
                    ProjectSortBy.CREATED_AT -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.createdAt, ExposedSortOrder.ASC)
                        } else {
                            query.orderBy(Projects.createdAt, ExposedSortOrder.DESC)
                        }
                    }
                    ProjectSortBy.UPDATED_AT -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.updatedAt, ExposedSortOrder.ASC)
                        } else {
                            query.orderBy(Projects.updatedAt, ExposedSortOrder.DESC)
                        }
                    }
                }
                
                // Apply pagination
                val total = query.count()
                val rows = query.limit(request.limit).offset(request.offset.toLong())
                
                val projects = rows.map { row ->
                    Project(
                        id = row[Projects.id].value.toKotlinUuid(),
                        name = row[Projects.name],
                        description = row[Projects.description],
                        parameters = emptyList(), // TODO: Deserialize
                        connections = emptyList(), // TODO: Load connections
                        blockGraph = BlockGraph(emptyList(), emptyList()), // TODO: Deserialize
                        createdAt = row[Projects.createdAt],
                        updatedAt = row[Projects.updatedAt],
                        version = row[Projects.version]
                    )
                }
                
                ProjectListResponse(
                    success = true,
                    projects = projects.toList(),
                    total = total.toInt()
                )
            }
        } catch (e: Exception) {
            ProjectListResponse(success = false, error = "Failed to list projects: ${e.message}")
        }
    }
    
    override suspend fun validateProject(project: Project): ValidationResponse {
        val errors = mutableListOf<ValidationError>()
        
        // Validate project name
        if (project.name.isBlank()) {
            errors.add(ValidationError("name", "Project name cannot be empty", "REQUIRED"))
        }
        
        // Validate block graph (basic validation)
        if (project.blockGraph.blocks.isEmpty()) {
            errors.add(ValidationError("blockGraph", "Project must have at least one block", "MIN_COUNT"))
        }
        
        return ValidationResponse(isValid = errors.isEmpty(), errors = errors)
    }
    
    override suspend fun getProjectTemplates(): ProjectTemplateListResponse {
        return ProjectTemplateListResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun createProjectFromTemplate(request: CreateFromTemplateRequest): ProjectResponse {
        return ProjectResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun createMessageTemplate(request: CreateMessageTemplateRequest): MessageTemplateResponse {
        return MessageTemplateResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun updateMessageTemplate(request: UpdateMessageTemplateRequest): MessageTemplateResponse {
        return MessageTemplateResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun deleteMessageTemplate(templateId: Uuid): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun listMessageTemplates(projectId: Uuid): MessageTemplateListResponse {
        return MessageTemplateListResponse(success = false, error = "Not implemented")
    }
}
