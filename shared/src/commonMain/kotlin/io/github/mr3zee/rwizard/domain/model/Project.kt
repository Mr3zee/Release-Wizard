package io.github.mr3zee.rwizard.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: UUID,
    val name: String,
    val description: String,
    val parameters: List<ProjectParameter>,
    val connections: List<Connection>,
    val blockGraph: BlockGraph,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int = 1
)

@Serializable
data class ProjectParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val defaultValue: String? = null,
    val isOptional: Boolean = false,
    val validationRules: List<ValidationRule> = emptyList()
)

@Serializable
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    SECRET,
    URL,
    EMAIL,
    PATH
}

@Serializable
data class ValidationRule(
    val type: ValidationType,
    val value: String,
    val errorMessage: String
)

@Serializable
enum class ValidationType {
    REGEX,
    MIN_LENGTH,
    MAX_LENGTH,
    REQUIRED,
    URL_FORMAT,
    EMAIL_FORMAT
}

@Serializable
data class BlockGraph(
    val blocks: List<Block>,
    val connections: List<BlockConnection>
)

@Serializable
sealed class Block {
    abstract val id: UUID
    abstract val name: String
    abstract val description: String
    abstract val parameters: List<BlockParameter>
    abstract val outputs: List<BlockOutput>
    abstract val timeout: Long? // in seconds
    
    @Serializable
    data class Container(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter> = emptyList(),
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null,
        val childGraph: BlockGraph
    ) : Block()
    
    @Serializable
    data class SlackMessage(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null,
        val channel: String,
        val messageTemplate: String? = null, // null means use project template
        val templateName: String? = null // reference to project template
    ) : Block()
    
    @Serializable
    data class TeamCityBuild(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput>,
        override val timeout: Long? = null,
        val buildConfigId: String,
        val branch: String? = null
    ) : Block()
    
    @Serializable
    data class MavenCentralStatus(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null
    ) : Block()
    
    @Serializable
    data class GitHubAction(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null,
        val repository: String,
        val workflowId: String,
        val ref: String = "main"
    ) : Block()
    
    @Serializable
    data class GitHubRelease(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null,
        val repository: String,
        val tagPattern: String,
        val releaseBranch: String = "main"
    ) : Block()
    
    @Serializable
    data class UserAction(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val parameters: List<BlockParameter>,
        override val outputs: List<BlockOutput> = emptyList(),
        override val timeout: Long? = null,
        val instructions: String
    ) : Block()
}

@Serializable
data class BlockParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val source: ParameterSource,
    val isOptional: Boolean = false,
    val validationRules: List<ValidationRule> = emptyList()
)

@Serializable
sealed class ParameterSource {
    @Serializable
    data object Manual : ParameterSource()
    
    @Serializable
    data class ProjectParameter(val parameterName: String) : ParameterSource()
    
    @Serializable
    data class BlockOutput(val blockId: UUID, val outputName: String) : ParameterSource()
    
    @Serializable
    data class DefaultValue(val value: String) : ParameterSource()
}

@Serializable
data class BlockOutput(
    val name: String,
    val description: String,
    val type: OutputType
)

@Serializable
enum class OutputType {
    STRING,
    URL,
    FILE_PATH,
    BUILD_NUMBER,
    ARTIFACT_LINK,
    STATUS
}

@Serializable
data class BlockConnection(
    val id: UUID,
    val fromBlockId: UUID,
    val toBlockId: UUID,
    val type: BlockConnectionType
)

@Serializable
enum class BlockConnectionType {
    SEQUENTIAL,
    PARALLEL
}
