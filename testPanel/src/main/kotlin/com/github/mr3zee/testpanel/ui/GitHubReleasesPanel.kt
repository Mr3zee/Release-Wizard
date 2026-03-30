package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.GhRelease
import com.github.mr3zee.testpanel.model.TestPanelState

@Composable
fun GitHubReleasesPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val releases = mockState.ghReleases

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
            Text("Releases", style = MaterialTheme.typography.headlineSmall)
            if (releases.isNotEmpty()) {
                OutlinedButton(onClick = { state.clearGhReleases() }) {
                    Text("Clear All")
                }
            }
        }

        if (releases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No releases created yet.",
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
                for (release in releases.sortedByDescending { it.id }) {
                    ReleaseCard(release)
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: GhRelease) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            release.tagName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (release.draft) {
                            ReleaseBadge("DRAFT", MaterialTheme.colorScheme.tertiary)
                        }
                        if (release.prerelease) {
                            ReleaseBadge("PRE-RELEASE", MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(
                        "Name: ${release.name}  |  Repo: ${release.repoKey}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (release.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    release.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReleaseBadge(text: String, color: androidx.compose.ui.graphics.Color) {
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
