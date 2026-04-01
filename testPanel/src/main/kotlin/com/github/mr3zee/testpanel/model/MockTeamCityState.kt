package com.github.mr3zee.testpanel.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class PanelState(
    val projects: List<TcProject> = listOf(TcProject("_Root", "<Root project>", null)),
    val buildTypes: List<TcBuildType> = emptyList(),
    val builds: List<TcBuild> = emptyList(),
    val nextBuildId: Int = 1,
    val buildNumberCounters: Map<String, Int> = emptyMap(),
    val serverConfig: ServerConfig = ServerConfig(),
    val slackMessages: List<SlackMessage> = emptyList(),
    val nextSlackMessageId: Int = 1,
    val ghRepos: List<GhRepo> = emptyList(),
    val ghWorkflows: Map<String, List<GhWorkflow>> = emptyMap(),
    val nextGhWorkflowId: Int = 1,
    val ghRuns: List<GhWorkflowRun> = emptyList(),
    val nextGhRunId: Int = 1,
    val nextGhJobId: Int = 1,
    val ghReleases: List<GhRelease> = emptyList(),
    val nextGhReleaseId: Int = 1,
    val webhookSendHistory: List<WebhookSendRecord> = emptyList(),
    val triggerHistory: List<TriggerRecord> = emptyList(),
)

class TestPanelState {
    private val _state = MutableStateFlow(PanelState())
    val state: StateFlow<PanelState> = _state.asStateFlow()

    // Not persisted — transient UI state
    private val _requestLog = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    val requestLog: StateFlow<List<RequestLogEntry>> = _requestLog.asStateFlow()

    fun loadState(saved: PanelState) {
        _state.value = saved
    }

    fun currentState(): PanelState = _state.value

    // --- Server config ---

    fun updateServerConfig(config: ServerConfig) {
        _state.update { it.copy(serverConfig = config) }
    }

    // --- Projects ---

    fun addProject(project: TcProject) {
        _state.update { it.copy(projects = it.projects + project) }
    }

