package com.github.mr3zee.teams

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.TeamResponse
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamListScreen(
    viewModel: TeamListViewModel,
    onTeamClick: (TeamId) -> Unit,
    onTeamCreated: (TeamId) -> Unit,
    onMyInvites: () -> Unit,
    onBack: (() -> Unit)? = null,
    memberTeamIds: Set<TeamId> = emptySet(),
) {
    val teams by viewModel.teams.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Spin animation for refresh icon
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
    )
    val spinning = isManualRefresh && isRefreshing

    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedError = error?.resolve()

    // Show errors via snackbar
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.loadTeams()
            }
        }
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = { Text(packStringResource(Res.string.teams_title)) },
                    navigationIcon = {
                        if (onBack != null) {
                            RwButton(onClick = onBack, variant = RwButtonVariant.Ghost) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                                Text(packStringResource(Res.string.common_back))
                            }
                        }
                    },
                    actions = {
                        RwButton(
                            onClick = onMyInvites,
                            variant = RwButtonVariant.Ghost,
                            modifier = Modifier.testTag("my_invites_button"),
                        ) {
                            Text(packStringResource(Res.string.teams_my_invites))
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text(packStringResource(Res.string.common_refresh)) } },
                            state = rememberTooltipState(),
                        ) {
                            RwIconButton(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.testTag("refresh_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = packStringResource(Res.string.common_refresh),
                                    modifier = Modifier
                                        .rotate(if (spinning) rotation else 0f)
                                        .testTag(if (spinning) "refresh_icon_spinning" else "refresh_icon_idle"),
                                )
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_team_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.teams_new_team))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("team_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Refresh error banner
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            RwTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = packStringResource(Res.string.teams_search_placeholder),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("team_search_field"),
            )

            val resolvedMessage = message?.resolve()
            if (resolvedMessage != null) {
                RwCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            resolvedMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        RwButton(onClick = { viewModel.clearMessage() }, variant = RwButtonVariant.Ghost) {
                            Text(packStringResource(Res.string.common_dismiss))
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (teams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (searchQuery.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                packStringResource(Res.string.common_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RwButton(onClick = { viewModel.setSearchQuery("") }, variant = RwButtonVariant.Ghost) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Text(
                            packStringResource(Res.string.teams_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("team_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(teams, key = { it.team.id.value }) { teamResponse ->
                        TeamListItem(
                            teamResponse = teamResponse,
                            onClick = { onTeamClick(teamResponse.team.id) },
                            onJoinRequest = { viewModel.requestToJoin(teamResponse.team.id) },
                            isMember = teamResponse.team.id in memberTeamIds,
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTeamDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.createTeam(name, description) { teamId ->
                    onTeamCreated(teamId)
                }
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun TeamListItem(
    teamResponse: TeamResponse,
    onClick: () -> Unit,
    onJoinRequest: () -> Unit,
    isMember: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        onClick = onClick,
        testTag = "team_item_${teamResponse.team.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                teamResponse.team.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (teamResponse.team.description.isNotBlank()) {
                Text(
                    teamResponse.team.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                packPluralStringResource(Res.plurals.members, teamResponse.memberCount, teamResponse.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isMember) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = AppShapes.pill,
            ) {
                Text(
                    packStringResource(Res.string.teams_member_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        } else {
            RwButton(onClick = onJoinRequest, variant = RwButtonVariant.Ghost) {
                Text(packStringResource(Res.string.teams_request_to_join))
            }
        }
    }
}

@Composable
private fun CreateTeamDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.teams_new_team)) },
        text = {
            Column {
                RwTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = packStringResource(Res.string.teams_team_name),
                    placeholder = packStringResource(Res.string.teams_team_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("team_name_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                RwTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = packStringResource(Res.string.teams_description_optional),
                    placeholder = packStringResource(Res.string.teams_description_optional),
                    modifier = Modifier.fillMaxWidth().testTag("team_description_input"),
                )
            }
        },
        confirmButton = {
            RwButton(onClick = { onCreate(name, description) }, variant = RwButtonVariant.Ghost, enabled = name.isNotBlank()) {
                Text(packStringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost) { Text(packStringResource(Res.string.common_cancel)) }
        },
    )
}
