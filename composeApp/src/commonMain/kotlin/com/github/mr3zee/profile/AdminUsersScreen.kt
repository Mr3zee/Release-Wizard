package com.github.mr3zee.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.BackRefreshTopBar
import com.github.mr3zee.components.EmptyState
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.copyToClipboard
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.launch
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel,
    onBack: () -> Unit,
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val generatedLinks by viewModel.generatedLinks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissLabel = packStringResource(Res.string.common_dismiss)
    val copiedMessage = packStringResource(Res.string.admin_users_copied)

    val resolvedError = error?.resolve()
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = dismissLabel,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissError()
    }

    val shortcutActions = remember {
        ShortcutActions(onRefresh = { viewModel.loadUsers() })
    }

    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            BackRefreshTopBar(
                title = packStringResource(Res.string.admin_users_title),
                onBack = onBack,
                onRefresh = { viewModel.loadUsers() },
                isRefreshing = isRefreshing,
                isManualRefresh = isManualRefresh,
                showTooltipOnBack = true,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("admin_users_screen"),
    ) { padding ->
        when {
            isLoading && users == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            users?.isEmpty() == true -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Group,
                        message = packStringResource(Res.string.admin_users_empty),
                        secondaryMessage = packStringResource(Res.string.admin_users_empty_hint),
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    users?.forEach { user ->
                        val userId = user.id.value
                        val generatedLink = generatedLinks[userId]

                        ListItemCard(
                            testTag = "admin_user_item_$userId",
                            modifier = Modifier.widthIn(max = 1200.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Text(
                                        user.username,
                                        style = AppTypography.subheading,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    RwBadge(
                                        text = when (user.role) {
                                            UserRole.ADMIN -> packStringResource(Res.string.profile_role_admin)
                                            UserRole.USER -> packStringResource(Res.string.profile_role_user)
                                        },
                                        color = if (user.role == UserRole.ADMIN) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalAppColors.current.chromeTextMetadata
                                        },
                                    )
                                    if (user.oauthProviders.isNotEmpty()) {
                                        RwBadge(
                                            text = packStringResource(Res.string.admin_users_oauth_badge),
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                    if (!user.hasPassword) {
                                        RwBadge(
                                            text = packStringResource(Res.string.admin_users_no_password_badge),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (generatedLink != null) {
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    Text(
                                        packStringResource(Res.string.admin_users_reset_link_label),
                                        style = AppTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        SelectionContainer(modifier = Modifier.weight(1f)) {
                                            Text(
                                                generatedLink,
                                                style = AppTypography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("admin_reset_link_value_$userId"),
                                            )
                                        }
                                        RwButton(
                                            onClick = {
                                                copyToClipboard(generatedLink)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = copiedMessage,
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                }
                                            },
                                            variant = RwButtonVariant.Ghost,
                                        ) {
                                            Text(packStringResource(Res.string.admin_users_copy_link))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(Spacing.sm))

                            RwButton(
                                onClick = { viewModel.generateResetLink(userId) },
                                variant = RwButtonVariant.Secondary,
                                modifier = Modifier.testTag("admin_generate_reset_link_$userId"),
                            ) {
                                Text(
                                    if (user.hasPassword) packStringResource(Res.string.admin_users_generate_reset_link)
                                    else packStringResource(Res.string.admin_users_generate_set_password_link)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }
    }

    } // ProvideShortcutActions
}