    fun removeProject(projectId: String) {
        _state.update { state ->
            val removedBtIds = state.buildTypes
                .filter { it.projectId == projectId }
                .map { it.id }
                .toSet()
            state.copy(
                projects = state.projects.filter { it.id != projectId },
                buildTypes = state.buildTypes.filter { it.projectId != projectId },
                builds = state.builds.filter { it.buildTypeId !in removedBtIds },
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

    // --- Slack ---

    fun addSlackMessage(text: String, channel: String = ""): SlackMessage {
        var msg: SlackMessage? = null
        _state.update { state ->
            val m = SlackMessage(
                id = state.nextSlackMessageId,
                text = text,
                channel = channel,
                receivedAt = formatTimestamp(),
            )
            msg = m
            state.copy(
                slackMessages = state.slackMessages + m,
                nextSlackMessageId = state.nextSlackMessageId + 1,
            )
        }
        return msg ?: error("SlackMessage was not created")
    }

    fun getSlackMessages(): List<SlackMessage> = _state.value.slackMessages

    fun clearSlackMessages() {
        _state.update { it.copy(slackMessages = emptyList()) }
    }

    // --- GitHub Repos ---

    fun addGhRepo(repo: GhRepo) {
        _state.update { it.copy(ghRepos = it.ghRepos + repo) }
    }

    fun removeGhRepo(owner: String, repo: String) {
        val key = "$owner/$repo"
        _state.update { state ->
            state.copy(
                ghRepos = state.ghRepos.filter { "${it.owner}/${it.repo}" != key },
                ghWorkflows = state.ghWorkflows - key,
                ghRuns = state.ghRuns.filter { it.repoKey != key },
                ghReleases = state.ghReleases.filter { it.repoKey != key },
            )
        }
    }

    // --- GitHub Workflows ---

    fun addGhWorkflow(repoKey: String, workflow: GhWorkflow) {
        _state.update { state ->
            val current = state.ghWorkflows[repoKey].orEmpty()
            val wf = workflow.copy(id = state.nextGhWorkflowId)
            state.copy(
                ghWorkflows = state.ghWorkflows + (repoKey to (current + wf)),
                nextGhWorkflowId = state.nextGhWorkflowId + 1,
            )
        }
    }

    fun updateGhWorkflow(repoKey: String, workflowId: Int, updated: GhWorkflow) {
        _state.update { state ->
            val current = state.ghWorkflows[repoKey].orEmpty()
            state.copy(
                ghWorkflows = state.ghWorkflows + (repoKey to current.map { if (it.id == workflowId) updated else it }),
            )
        }
    }

    fun removeGhWorkflow(repoKey: String, workflowId: Int) {
        _state.update { state ->
            val current = state.ghWorkflows[repoKey].orEmpty()
            val removedPath = current.find { it.id == workflowId }?.path
            state.copy(
                ghWorkflows = state.ghWorkflows + (repoKey to current.filter { it.id != workflowId }),
                ghRuns = if (removedPath != null) {
                    state.ghRuns.filter { !(it.repoKey == repoKey && it.workflowPath == removedPath) }
                } else state.ghRuns,
            )
        }
    }

    // --- GitHub Runs ---

    fun triggerGhRun(repoKey: String, workflowPath: String, ref: String?): GhWorkflowRun {
        var run: GhWorkflowRun? = null
        _state.update { state ->
            val defaultJobs = listOf(
                GhJob(id = state.nextGhJobId, name = "build"),
                GhJob(id = state.nextGhJobId + 1, name = "test"),
            )
            val r = GhWorkflowRun(
                id = state.nextGhRunId,
                workflowPath = workflowPath,
                repoKey = repoKey,
                ref = ref,
                createdAt = formatTimestamp(),
                jobs = defaultJobs,
            )
            run = r
            state.copy(
                ghRuns = state.ghRuns + r,
                nextGhRunId = state.nextGhRunId + 1,
                nextGhJobId = state.nextGhJobId + defaultJobs.size,
            )
        }
        return run ?: error("GhWorkflowRun was not created")
    }

    fun startGhRun(runId: Int) {
        _state.update { state ->
            state.copy(ghRuns = state.ghRuns.map { run ->
                if (run.id == runId && run.status == GhRunStatus.QUEUED) {
                    run.copy(
                        status = GhRunStatus.IN_PROGRESS,
                        jobs = run.jobs.map { it.copy(status = GhRunStatus.IN_PROGRESS, startedAt = formatTimestamp()) },
                    )
                } else run
            })
        }
    }

    fun completeGhRun(runId: Int, conclusion: GhRunConclusion) {
        _state.update { state ->
            state.copy(ghRuns = state.ghRuns.map { run ->
                if (run.id == runId && run.status == GhRunStatus.IN_PROGRESS) {
                    run.copy(
                        status = GhRunStatus.COMPLETED,
                        conclusion = conclusion,
                        jobs = run.jobs.map { job ->
                            if (job.status != GhRunStatus.COMPLETED) {
                                job.copy(status = GhRunStatus.COMPLETED, conclusion = conclusion, completedAt = formatTimestamp())
                            } else job
                        },
                    )
                } else run
            })
        }
    }

    fun cancelGhRun(runId: Int) {
        _state.update { state ->
            state.copy(ghRuns = state.ghRuns.map { run ->
                if (run.id == runId && run.status != GhRunStatus.COMPLETED) {
                    run.copy(
                        status = GhRunStatus.COMPLETED,
                        conclusion = GhRunConclusion.CANCELLED,
                        jobs = run.jobs.map { job ->
                            if (job.status != GhRunStatus.COMPLETED) {
                                job.copy(status = GhRunStatus.COMPLETED, conclusion = GhRunConclusion.CANCELLED, completedAt = formatTimestamp())
                            } else job
                        },
                    )
                } else run
            })
        }
    }

    fun updateGhJob(runId: Int, jobId: Int, status: GhRunStatus, conclusion: GhRunConclusion?) {
        _state.update { state ->
            state.copy(ghRuns = state.ghRuns.map { run ->
                if (run.id == runId) {
                    run.copy(jobs = run.jobs.map { job ->
                        if (job.id == jobId) {
                            job.copy(
                                status = status,
                                conclusion = conclusion,
                                startedAt = job.startedAt ?: if (status == GhRunStatus.IN_PROGRESS) formatTimestamp() else null,
                                completedAt = if (status == GhRunStatus.COMPLETED) formatTimestamp() else null,
                            )
                        } else job
                    })
                } else run
            })
        }
    }

    fun clearGhRuns() {
        _state.update { it.copy(ghRuns = emptyList()) }
    }

    // --- GitHub Releases ---

    fun createGhRelease(repoKey: String, tagName: String, name: String, body: String, draft: Boolean, prerelease: Boolean): GhRelease {
        var release: GhRelease? = null
        _state.update { state ->
            val r = GhRelease(
                id = state.nextGhReleaseId,
                repoKey = repoKey,
                tagName = tagName,
                name = name,
                body = body,
                draft = draft,
                prerelease = prerelease,
                htmlUrl = "http://localhost:8111/repos/$repoKey/releases/tag/$tagName",
            )
            release = r
            state.copy(
                ghReleases = state.ghReleases + r,
                nextGhReleaseId = state.nextGhReleaseId + 1,
            )
        }
        return release ?: error("GhRelease was not created")
    }

    fun clearGhReleases() {
        _state.update { it.copy(ghReleases = emptyList()) }
    }

    // --- Webhook Sender ---

    fun addWebhookSendRecord(record: WebhookSendRecord) {
        _state.update { state ->
            state.copy(webhookSendHistory = (state.webhookSendHistory + record).takeLast(100))
        }
    }

    fun clearWebhookHistory() {
        _state.update { it.copy(webhookSendHistory = emptyList()) }
    }

    // --- Release Trigger ---

    fun addTriggerRecord(record: TriggerRecord) {
        _state.update { state ->
            state.copy(triggerHistory = (state.triggerHistory + record).takeLast(100))
        }
    }

    fun clearTriggerHistory() {
        _state.update { it.copy(triggerHistory = emptyList()) }
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

private fun formatTimestamp(): String {
    return java.time.Instant.now().toString()
}
