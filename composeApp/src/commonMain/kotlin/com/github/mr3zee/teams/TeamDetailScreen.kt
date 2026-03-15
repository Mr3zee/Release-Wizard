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
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.theme.AppTypography
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

    var showLeaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        team?.name ?: packStringResource(Res.string.teams_team_fallback),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
                actions = {
                    RwButton(
                        onClick = { showLeaveDialog = true },
                        variant = RwButtonVariant.Ghost,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("leave_team_button"),
                    ) {
                        Text(packStringResource(Res.string.teams_leave))
                    }
                    RwButton(
                        onClick = onAuditLog,
                        variant = RwButtonVariant.Ghost,
                        modifier = Modifier.testTag("audit_log_button"),
                    ) {
                        Text(packStringResource(Res.string.teams_audit_log))
                    }
                    if (isTeamLead) {
                        RwButton(
                            onClick = onManage,
                            variant = RwButtonVariant.Ghost,
                            modifier = Modifier.testTag("manage_team_button"),
                        ) {
                            Text(packStringResource(Res.string.teams_manage))
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
                    Text(error?.resolve() ?: packStringResource(Res.string.common_unknown_error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    RwButton(onClick = { viewModel.loadDetail() }, variant = RwButtonVariant.Primary) { Text(packStringResource(Res.string.common_retry)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                team?.let { t ->
                    item {
                        RwCard(
                            modifier = Modifier
                                .widthIn(max = 900.dp)
                                .fillMaxWidth()
                                .padding(Spacing.lg),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.lg)) {
                                Text(
                                    t.name,
                                    style = AppTypography.heading,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (t.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        t.description,
                                        style = AppTypography.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
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
                            .widthIn(max = 900.dp)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }

                items(members, key = { it.userId.value }) { member ->
                    MemberItem(
                        member = member,
                        modifier = Modifier.widthIn(max = 900.dp),
                    )
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(packStringResource(Res.string.teams_leave_title)) },
            text = { Text(packStringResource(Res.string.teams_leave_confirmation, team?.name ?: packStringResource(Res.string.teams_leave_fallback))) },
            confirmButton = {
                RwButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveTeam { onBack() }
                }, variant = RwButtonVariant.Ghost, contentColor = MaterialTheme.colorScheme.error) {
                    Text(packStringResource(Res.string.teams_leave))
                }
            },
            dismissButton = {
                RwButton(onClick = { showLeaveDialog = false }, variant = RwButtonVariant.Ghost) { Text(packStringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun MemberItem(
    member: TeamMembership,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        testTag = "member_item_${member.userId.value}",
        modifier = modifier,
    ) {
        Text(
            member.username,
            style = AppTypography.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            member.role.displayName(),
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
