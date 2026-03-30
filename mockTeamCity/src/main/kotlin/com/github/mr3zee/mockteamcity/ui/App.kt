package com.github.mr3zee.mockteamcity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import com.github.mr3zee.mockteamcity.server.MockTeamCityServer

enum class NavSection(val label: String) {
    SERVER("Server"),
    PROJECTS("Projects"),
    BUILD_TYPES("Build Configs"),
    BUILD_QUEUE("Build Queue"),
}

@Composable
fun MockTeamCityApp(
    state: MockTeamCityState,
    server: MockTeamCityServer,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
    ) {
        var selectedSection by remember { mutableStateOf(NavSection.SERVER) }

        Row(Modifier.fillMaxSize()) {
            // Sidebar
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "Mock TeamCity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(8.dp))

                for (section in NavSection.entries) {
                    val isSelected = section == selectedSection
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSection = section }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            section.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

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
                    NavSection.PROJECTS -> ProjectsPanel(state)
                    NavSection.BUILD_TYPES -> BuildTypesPanel(state)
                    NavSection.BUILD_QUEUE -> BuildQueuePanel(state)
                }
            }
        }
    }
}
