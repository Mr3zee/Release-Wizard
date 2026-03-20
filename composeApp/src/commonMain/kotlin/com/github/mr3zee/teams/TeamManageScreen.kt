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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwDangerZone
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.*
import com.github.mr3zee.components.RwBadge
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
    currentUserId: String? = null,
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
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (hasEditChanges) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }
    var memberToRemove by remember { mutableStateOf<TeamMembership?>(null) }
    var inviteToCancel by remember { mutableStateOf<TeamInvite?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    val isDialogOpen = showDiscardConfirm || showDeleteDialog || showInviteDialog || memberToRemove != null || inviteToCancel != null
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(
            onSave = { if (hasEditChanges && editName.isNotBlank()) viewModel.updateTeam(editName, editDescription) },
            onRefresh = { viewModel.loadAll() },
            hasDialogOpen = isDialogOpen,
        )
    }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(packStringResource(Res.string.teams_manage_title))
                        Text(
                            teamName,
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    RwButton(onClick = handleBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
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
                val loadingDesc = packStringResource(Res.string.loading_team_manage)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                RwInlineConfirmation(
                    visible = showDiscardConfirm,
                    message = packStringResource(Res.string.common_unsaved_message),
                    confirmLabel = packStringResource(Res.string.common_discard),
                    onConfirm = {
                        showDiscardConfirm = false
                        onBack()
                    },
                    onDismiss = { showDiscardConfirm = false },
                    isDestructive = true,
                    testTag = "discard_changes_confirm",
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 80.dp),
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
                        val updatedMessage = packStringResource(Res.string.teams_updated_success, editName)
                        RwButton(
                            onClick = {
                                viewModel.updateTeam(editName, editDescription)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = updatedMessage,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            variant = RwButtonVariant.Primary,
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
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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

                // Invite form below the invite button
                item {
                    InviteUserInlineForm(
                        visible = showInviteDialog,
                        isInviting = isInviting,
                        error = inviteError,
                        onDismiss = {
                            showInviteDialog = false
                            viewModel.clearInviteState()
                        },
                        onClearError = { viewModel.clearInviteState() },
                        onInvite = { username -> viewModel.inviteUser(username) },
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    )
                }
                items(members, key = { "member-${it.userId.value}" }) { member ->
                    Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                        ManageMemberItem(
                            member = member,
                            isCurrentUser = currentUserId != null && member.userId.value == currentUserId,
                            onToggleRole = {
                                val newRole = if (member.role == TeamRole.TEAM_LEAD) TeamRole.COLLABORATOR else TeamRole.TEAM_LEAD
                                viewModel.updateMemberRole(member.userId.value, newRole)
                            },
                            onRemove = { memberToRemove = member },
                        )
                        RwInlineConfirmation(
                            visible = memberToRemove?.userId == member.userId,
                            message = packStringResource(Res.string.teams_remove_member_confirmation, member.username),
                            confirmLabel = packStringResource(Res.string.teams_remove),
                            onConfirm = {
                                viewModel.removeMember(member.userId.value)
                                memberToRemove = null
                            },
                            onDismiss = { memberToRemove = null },
                            testTag = "remove_member_confirm_${member.userId.value}",
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                }

                // Pending invites section
                item {
                    HorizontalDivider(modifier = Modifier.widthIn(max = 1200.dp).padding(vertical = Spacing.sm))
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
                    if (invites.isEmpty()) {
                        item {
                            Text(
                                packStringResource(Res.string.teams_no_pending_invites),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 1200.dp).fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            )
                        }
                    }
                    items(invites, key = { "invite-${it.id}" }) { invite ->
                        Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                            InviteItem(
                                invite = invite,
                                onCancel = { inviteToCancel = invite },
                            )
                            RwInlineConfirmation(
                                visible = inviteToCancel?.id == invite.id,
                                message = packStringResource(Res.string.teams_revoke_invite_confirmation, invite.invitedUsername),
                                confirmLabel = packStringResource(Res.string.teams_revoke_invite),
                                onConfirm = {
                                    viewModel.cancelInvite(invite.id)
                                    inviteToCancel = null
                                },
                                onDismiss = { inviteToCancel = null },
                                testTag = "revoke_invite_confirm_${invite.id}",
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }

                // Join requests section
                item {
                    HorizontalDivider(modifier = Modifier.widthIn(max = 1200.dp).padding(vertical = Spacing.sm))
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
                if (joinRequests.isEmpty()) {
                    item {
                        Text(
                            packStringResource(Res.string.teams_no_join_requests),
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 1200.dp).fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                }
                items(joinRequests, key = { "request-${it.id}" }) { request ->
                    JoinRequestItem(
                        request = request,
                        onApprove = { viewModel.approveJoinRequest(request.id) },
                        onReject = { viewModel.rejectJoinRequest(request.id) },
                        modifier = Modifier.widthIn(max = 1200.dp),
                    )
                }

                // Delete team (danger zone)
                item {
                    RwDangerZone(
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                packStringResource(Res.string.teams_delete_team),
                                style = AppTypography.body,
                            )
                            RwButton(
                                onClick = { showDeleteDialog = true },
                                variant = RwButtonVariant.Danger,
                                modifier = Modifier.testTag("delete_team_button"),
                            ) {
                                Text(packStringResource(Res.string.teams_delete_team))
                            }
                        }
                    }
                    RwInlineConfirmation(
                        visible = showDeleteDialog,
                        message = packStringResource(Res.string.teams_delete_confirmation, teamName),
                        confirmLabel = packStringResource(Res.string.common_delete),
                        onConfirm = {
                            showDeleteDialog = false
                            viewModel.deleteTeam { onTeamDeleted() }
                        },
                        onDismiss = { showDeleteDialog = false },
                        testTag = "delete_team_confirm",
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
                }
            }
        }
    }

    // Delete team confirmation is now shown inline in the danger zone section
    // Remove member confirmation is now shown inline per-item in the members section
    // Revoke invite confirmation is now shown inline per-item in the invites section

    // Close invite form on success
    LaunchedEffect(inviteSuccess) {
        if (inviteSuccess) {
            showInviteDialog = false
            viewModel.clearInviteState()
        }
    }

    // Invite user form is now shown inline above the members section

    } // ProvideShortcutActions
}

@Composable
private fun ManageMemberItem(
    member: TeamMembership,
    isCurrentUser: Boolean = false,
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
                style = AppTypography.subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                member.role.displayName(),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrentUser) {
            RwBadge(
                text = packStringResource(Res.string.teams_you_badge),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "you_badge_${member.userId.value}",
            )
        } else {
            RwButton(
                onClick = onToggleRole,
                variant = RwButtonVariant.Ghost,
                modifier = Modifier.testTag("toggle_role_${member.userId.value}"),
            ) {
                Text(if (member.role == TeamRole.TEAM_LEAD) packStringResource(Res.string.teams_demote_to_collaborator) else packStringResource(Res.string.teams_promote_to_lead))
            }
            RwButton(
                onClick = onRemove,
                variant = RwButtonVariant.Ghost,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("remove_member_${member.userId.value}"),
            ) {
                Text(packStringResource(Res.string.teams_remove))
            }
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
            style = AppTypography.subheading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RwButton(onClick = onCancel, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("revoke_invite_btn_${invite.id}")) {
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
            style = AppTypography.subheading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row {
            RwButton(onClick = onApprove, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("approve_request_${request.id}")) { Text(packStringResource(Res.string.common_approve)) }
            RwButton(onClick = onReject, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("reject_request_${request.id}")) {
                Text(packStringResource(Res.string.teams_reject))
            }
        }
    }
}

@Composable
private fun InviteUserInlineForm(
    visible: Boolean,
    isInviting: Boolean,
    error: com.github.mr3zee.util.UiMessage?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    val resolvedError = error?.resolve()

    // Reset form state when it becomes visible
    LaunchedEffect(visible) {
        if (visible) username = ""
    }

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.teams_invite_dialog_title),
        onDismiss = onDismiss,
        dismissEnabled = !isInviting,
        testTag = "invite_user_form",
        modifier = modifier,
        actions = {
            RwButton(
                onClick = { onInvite(username.trim()) },
                variant = RwButtonVariant.Primary,
                enabled = username.isNotBlank() && !isInviting,
                modifier = Modifier.testTag("invite_user_confirm"),
            ) {
                if (isInviting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.teams_invite))
                }
            }
        },
    ) {
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
}
