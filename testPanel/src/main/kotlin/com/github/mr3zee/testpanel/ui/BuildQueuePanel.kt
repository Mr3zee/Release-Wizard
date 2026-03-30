package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.*

@Composable
fun BuildQueuePanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val builds = mockState.builds

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
            Text("Build Queue", style = MaterialTheme.typography.headlineSmall)
            if (builds.isNotEmpty()) {
                OutlinedButton(onClick = { state.clearBuilds() }) {
                    Text("Clear All")
                }
            }
        }

        // Summary stats
        val queued = builds.count { it.state == TcBuildState.QUEUED }
        val running = builds.count { it.state == TcBuildState.RUNNING }
        val finished = builds.count { it.state == TcBuildState.FINISHED }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("Queued", queued, MaterialTheme.colorScheme.tertiary)
            StatChip("Running", running, MaterialTheme.colorScheme.primary)
            StatChip("Finished", finished, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (builds.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No builds yet. Trigger a build from Release Wizard.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Group top-level builds and their sub-builds
            val topBuilds = builds.filter { it.parentBuildId == null }.sortedByDescending { it.id }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (build in topBuilds) {
                    val subBuilds = builds.filter { it.parentBuildId == build.id }
                    BuildCard(build, subBuilds, state, mockState)
                }

                // Orphan builds (sub-builds without a visible parent — shouldn't happen but be safe)
                val orphans = builds.filter {
                    it.parentBuildId != null && builds.none { p -> p.id == it.parentBuildId }
                }
                for (build in orphans) {
                    BuildCard(build, emptyList(), state, mockState)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
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
private fun BuildCard(
    build: TcBuild,
    subBuilds: List<TcBuild>,
    state: TestPanelState,
    mockState: PanelState,
) {
    val btName = mockState.buildTypes.find { it.id == build.buildTypeId }?.name ?: build.buildTypeId

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
                            btName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        BuildStateBadge(build.state, build.status)
                    }
                    Text(
                        "#${build.number}  |  ID: ${build.id}  |  Type: ${build.buildTypeId}" +
                                (build.branchName?.let { "  |  Branch: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Action buttons
                BuildActions(build, state)
            }

            // Trigger properties
            if (build.triggerProperties.isNotEmpty()) {
                var showProps by remember(build.id) { mutableStateOf(false) }
                TextButton(onClick = { showProps = !showProps }) {
                    Text(
                        if (showProps) "Hide properties (${build.triggerProperties.size})"
                        else "Show properties (${build.triggerProperties.size})",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (showProps) {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        for ((k, v) in build.triggerProperties) {
                            Text(
                                "$k = $v",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            // Sub-builds
            if (subBuilds.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Sub-builds", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                for (sub in subBuilds) {
                    SubBuildRow(sub, state, mockState)
                }
            }
        }
    }
}

@Composable
private fun SubBuildRow(build: TcBuild, state: TestPanelState, mockState: PanelState) {
    val btName = mockState.buildTypes.find { it.id == build.buildTypeId }?.name ?: build.buildTypeId

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
                "$btName #${build.number}",
                style = MaterialTheme.typography.bodySmall,
            )
            BuildStateBadge(build.state, build.status)
        }
        BuildActions(build, state)
    }
}

@Composable
private fun BuildStateBadge(buildState: TcBuildState, status: TcBuildStatus?) {
    val (text, color) = when (buildState) {
        TcBuildState.QUEUED -> "QUEUED" to MaterialTheme.colorScheme.tertiary
        TcBuildState.RUNNING -> "RUNNING" to MaterialTheme.colorScheme.primary
        TcBuildState.FINISHED -> when (status) {
            TcBuildStatus.SUCCESS -> "SUCCESS" to MaterialTheme.colorScheme.primary
            TcBuildStatus.FAILURE -> "FAILURE" to MaterialTheme.colorScheme.error
            else -> "CANCELLED" to MaterialTheme.colorScheme.onSurfaceVariant
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
private fun BuildActions(build: TcBuild, state: TestPanelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (build.state) {
            TcBuildState.QUEUED -> {
                Button(
                    onClick = { state.startBuild(build.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Start", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { state.cancelBuild(build.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
            TcBuildState.RUNNING -> {
                Button(
                    onClick = { state.finishBuild(build.id, TcBuildStatus.SUCCESS) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("SUCCESS", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { state.finishBuild(build.id, TcBuildStatus.FAILURE) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("FAILURE", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { state.cancelBuild(build.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
            TcBuildState.FINISHED -> {
                // No actions for finished builds
            }
        }
    }
}
