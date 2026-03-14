package com.github.mr3zee.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.model.TeamRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    viewModel: TeamDetailViewModel,
    onManage: () -> Unit,
    onAuditLog: () -> Unit = {},
    onBack: () -> Unit,
    isTeamLead: Boolean = false,
) {
    val team by viewModel.team.collectAsState()
    val members by viewModel.members.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showLeaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(team?.name ?: "Team") },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier.testTag("leave_team_button"),
                    ) {
                        Text("Leave", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = onAuditLog,
                        modifier = Modifier.testTag("audit_log_button"),
                    ) {
                        Text("Audit Log")
                    }
                    if (isTeamLead) {
                        TextButton(
                            onClick = onManage,
                            modifier = Modifier.testTag("manage_team_button"),
                        ) {
                            Text("Manage")
                        }
                    }
                },
            )
        },
        modifier = Modifier.testTag("team_detail_screen"),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadDetail() }) { Text("Retry") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                team?.let { t ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(t.name, style = MaterialTheme.typography.titleLarge)
                                if (t.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${members.size} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Members",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                items(members, key = { it.userId.value }) { member ->
                    MemberItem(member)
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Team") },
            text = { Text("Are you sure you want to leave \"${team?.name ?: "this team"}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveTeam { onBack() }
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MemberItem(member: TeamMembership) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("member_item_${member.userId.value}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(member.username, style = MaterialTheme.typography.titleMedium)
            Text(
                when (member.role) {
                    TeamRole.TEAM_LEAD -> "Lead"
                    TeamRole.COLLABORATOR -> "Collaborator"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
