package com.github.mr3zee.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.*
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManageScreen(
    viewModel: TeamManageViewModel,
    onBack: () -> Unit,
    onTeamDeleted: () -> Unit = {},
) {
    val teamName by viewModel.teamName.collectAsState()
    val teamDescription by viewModel.teamDescription.collectAsState()
    val members by viewModel.members.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isInviting by viewModel.isInviting.collectAsState()
    val inviteError by viewModel.inviteError.collectAsState()
    val inviteSuccess by viewModel.inviteSuccess.collectAsState()

    var editName by remember(teamName) { mutableStateOf(teamName) }
    var editDescription by remember(teamDescription) { mutableStateOf(teamDescription) }
    val hasEditChanges = editName != teamName || editDescription != teamDescription

    var showInviteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<TeamMembership?>(null) }
    var inviteToCancel by remember { mutableStateOf<TeamInvite?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)
    val resolvedError = error?.resolve()

    // Show errors via snackbar with dismiss
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = dismissLabel,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packStringResource(Res.string.teams_manage_title)) },
                navigationIcon = {
                    RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("team_manage_screen"),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Edit team section
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Text(packStringResource(Res.string.teams_edit_team), style = AppTypography.heading)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = packStringResource(Res.string.teams_team_name),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_team_name"),
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = packStringResource(Res.string.teams_description_optional),
                            modifier = Modifier.fillMaxWidth().testTag("edit_team_description"),
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwButton(
                            onClick = { viewModel.updateTeam(editName, editDescription) },
                            variant = RwButtonVariant.Ghost,
                            enabled = hasEditChanges && editName.isNotBlank(),
                            modifier = Modifier.testTag("save_team_button"),
                        ) {
                            Text(packStringResource(Res.string.common_save))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.widthIn(max = 1200.dp).padding(vertical = Spacing.sm))
                }

                // Members section
                item {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(packStringResource(Res.string.teams_members_count, members.size), style = AppTypography.heading)
                        RwButton(
                            onClick = { showInviteDialog = true },
                            variant = RwButtonVariant.Ghost,
                            modifier = Modifier.testTag("invite_user_button"),
                        ) {
                            Text(packStringResource(Res.string.teams_invite_user))
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
                        modifier = Modifier.widthIn(max = 1200.dp),
                    )
                }

                // Pending invites section
                if (invites.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    item {
                        Text(
                            packStringResource(Res.string.teams_pending_invites_count, invites.size),
                            style = AppTypography.heading,
                            modifier = Modifier
                                .widthIn(max = 1200.dp)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    items(invites, key = { "invite-${it.id}" }) { invite ->
                        InviteItem(
                            invite = invite,
                            onCancel = { inviteToCancel = invite },
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                    }
                }

                // Join requests section
                if (joinRequests.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    item {
                        Text(
                            packStringResource(Res.string.teams_join_requests_count, joinRequests.size),
                            style = AppTypography.heading,
                            modifier = Modifier
                                .widthIn(max = 1200.dp)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    items(joinRequests, key = { "request-${it.id}" }) { request ->
                        JoinRequestItem(
                            request = request,
                            onApprove = { viewModel.approveJoinRequest(request.id) },
                            onReject = { viewModel.rejectJoinRequest(request.id) },
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                    }
                }

                // Delete team (danger zone)
                item {
                    HorizontalDivider(modifier = Modifier.widthIn(max = 1200.dp).padding(vertical = Spacing.sm))
                    Row(
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        RwButton(
                            onClick = { showDeleteDialog = true },
                            variant = RwButtonVariant.Ghost,
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("delete_team_button"),
                        ) {
                            Text(packStringResource(Res.string.teams_delete_team))
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(packStringResource(Res.string.teams_delete_team)) },
            text = { Text(packStringResource(Res.string.teams_delete_confirmation, teamName)) },
            confirmButton = {
                RwButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTeam { onTeamDeleted() }
                    },
                    variant = RwButtonVariant.Ghost,
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Text(packStringResource(Res.string.common_delete))
                }
            },
            dismissButton = {
                RwButton(onClick = { showDeleteDialog = false }, variant = RwButtonVariant.Ghost) {
                    Text(packStringResource(Res.string.common_cancel))
                }
            },
        )
    }

    // Close invite dialog on success
    LaunchedEffect(inviteSuccess) {
        if (inviteSuccess) {
            showInviteDialog = false
            viewModel.clearInviteState()
        }
    }

    if (showInviteDialog) {
        InviteUserDialog(
            isInviting = isInviting,
            error = inviteError,
            onDismiss = {
                showInviteDialog = false
                viewModel.clearInviteState()
            },
            onClearError = { viewModel.clearInviteState() },
            onInvite = { username -> viewModel.inviteUser(username) },
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(packStringResource(Res.string.teams_remove)) },
            text = { Text(packStringResource(Res.string.teams_remove_member_confirmation, member.username)) },
            confirmButton = {
                RwButton(onClick = {
                    viewModel.removeMember(member.userId.value)
                    memberToRemove = null
                }, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
                    Text(packStringResource(Res.string.teams_remove))
                }
            },
            dismissButton = {
                RwButton(onClick = { memberToRemove = null }, variant = RwButtonVariant.Ghost) { Text(packStringResource(Res.string.common_cancel)) }
            },
        )
    }

    inviteToCancel?.let { invite ->
        AlertDialog(
            onDismissRequest = { inviteToCancel = null },
            title = { Text(packStringResource(Res.string.teams_revoke_invite)) },
            text = { Text(packStringResource(Res.string.teams_cancel_invite_confirmation, invite.invitedUsername)) },
            confirmButton = {
                RwButton(onClick = {
                    viewModel.cancelInvite(invite.id)
                    inviteToCancel = null
                }, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
                    Text(packStringResource(Res.string.teams_revoke_invite))
                }
            },
            dismissButton = {
                RwButton(onClick = { inviteToCancel = null }, variant = RwButtonVariant.Ghost) { Text(packStringResource(Res.string.teams_keep)) }
            },
        )
    }
}

@Composable
private fun ManageMemberItem(
    member: TeamMembership,
    onToggleRole: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        testTag = "manage_member_${member.userId.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                member.username,
                style = AppTypography.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                member.role.displayName(),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RwButton(onClick = onToggleRole, variant = RwButtonVariant.Ghost) {
            Text(if (member.role == TeamRole.TEAM_LEAD) packStringResource(Res.string.teams_demote) else packStringResource(Res.string.teams_promote))
        }
        RwButton(onClick = onRemove, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
            Text(packStringResource(Res.string.teams_remove))
        }
    }
}

@Composable
private fun InviteItem(
    invite: TeamInvite,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        testTag = "invite_item_${invite.id}",
        modifier = modifier,
    ) {
        Text(
            invite.invitedUsername,
            style = AppTypography.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RwButton(onClick = onCancel, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
            Text(packStringResource(Res.string.teams_revoke_invite))
        }
    }
}

@Composable
private fun JoinRequestItem(
    request: JoinRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        testTag = "join_request_item_${request.id}",
        modifier = modifier,
    ) {
        Text(
            request.username,
            style = AppTypography.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row {
            RwButton(onClick = onApprove, variant = RwButtonVariant.Ghost) { Text(packStringResource(Res.string.common_approve)) }
            RwButton(onClick = onReject, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
                Text(packStringResource(Res.string.teams_reject))
            }
        }
    }
}

@Composable
private fun InviteUserDialog(
    isInviting: Boolean,
    error: com.github.mr3zee.util.UiMessage?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onInvite: (String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    val resolvedError = error?.resolve()
    AlertDialog(
        onDismissRequest = { if (!isInviting) onDismiss() },
        title = { Text(packStringResource(Res.string.teams_invite_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RwTextField(
                    value = username,
                    onValueChange = { username = it; onClearError() },
                    label = packStringResource(Res.string.teams_invite_username_label),
                    placeholder = packStringResource(Res.string.teams_invite_username_placeholder),
                    singleLine = true,
                    isError = resolvedError != null,
                    modifier = Modifier.fillMaxWidth().testTag("invite_user_id_input"),
                )
                if (resolvedError != null) {
                    Text(
                        resolvedError,
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("invite_error_text"),
                    )
                }
            }
        },
        confirmButton = {
            RwButton(
                onClick = { onInvite(username.trim()) },
                variant = RwButtonVariant.Primary,
                enabled = username.isNotBlank() && !isInviting,
            ) {
                if (isInviting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.teams_invite))
                }
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost, enabled = !isInviting) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}
