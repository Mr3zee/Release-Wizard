package com.github.mr3zee.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RefreshIconButton
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

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
    val actionError by viewModel.actionError.collectAsState()
    val isLeaving by viewModel.isLeaving.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    var showLeaveDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val shortcutActions = remember(showLeaveDialog) {
        ShortcutActions(onRefresh = { viewModel.refresh() }, hasDialogOpen = showLeaveDialog)
    }
    ProvideShortcutActions(shortcutActions) {

    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedActionError = actionError?.resolve()

    // Show action errors (e.g., leave team failure) via snackbar
    LaunchedEffect(actionError) {
        val msg = resolvedActionError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.leaveTeam { onBack() }
        }
        viewModel.dismissActionError()
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = {
                        Text(
                            team?.name ?: packStringResource(Res.string.teams_team_fallback),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        RwTooltip(tooltip = packStringResource(Res.string.common_back)) {
                            RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                                Text(packStringResource(Res.string.common_back))
                            }
                        }
                    },
                    actions = {
                        RwTooltip(tooltip = packStringResource(Res.string.teams_audit_log)) {
                            RwButton(onClick = onAuditLog, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("audit_log_button")) {
                                Text(packStringResource(Res.string.teams_audit_log))
                            }
                        }
                        if (isTeamLead) {
                            RwTooltip(tooltip = packStringResource(Res.string.teams_manage)) {
                                RwButton(onClick = onManage, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("manage_team_button")) {
                                    Text(packStringResource(Res.string.teams_manage))
                                }
                            }
                        }
                        RefreshIconButton(
                            onClick = { viewModel.refresh() },
                            isRefreshing = isRefreshing,
                            isManualRefresh = isManualRefresh,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        RwTooltip(tooltip = packStringResource(Res.string.teams_leave)) {
                            RwButton(
                                onClick = { showLeaveDialog = true },
                                variant = RwButtonVariant.Ghost,
                                contentColor = MaterialTheme.colorScheme.error,
                                enabled = !isLeaving,
                                modifier = Modifier.testTag("leave_team_button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = packStringResource(Res.string.teams_leave),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(packStringResource(Res.string.teams_leave))
                            }
                        }
                    },
                )
                if (isRefreshing && !isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .alpha(if (isManualRefresh) 1f else 0.5f)
                            .testTag("refresh_indicator"),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("team_detail_screen"),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && team == null) {
            // Full-page error only for initial load failures
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error?.resolve() ?: packStringResource(Res.string.common_unknown_error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    RwButton(onClick = { viewModel.loadDetail() }, variant = RwButtonVariant.Primary) { Text(packStringResource(Res.string.common_retry)) }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Refresh error banner
                val resolvedRefreshError = refreshError?.resolve()
                if (resolvedRefreshError != null) {
                    RefreshErrorBanner(
                        message = resolvedRefreshError,
                        onDismiss = { viewModel.dismissRefreshError() },
                    )
                }

                // Leave confirmation — fixed position above LazyColumn
                team?.let { t ->
                    RwInlineConfirmation(
                        visible = showLeaveDialog,
                        message = packStringResource(Res.string.teams_leave_confirmation, t.name),
                        confirmLabel = packStringResource(Res.string.teams_leave),
                        onConfirm = {
                            showLeaveDialog = false
                            viewModel.leaveTeam { onBack() }
                        },
                        onDismiss = { showLeaveDialog = false },
                        testTag = "leave_team_confirm",
                        modifier = Modifier
                            .widthIn(max = 1200.dp)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    team?.let { t ->
                        item {
                            RwCard(
                                modifier = Modifier
                                    .widthIn(max = 1200.dp)
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                            ) {
                                Column(modifier = Modifier.padding(Spacing.lg)) {
                                    // Team name as heading inside the card
                                    Text(
                                        t.name,
                                        style = AppTypography.heading,
                                    )
                                    if (t.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(Spacing.sm))
                                        Text(
                                            t.description,
                                            style = AppTypography.body,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    Text(
                                        packPluralStringResource(Res.plurals.members, members.size, members.size),
                                        style = AppTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            packStringResource(Res.string.teams_members_section),
                            style = AppTypography.heading,
                            modifier = Modifier
                                .widthIn(max = 1200.dp)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }

                    if (members.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 1200.dp)
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // todo claude: duplicate 12 lines
                                    Icon(
                                        Icons.Outlined.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    Text(
                                        packStringResource(Res.string.teams_no_members),
                                        style = AppTypography.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(members, key = { it.userId.value }) { member ->
                            MemberItem(
                                member = member,
                                modifier = Modifier.widthIn(max = 1200.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun MemberItem(
    member: TeamMembership,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    ListItemCard(
        testTag = "member_item_${member.userId.value}",
        modifier = modifier,
    ) {
        Text(
            member.username,
            style = AppTypography.subheading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RwBadge(
            text = member.role.displayName(),
            color = if (member.role == TeamRole.TEAM_LEAD) MaterialTheme.colorScheme.primary else appColors.chromeTextMetadata,
            testTag = "role_badge_${member.userId.value}",
        )
    }
}
