package com.github.mr3zee.teams

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.TeamResponse
import com.github.mr3zee.components.EmptySearchResults
import com.github.mr3zee.components.EmptyState
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.SearchBar
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RefreshIconButton
import com.github.mr3zee.components.RwFab
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwMarkdownText
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val pagination by viewModel.pagination.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }

    val shortcutActions = remember(showCreateDialog) {
        ShortcutActions(
            onSearch = { searchFocusRequester.requestFocus() },
            onCreate = { showCreateDialog = true },
            onRefresh = { viewModel.refresh() },
            hasDialogOpen = showCreateDialog,
        )
    }
    ProvideShortcutActions(shortcutActions) {

    val refreshLabel = packStringResource(Res.string.common_refresh)
    val resolvedError = error?.resolve()

    // Show errors via snackbar — use Refresh since the error may come from load or join
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = refreshLabel,
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
                    title = {
                        Text(
                            packStringResource(Res.string.teams_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
                        RefreshIconButton(
                            onClick = { viewModel.refresh() },
                            isRefreshing = isRefreshing,
                            isManualRefresh = isManualRefresh,
                        )
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
            RwTooltip(tooltip = packStringResource(Res.string.teams_new_team)) {
                RwFab(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_team_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.teams_new_team))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("team_list_screen"),
    ) { padding ->
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

            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholder = packStringResource(Res.string.teams_search_placeholder),
                focusRequester = searchFocusRequester,
                testTag = "team_search_field",
            )

            val resolvedMessage = message?.resolve()
            if (resolvedMessage != null) {
                RwCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            resolvedMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = AppTypography.body,
                            modifier = Modifier.weight(1f),
                        )
                        RwButton(onClick = { viewModel.clearMessage() }, variant = RwButtonVariant.Ghost) {
                            Text(packStringResource(Res.string.common_dismiss))
                        }
                    }
                }
            }

            CreateTeamInlineForm(
                visible = showCreateDialog,
                onDismiss = { showCreateDialog = false },
                onCreate = { name, description ->
                    viewModel.createTeam(name, description) { teamId ->
                        onTeamCreated(teamId)
                    }
                    showCreateDialog = false
                },
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (teams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (searchQuery.isNotBlank()) {
                        EmptySearchResults(
                            onClearSearch = { viewModel.setSearchQuery("") },
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Group,
                            message = packStringResource(Res.string.teams_empty_state),
                            action = {
                                RwButton(
                                    onClick = { showCreateDialog = true },
                                    variant = RwButtonVariant.Primary,
                                    modifier = Modifier.testTag("empty_state_create_team_button"),
                                ) {
                                    Text(packStringResource(Res.string.teams_new_team))
                                }
                            },
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("team_list"),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(teams, key = { it.team.id.value }) { teamResponse ->
                        TeamListItem(
                            teamResponse = teamResponse,
                            onClick = { onTeamClick(teamResponse.team.id) },
                            onJoinRequest = { viewModel.requestToJoin(teamResponse.team.id) },
                            isMember = teamResponse.team.id in memberTeamIds,
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                )
                }
            }
        }
    }

    // Create team dialog replaced by inline form in the content area
    } // ProvideShortcutActions
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
        onClick = if (isMember) onClick else null,
        testTag = "team_item_${teamResponse.team.id.value}",
        modifier = if (!isMember) modifier.alpha(0.7f) else modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                teamResponse.team.name,
                style = AppTypography.subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (teamResponse.team.description.isNotBlank()) {
                RwMarkdownText(
                    markdown = teamResponse.team.description,
                )
            }
        }
        Text(
            packPluralStringResource(Res.plurals.members, teamResponse.memberCount, teamResponse.memberCount),
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        if (isMember) {
            RwBadge(
                text = packStringResource(Res.string.teams_member_badge),
                color = MaterialTheme.colorScheme.primary,
                testTag = "member_badge_${teamResponse.team.id.value}",
            )
        } else {
            RwButton(onClick = onJoinRequest, variant = RwButtonVariant.Ghost) {
                Text(packStringResource(Res.string.teams_request_to_join))
            }
        }
    }
}

@Composable
private fun CreateTeamInlineForm(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Reset form state when it becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            name = ""
            description = ""
        }
    }

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.teams_new_team),
        onDismiss = onDismiss,
        onSubmit = { if (name.isNotBlank()) onCreate(name, description) },
        testTag = "create_team_form",
        modifier = Modifier
            .widthIn(max = 1200.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        actions = {
            RwButton(
                onClick = { onCreate(name, description) },
                variant = RwButtonVariant.Primary,
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("create_team_confirm"),
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
    ) {
        RwTextField(
            value = name,
            onValueChange = { name = it },
            label = packStringResource(Res.string.teams_team_name),
            placeholder = packStringResource(Res.string.teams_team_name_placeholder),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("team_name_input"),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        RwTextField(
            value = description,
            onValueChange = { description = it },
            label = packStringResource(Res.string.teams_description_optional),
            placeholder = packStringResource(Res.string.teams_description_placeholder),
            modifier = Modifier.fillMaxWidth().testTag("team_description_input"),
        )
    }
}
