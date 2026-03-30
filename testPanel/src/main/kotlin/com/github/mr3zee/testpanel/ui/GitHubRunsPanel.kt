package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.*

@Composable
fun GitHubRunsPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val runs = mockState.ghRuns

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Workflow Runs", style = MaterialTheme.typography.headlineSmall)
            if (runs.isNotEmpty()) {
                OutlinedButton(onClick = { state.clearGhRuns() }) {
                    Text("Clear All")
                }
            }
        }

        // Summary stats
        val queued = runs.count { it.status == GhRunStatus.QUEUED }
        val inProgress = runs.count { it.status == GhRunStatus.IN_PROGRESS }
        val completed = runs.count { it.status == GhRunStatus.COMPLETED }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GhRunStatChip("Queued", queued, MaterialTheme.colorScheme.tertiary)
            GhRunStatChip("In Progress", inProgress, MaterialTheme.colorScheme.primary)
            GhRunStatChip("Completed", completed, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (runs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No runs yet. Trigger a workflow dispatch from Release Wizard.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (run in runs.sortedByDescending { it.id }) {
                    GhRunCard(run, state)
                }
            }
        }
    }
}

@Composable
private fun GhRunStatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            "$label: $count",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun GhRunCard(run: GhWorkflowRun, state: TestPanelState) {
    var showJobs by remember(run.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            run.workflowPath,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        GhRunStatusBadge(run.status, run.conclusion)
                    }
                    Text(
                        "Run #${run.id}  |  Repo: ${run.repoKey}" +
                                (run.ref?.let { "  |  Ref: $it" } ?: "") +
                                "  |  ${run.createdAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Action buttons
                GhRunActions(run, state)
            }

            // Jobs section
            if (run.jobs.isNotEmpty()) {
                TextButton(onClick = { showJobs = !showJobs }) {
                    Text(
                        if (showJobs) "Hide jobs (${run.jobs.size})"
                        else "Show jobs (${run.jobs.size})",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (showJobs) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Jobs", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    for (job in run.jobs) {
                        GhJobRow(run.id, job, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun GhRunStatusBadge(status: GhRunStatus, conclusion: GhRunConclusion?) {
    val (text, color) = when (status) {
        GhRunStatus.QUEUED -> "QUEUED" to MaterialTheme.colorScheme.tertiary
        GhRunStatus.IN_PROGRESS -> "IN PROGRESS" to MaterialTheme.colorScheme.primary
        GhRunStatus.COMPLETED -> when (conclusion) {
            GhRunConclusion.SUCCESS -> "SUCCESS" to MaterialTheme.colorScheme.primary
            GhRunConclusion.FAILURE -> "FAILURE" to MaterialTheme.colorScheme.error
            GhRunConclusion.CANCELLED -> "CANCELLED" to MaterialTheme.colorScheme.onSurfaceVariant
            GhRunConclusion.TIMED_OUT -> "TIMED OUT" to MaterialTheme.colorScheme.error
            null -> "COMPLETED" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun GhRunActions(run: GhWorkflowRun, state: TestPanelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (run.status) {
            GhRunStatus.QUEUED -> {
                Button(
                    onClick = { state.startGhRun(run.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Start", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { state.cancelGhRun(run.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
            GhRunStatus.IN_PROGRESS -> {
                Button(
                    onClick = { state.completeGhRun(run.id, GhRunConclusion.SUCCESS) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("SUCCESS", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { state.completeGhRun(run.id, GhRunConclusion.FAILURE) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("FAILURE", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { state.cancelGhRun(run.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
            GhRunStatus.COMPLETED -> {
                // No actions for completed runs
            }
        }
    }
}

@Composable
private fun GhJobRow(runId: Int, job: GhJob, state: TestPanelState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                job.name,
                style = MaterialTheme.typography.bodySmall,
            )
            GhRunStatusBadge(job.status, job.conclusion)
        }
        GhJobActions(runId, job, state)
    }
}

@Composable
private fun GhJobActions(runId: Int, job: GhJob, state: TestPanelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (job.status) {
            GhRunStatus.QUEUED -> {
                Button(
                    onClick = { state.updateGhJob(runId, job.id, GhRunStatus.IN_PROGRESS, null) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("Start", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { state.updateGhJob(runId, job.id, GhRunStatus.COMPLETED, GhRunConclusion.CANCELLED) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
            GhRunStatus.IN_PROGRESS -> {
                Button(
                    onClick = { state.updateGhJob(runId, job.id, GhRunStatus.COMPLETED, GhRunConclusion.SUCCESS) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("OK", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { state.updateGhJob(runId, job.id, GhRunStatus.COMPLETED, GhRunConclusion.FAILURE) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("FAIL", style = MaterialTheme.typography.labelSmall)
                }
            }
            GhRunStatus.COMPLETED -> {
                // No actions for completed jobs
            }
        }
    }
}
