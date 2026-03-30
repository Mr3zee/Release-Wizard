package com.github.mr3zee.mockteamcity.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class MockState(
    val projects: List<TcProject> = listOf(TcProject("_Root", "<Root project>", null)),
    val buildTypes: List<TcBuildType> = emptyList(),
    val builds: List<TcBuild> = emptyList(),
    val nextBuildId: Int = 1,
    val buildNumberCounters: Map<String, Int> = emptyMap(),
    val serverConfig: MockServerConfig = MockServerConfig(),
)

class MockTeamCityState {
    private val _state = MutableStateFlow(MockState())
    val state: StateFlow<MockState> = _state.asStateFlow()

    // Not persisted — transient UI state
    private val _requestLog = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    val requestLog: StateFlow<List<RequestLogEntry>> = _requestLog.asStateFlow()

    fun loadState(saved: MockState) {
        _state.value = saved
    }

    fun currentState(): MockState = _state.value

    // --- Server config ---

    fun updateServerConfig(config: MockServerConfig) {
        _state.update { it.copy(serverConfig = config) }
    }

    // --- Projects ---

    fun addProject(project: TcProject) {
        _state.update { it.copy(projects = it.projects + project) }
    }

    fun removeProject(projectId: String) {
        _state.update { state ->
            state.copy(
                projects = state.projects.filter { it.id != projectId },
                buildTypes = state.buildTypes.filter { it.projectId != projectId },
            )
        }
    }

    fun updateProject(projectId: String, updated: TcProject) {
        _state.update { state ->
            state.copy(projects = state.projects.map { if (it.id == projectId) updated else it })
        }
    }

    // --- Build Types ---

    fun addBuildType(buildType: TcBuildType) {
        _state.update { it.copy(buildTypes = it.buildTypes + buildType) }
    }

    fun removeBuildType(buildTypeId: String) {
        _state.update { state ->
            state.copy(buildTypes = state.buildTypes.filter { it.id != buildTypeId })
        }
    }

    fun updateBuildType(buildTypeId: String, updated: TcBuildType) {
        _state.update { state ->
            state.copy(buildTypes = state.buildTypes.map { if (it.id == buildTypeId) updated else it })
        }
    }

    // --- Builds ---

    fun triggerBuild(
        buildTypeId: String,
        branchName: String?,
        properties: Map<String, String>,
    ): TcBuild {
        var newBuild: TcBuild? = null
        _state.update { state ->
            val buildId = state.nextBuildId
            val btCounter = (state.buildNumberCounters[buildTypeId] ?: 0) + 1
            val build = TcBuild(
                id = buildId,
                buildTypeId = buildTypeId,
                branchName = branchName,
                state = TcBuildState.QUEUED,
                number = btCounter.toString(),
                triggerProperties = properties,
            )
            newBuild = build

            // Create sub-builds for snapshot dependencies
            val bt = state.buildTypes.find { it.id == buildTypeId }
            val updatedCounters = state.buildNumberCounters.toMutableMap()
            updatedCounters[buildTypeId] = btCounter
            val subBuilds = bt?.snapshotDependencyIds?.mapIndexed { index, depId ->
                val depCounter = (updatedCounters[depId] ?: 0) + 1
                updatedCounters[depId] = depCounter
                TcBuild(
                    id = buildId + 1 + index,
                    buildTypeId = depId,
                    branchName = branchName,
                    state = TcBuildState.QUEUED,
                    number = depCounter.toString(),
                    parentBuildId = buildId,
                )
            }.orEmpty()

            val allNewBuilds = listOf(build) + subBuilds

            state.copy(
                builds = state.builds + allNewBuilds,
                nextBuildId = buildId + 1 + subBuilds.size,
                buildNumberCounters = updatedCounters,
            )
        }
        return newBuild ?: error("Build was not created")
    }

    fun startBuild(buildId: Int) {
        _state.update { state ->
            state.copy(
                builds = state.builds.map { build ->
                    if (build.id == buildId && build.state == TcBuildState.QUEUED) {
                        build.copy(
                            state = TcBuildState.RUNNING,
                            startDate = formatTcTimestamp(),
                        )
                    } else build
                },
            )
        }
    }

    fun finishBuild(buildId: Int, status: TcBuildStatus) {
        _state.update { state ->
            state.copy(
                builds = state.builds.map { build ->
                    if (build.id == buildId && build.state == TcBuildState.RUNNING) {
                        build.copy(
                            state = TcBuildState.FINISHED,
                            status = status,
                            finishDate = formatTcTimestamp(),
                        )
                    } else build
                },
            )
        }
    }

    fun cancelBuild(buildId: Int) {
        _state.update { state ->
            state.copy(
                builds = state.builds.map { build ->
                    if (build.id == buildId && build.state != TcBuildState.FINISHED) {
                        build.copy(
                            state = TcBuildState.FINISHED,
                            status = TcBuildStatus.UNKNOWN,
                            finishDate = formatTcTimestamp(),
                        )
                    } else build
                },
            )
        }
    }

    fun clearBuilds() {
        _state.update { it.copy(builds = emptyList()) }
    }

    // --- Request Log ---

    fun logRequest(entry: RequestLogEntry) {
        _requestLog.update { (it + entry).takeLast(200) }
    }

    fun clearLog() {
        _requestLog.value = emptyList()
    }
}

private val TC_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssZ")

private fun formatTcTimestamp(): String {
    return TC_TIMESTAMP_FORMAT.format(Instant.now().atOffset(ZoneOffset.UTC))
}
