package com.github.mr3zee.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.i18n.LanguagePack
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.ThemePreference
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onEditProject: (ProjectId) -> Unit,
    onConnections: (() -> Unit)? = null,
    onReleases: (() -> Unit)? = null,
    onTeams: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
    languagePack: LanguagePack = LanguagePack.ENGLISH,
    onLanguagePackChange: (LanguagePack) -> Unit = {},
    activeTeamId: StateFlow<TeamId?>? = null,
    userTeams: List<UserTeamInfo> = emptyList(),
    onTeamChanged: (TeamId) -> Unit = {},
) {
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectTemplate?>(null) }

    val currentTeamId by (activeTeamId ?: remember { MutableStateFlow<TeamId?>(null) }).collectAsState()
    var showTeamPicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val activeTeamName = userTeams.find { it.teamId == currentTeamId }?.teamName
        ?: packStringResource(Res.string.projects_no_team)

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = {
                        if (userTeams.size > 1) {
                            TextButton(
                                onClick = { showTeamPicker = true },
                                modifier = Modifier.testTag("team_switcher"),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        packStringResource(Res.string.projects_title_with_team, activeTeamName),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = packStringResource(Res.string.projects_switch_team),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                packStringResource(Res.string.projects_title_with_team, activeTeamName),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        if (onTeams != null) {
                            TextButton(
                                onClick = onTeams,
                                modifier = Modifier.testTag("teams_button"),
                            ) {
                                Text(packStringResource(Res.string.projects_teams))
                            }
                        }
                        if (onReleases != null) {
                            TextButton(
                                onClick = onReleases,
                                modifier = Modifier.testTag("releases_button"),
                            ) {
                                Text(packStringResource(Res.string.projects_releases))
                            }
                        }
                        if (onConnections != null) {
                            TextButton(
                                onClick = onConnections,
                                modifier = Modifier.testTag("connections_button"),
                            ) {
                                Text(packStringResource(Res.string.projects_connections))
                            }
                        }
                        if (onLogout != null) {
                            TextButton(
                                onClick = onLogout,
                                modifier = Modifier.testTag("logout_button"),
                            ) {
                                Text(packStringResource(Res.string.auth_sign_out))
                            }
                        }
                        // Overflow menu for theme toggle and refresh
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("overflow_menu_button"),
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = packStringResource(Res.string.common_more_options))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(packStringResource(Res.string.common_refresh)) },
                                    onClick = {
                                        viewModel.refresh()
                                        showOverflowMenu = false
                                    },
                                    modifier = Modifier.testTag("refresh_menu_item"),
                                )
                                DropdownMenuItem(
                                    text = {
                                        val label = when (themePreference) {
                                            ThemePreference.SYSTEM -> packStringResource(Res.string.projects_theme_auto)
                                            ThemePreference.LIGHT -> packStringResource(Res.string.projects_theme_light)
                                            ThemePreference.DARK -> packStringResource(Res.string.projects_theme_dark)
                                        }
                                        Text(label)
                                    },
                                    onClick = {
                                        val next = when (themePreference) {
                                            ThemePreference.SYSTEM -> ThemePreference.LIGHT
                                            ThemePreference.LIGHT -> ThemePreference.DARK
                                            ThemePreference.DARK -> ThemePreference.SYSTEM
                                        }
                                        onThemeChange(next)
                                        showOverflowMenu = false
                                    },
                                    modifier = Modifier.testTag("theme_toggle_menu_item"),
                                )
                                HorizontalDivider()
                                LanguagePack.entries.forEach { pack ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = pack == languagePack,
                                                    onClick = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(pack.displayName, style = MaterialTheme.typography.bodyMedium)
                                                    if (pack != LanguagePack.ENGLISH) {
                                                        Text(
                                                            pack.preview,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            onLanguagePackChange(pack)
                                            showOverflowMenu = false
                                        },
                                        modifier = Modifier.testTag("language_pack_${pack.name}"),
                                    )
                                }
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
                modifier = Modifier.testTag("create_project_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.projects_create_project))
            }
        },
        modifier = Modifier.testTag("project_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Refresh error banner
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(packStringResource(Res.string.projects_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error?.resolve() ?: packStringResource(Res.string.common_unknown_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadProjects() }) {
                            Text(packStringResource(Res.string.common_retry))
                        }
                    }
                }
            } else if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (searchQuery.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = packStringResource(Res.string.common_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.setSearchQuery("") }) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Text(
                            text = packStringResource(Res.string.projects_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("project_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(projects, key = { it.id.value }) { project ->
                        ProjectListItem(
                            project = project,
                            onClick = { onEditProject(project.id) },
                            onDelete = { projectToDelete = project },
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createProject(name) { projectId ->
                    onEditProject(projectId)
                }
                showCreateDialog = false
            },
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(packStringResource(Res.string.projects_delete_title)) },
            text = { Text(packStringResource(Res.string.projects_delete_confirmation, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project.id)
                    projectToDelete = null
                }) {
                    Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(packStringResource(Res.string.common_cancel))
                }
            },
        )
    }

    if (showTeamPicker && userTeams.size > 1) {
        AlertDialog(
            onDismissRequest = { showTeamPicker = false },
            title = { Text(packStringResource(Res.string.projects_switch_team_title)) },
            text = {
                Column {
                    userTeams.forEach { teamInfo ->
                        TextButton(
                            onClick = {
                                onTeamChanged(teamInfo.teamId)
                                showTeamPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("team_picker_${teamInfo.teamId.value}"),
                        ) {
                            Text(
                                teamInfo.teamName,
                                color = if (teamInfo.teamId == currentTeamId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTeamPicker = false }) { Text(packStringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ProjectListItem(
    project: ProjectTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        onClick = onClick,
        testTag = "project_item_${project.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = packPluralStringResource(Res.plurals.blocks, project.dagGraph.blocks.size, project.dagGraph.blocks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.projects_new_project)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(packStringResource(Res.string.projects_project_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_name_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}
