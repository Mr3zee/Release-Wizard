package io.github.mr3zee.rwizard.services

import io.github.mr3zee.rwizard.api.*
import io.github.mr3zee.rwizard.database.*
import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder as ExposedSortOrder

class ProjectServiceImpl : ProjectService {
    
    override suspend fun createProject(request: CreateProjectRequest): ProjectResponse {
        return try {
            transaction {
                val projectId = java.util.UUID.randomUUID()
                val now = Clock.System.now()
                
                val projectUUID = UUID(projectId.toString())
                val project = Project(
                    id = projectUUID,
                    name = request.name,
                    description = request.description,
                    parameters = request.parameters,
                    connections = emptyList(), // Will be populated from join table
                    blockGraph = request.blockGraph,
                    createdAt = now,
                    updatedAt = now
                )
                
                Projects.insert {
                    it[id] = projectId
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
                        it[projectId] = projectId
                        it[connectionId] = java.util.UUID.fromString(connectionId.value)
                    }
                }
                
                ProjectResponse(success = true, project = project)
            }
        } catch (e: Exception) {
            ProjectResponse(success = false, error = "Failed to create project: ${e.message}")
        }
    }
    
    override suspend fun getProject(projectId: UUID): ProjectResponse {
        return try {
            transaction {
                val row = Projects.selectAll()
                    .where { Projects.id eq java.util.UUID.fromString(projectId.value) }
                    .singleOrNull()
                    
                if (row == null) {
                    return@transaction ProjectResponse(
                        success = false, 
                        error = "Project not found"
                    )
                }
                
                // Get project connections
                val connectionIds = ProjectConnections
                    .selectAll()
                    .where { ProjectConnections.projectId eq java.util.UUID.fromString(projectId.value) }
                    .map { UUID(it[ProjectConnections.connectionId].toString()) }
                
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
            transaction {
                val now = Clock.System.now()
                
                Projects.update({ Projects.id eq java.util.UUID.fromString(request.projectId.value) }) {
                    request.name?.let { name -> it[Projects.name] = name }
                    request.description?.let { description -> it[Projects.description] = description }
                    it[updatedAt] = now
                }
                
                // TODO: Update parameters, connections, and block graph
                
                getProject(request.projectId)
            }
        } catch (e: Exception) {
            ProjectResponse(success = false, error = "Failed to update project: ${e.message}")
        }
    }
    
    override suspend fun deleteProject(projectId: UUID): SuccessResponse {
        return try {
            transaction {
                // Delete project connections first
                ProjectConnections.deleteWhere { 
                    ProjectConnections.projectId eq java.util.UUID.fromString(projectId.value) 
                }
                
                // Delete message templates
                MessageTemplates.deleteWhere { 
                    MessageTemplates.projectId eq java.util.UUID.fromString(projectId.value) 
                }
                
                // Delete the project
                Projects.deleteWhere { 
                    Projects.id eq java.util.UUID.fromString(projectId.value) 
                }
            }
            SuccessResponse(success = true, message = "Project deleted successfully")
        } catch (e: Exception) {
            SuccessResponse(success = false, error = "Failed to delete project: ${e.message}")
        }
    }
    
    override suspend fun listProjects(request: ListProjectsRequest): ProjectListResponse {
        return try {
            transaction {
                var query = Projects.selectAll()
                
                // Apply search filter
                request.search?.let { search ->
                    query = query.andWhere { 
                        Projects.name.lowerCase() like "%${search.lowercase()}%" or
                        Projects.description.lowerCase() like "%${search.lowercase()}%"
                    }
                }
                
                // Apply sorting
                query = when (request.sortBy) {
                    ProjectSortBy.NAME -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.name, SortOrder.ASC)
                        } else {
                            query.orderBy(Projects.name, SortOrder.DESC)
                        }
                    }
                    ProjectSortBy.CREATED_AT -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.createdAt, SortOrder.ASC)
                        } else {
                            query.orderBy(Projects.createdAt, SortOrder.DESC)
                        }
                    }
                    ProjectSortBy.UPDATED_AT -> {
                        if (request.sortOrder == SortOrder.ASC) {
                            query.orderBy(Projects.updatedAt, SortOrder.ASC)
                        } else {
                            query.orderBy(Projects.updatedAt, SortOrder.DESC)
                        }
                    }
                }
                
                // Apply pagination
                val total = query.count()
                val rows = query.limit(request.limit, request.offset.toLong()).toList()
                
                val projects = rows.map { row ->
                    Project(
                        id = UUID(row[Projects.id].toString()),
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
                    projects = projects,
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
    
    override suspend fun deleteMessageTemplate(templateId: UUID): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun listMessageTemplates(projectId: UUID): MessageTemplateListResponse {
        return MessageTemplateListResponse(success = false, error = "Not implemented")
    }
}
