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
import com.github.mr3zee.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManageScreen(
    viewModel: TeamManageViewModel,
    onBack: () -> Unit,
) {
    val members by viewModel.members.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showInviteDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<TeamMembership?>(null) }
    var inviteToCancel by remember { mutableStateOf<TeamInvite?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Team") },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Text("Back") }
                },
            )
        },
        modifier = Modifier.testTag("team_manage_screen"),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                // Error
                if (error != null) {
                    item {
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                // Members section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.testTag("invite_user_button"),
                        ) {
                            Text("Invite User")
                        }
                    }
                }
                items(members, key = { "member-${it.userId.value}" }) { member ->
                    ManageMemberItem(
                        member = member,
                        onToggleRole = {
                            val newRole = if (member.role == TeamRole.TEAM_LEAD) TeamRole.COLLABORATOR else TeamRole.TEAM_LEAD
                            viewModel.updateMemberRole(member.userId.value, newRole)
                        },
                        onRemove = { memberToRemove = member },
                    )
                }

                // Pending invites section
                if (invites.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        Text(
                            "Pending Invites (${invites.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(invites, key = { "invite-${it.id}" }) { invite ->
                        InviteItem(
                            invite = invite,
                            onCancel = { inviteToCancel = invite },
                        )
                    }
                }

                // Join requests section
                if (joinRequests.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        Text(
                            "Join Requests (${joinRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(joinRequests, key = { "request-${it.id}" }) { request ->
                        JoinRequestItem(
                            request = request,
                            onApprove = { viewModel.approveJoinRequest(request.id) },
                            onReject = { viewModel.rejectJoinRequest(request.id) },
                        )
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteUserDialog(
            onDismiss = { showInviteDialog = false },
            onInvite = { userIdStr ->
                viewModel.inviteUser(UserId(userIdStr))
                showInviteDialog = false
            },
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove Member") },
            text = { Text("Are you sure you want to remove ${member.username}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.userId.value)
                    memberToRemove = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            },
        )
    }

    inviteToCancel?.let { invite ->
        AlertDialog(
            onDismissRequest = { inviteToCancel = null },
            title = { Text("Cancel Invite") },
            text = { Text("Cancel invite for ${invite.invitedUsername}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelInvite(invite.id)
                    inviteToCancel = null
                }) {
                    Text("Cancel Invite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { inviteToCancel = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun ManageMemberItem(
    member: TeamMembership,
    onToggleRole: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("manage_member_${member.userId.value}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            TextButton(onClick = onToggleRole) {
                Text(if (member.role == TeamRole.TEAM_LEAD) "Demote" else "Promote")
            }
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InviteItem(invite: TeamInvite, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("invite_item_${invite.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(invite.invitedUsername, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun JoinRequestItem(request: JoinRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("join_request_item_${request.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(request.username, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = onApprove) { Text("Approve") }
                TextButton(onClick = onReject) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun InviteUserDialog(onDismiss: () -> Unit, onInvite: (String) -> Unit) {
    var userId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite User") },
        text = {
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("User ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("invite_user_id_input"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onInvite(userId) }, enabled = userId.isNotBlank()) {
                Text("Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
