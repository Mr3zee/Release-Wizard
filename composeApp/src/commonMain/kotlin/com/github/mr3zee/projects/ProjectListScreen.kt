package com.github.mr3zee.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RefreshIconButton
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwFab
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onEditProject: (ProjectId) -> Unit,
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

    val searchFocusRequester = remember { FocusRequester() }

    val isDialogOpen = showCreateDialog || projectToDelete != null
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(
            onSearch = { searchFocusRequester.requestFocus() },
            onCreate = { showCreateDialog = true },
            onRefresh = { viewModel.refresh() },
            hasDialogOpen = isDialogOpen,
        )
    }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = {
                        Text(
                            packStringResource(Res.string.sidebar_projects),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
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
            RwTooltip(tooltip = packStringResource(Res.string.projects_create_project)) {
                RwFab(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_project_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.projects_create_project))
                }
            }
        },
        modifier = Modifier.testTag("project_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                placeholder = packStringResource(Res.string.projects_search_placeholder),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .focusRequester(searchFocusRequester)
                    .testTag("search_field"),
            )

            CreateProjectInlineForm(
                visible = showCreateDialog,
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createProject(name) { projectId ->
                        onEditProject(projectId)
                    }
                    showCreateDialog = false
                },
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
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwButton(onClick = { viewModel.loadProjects() }, variant = RwButtonVariant.Primary) {
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
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            RwButton(onClick = { viewModel.setSearchQuery("") }, variant = RwButtonVariant.Ghost) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = packStringResource(Res.string.projects_empty_state),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            RwButton(
                                onClick = { showCreateDialog = true },
                                variant = RwButtonVariant.Primary,
                                modifier = Modifier.testTag("empty_state_create_project_button"),
                            ) {
                                Text(packStringResource(Res.string.projects_create_project))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("project_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(projects, key = { it.id.value }) { project ->
                        Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                            ProjectListItem(
                                project = project,
                                onClick = { onEditProject(project.id) },
                                onDelete = { projectToDelete = project },
                            )
                            RwInlineConfirmation(
                                visible = projectToDelete?.id == project.id,
                                message = packStringResource(Res.string.projects_delete_confirmation, project.name),
                                confirmLabel = packStringResource(Res.string.common_delete),
                                onConfirm = {
                                    viewModel.deleteProject(project.id)
                                    projectToDelete = null
                                },
                                onDismiss = { projectToDelete = null },
                                testTag = "delete_project_confirm_${project.id.value}",
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun ProjectListItem(
    project: ProjectTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItemCard(
        onClick = onClick,
        testTag = "project_item_${project.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = AppTypography.subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = AppTypography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = packPluralStringResource(Res.plurals.blocks, project.dagGraph.blocks.size, project.dagGraph.blocks.size),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            RwIconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("project_menu_${project.id.value}"),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = packStringResource(Res.string.common_more_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("delete_menu_item"),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CreateProjectInlineForm(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    // Reset form state when it becomes visible
    LaunchedEffect(visible) {
        if (visible) name = ""
    }

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.projects_new_project),
        onDismiss = onDismiss,
        testTag = "create_project_form",
        modifier = Modifier
            .widthIn(max = 1200.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        actions = {
            RwButton(
                onClick = { onCreate(name) },
                variant = RwButtonVariant.Primary,
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("create_project_confirm"),
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
    ) {
        RwTextField(
            value = name,
            onValueChange = { name = it },
            label = packStringResource(Res.string.projects_project_name),
            placeholder = packStringResource(Res.string.projects_project_name),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("project_name_input"),
        )
    }
}
