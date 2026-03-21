package com.github.mr3zee.teams

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
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
import com.github.mr3zee.components.BackRefreshTopBar
import com.github.mr3zee.components.EmptyState
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.TeamInvite
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInvitesScreen(
    viewModel: MyInvitesViewModel,
    onBack: () -> Unit,
    onInviteAccepted: () -> Unit = {},
) {
    val invites by viewModel.invites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val loadingInviteIds by viewModel.loadingInviteIds.collectAsState()

    var declineInviteId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedError = error?.resolve()

    // Show errors via snackbar with dismiss
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.loadInvites()
            }
        }
        viewModel.dismissError()
    }

    val shortcutActions = remember { ShortcutActions(onRefresh = { viewModel.refresh() }) }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            BackRefreshTopBar(
                title = packStringResource(Res.string.teams_my_invites),
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
                isRefreshing = isRefreshing,
                isManualRefresh = isManualRefresh,
                isLoading = isLoading,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("my_invites_screen"),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    val loadingDesc = packStringResource(Res.string.loading_invites)
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
                }
            } else if (invites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Mail,
                        iconContentDescription = packStringResource(Res.string.teams_invites_empty_icon),
                        message = packStringResource(Res.string.teams_no_pending_invites),
                        secondaryMessage = packStringResource(Res.string.teams_invites_empty_hint),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(invites, key = { it.id }) { invite ->
                        val isInviteLoading = invite.id in loadingInviteIds
                        val acceptedMessage = packStringResource(Res.string.teams_invite_accepted, invite.teamName)
                        val declinedMessage = packStringResource(Res.string.teams_invite_declined_success, invite.teamName)
                        Column(modifier = Modifier.widthIn(max = 1200.dp).animateItem()) {
                            InviteCard(
                                invite = invite,
                                isLoading = isInviteLoading,
                                onAccept = {
                                    viewModel.acceptInvite(invite.id) {
                                        onInviteAccepted()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = acceptedMessage,
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                onDecline = { declineInviteId = invite.id },
                            )
                            RwInlineConfirmation(
                                visible = declineInviteId == invite.id,
                                message = packStringResource(Res.string.teams_decline_confirmation, invite.teamName),
                                confirmLabel = packStringResource(Res.string.teams_decline),
                                onConfirm = {
                                    declineInviteId = null
                                    viewModel.declineInvite(invite.id) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = declinedMessage,
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                onDismiss = { declineInviteId = null },
                                isDestructive = true,
                                testTag = "decline_invite_confirm_${invite.id}",
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                )
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun InviteCard(
    invite: TeamInvite,
    isLoading: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        testTag = "invite_card_${invite.id}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                invite.teamName,
                style = AppTypography.subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                packStringResource(Res.string.teams_invited_by, invite.invitedByUsername),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RwButton(
                onClick = onAccept,
                variant = RwButtonVariant.Primary,
                enabled = !isLoading,
                modifier = Modifier.testTag("accept_invite_${invite.id}"),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.teams_accept))
                }
            }
            RwButton(
                onClick = onDecline,
                variant = RwButtonVariant.Ghost,
                enabled = !isLoading,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("decline_invite_${invite.id}"),
            ) {
                Text(packStringResource(Res.string.teams_decline))
            }
        }
    }
}
