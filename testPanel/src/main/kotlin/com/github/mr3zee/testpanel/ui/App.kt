package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.TestPanelState
import com.github.mr3zee.testpanel.server.TestPanelServer

enum class NavSection(val label: String, val group: String) {
    SERVER("Server", "SERVER"),
    RELEASE_TRIGGER("Release Trigger", "RELEASE WIZARD"),
    PROJECTS("Projects", "TEAMCITY"),
    BUILD_TYPES("Build Configs", "TEAMCITY"),
    BUILD_QUEUE("Build Queue", "TEAMCITY"),
    WEBHOOK_SENDER("Webhook Sender", "TEAMCITY"),
    SLACK_MESSAGES("Messages", "SLACK"),
    GH_REPOS("Repos & Workflows", "GITHUB"),
    GH_RUNS("Workflow Runs", "GITHUB"),
    GH_RELEASES("Releases", "GITHUB"),
}

@Composable
fun TestPanelApp(
    state: TestPanelState,
    server: TestPanelServer,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
    ) {
        var selectedSection by remember { mutableStateOf(NavSection.SERVER) }

        Row(Modifier.fillMaxSize()) {
            // Sidebar
            Column(
                modifier = Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "Test Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    var lastGroup = ""
                    for (section in NavSection.entries) {
                        if (section.group != lastGroup) {
                            lastGroup = section.group
                            Text(
                                section.group,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        val isSelected = section == selectedSection
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSection = section }
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                section.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Server status indicator
                val mockState by state.state.collectAsState()
                val running by server.isRunning.collectAsState()
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                color = if (running) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                    )
                    Text(
                        if (running) "Port ${mockState.serverConfig.port}" else "Stopped",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Main content
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                when (selectedSection) {
                    NavSection.SERVER -> ServerControlPanel(state, server)
                    NavSection.RELEASE_TRIGGER -> ReleaseTriggerPanel(state)
                    NavSection.PROJECTS -> ProjectsPanel(state)
                    NavSection.BUILD_TYPES -> BuildTypesPanel(state)
                    NavSection.BUILD_QUEUE -> BuildQueuePanel(state)
                    NavSection.WEBHOOK_SENDER -> WebhookSenderPanel(state)
                    NavSection.SLACK_MESSAGES -> SlackPanel(state)
                    NavSection.GH_REPOS -> GitHubReposPanel(state)
                    NavSection.GH_RUNS -> GitHubRunsPanel(state)
                    NavSection.GH_RELEASES -> GitHubReleasesPanel(state)
                }
            }
        }
    }
}
