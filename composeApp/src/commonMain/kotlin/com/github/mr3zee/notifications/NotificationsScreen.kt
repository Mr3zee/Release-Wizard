package com.github.mr3zee.notifications

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SupervisedUserCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.EmptyState
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.SidebarNavItem
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.BackRefreshTopBar
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.UserNotification
import com.github.mr3zee.model.UserNotificationType
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.formatRelativeTimestamp
import com.github.mr3zee.util.resolve
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedError = error?.resolve()

    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refresh()
            }
        }
        viewModel.dismissError()
    }

    val hasUnread = notifications.any { !it.read }

    val shortcutActions = remember { ShortcutActions(onRefresh = { viewModel.refresh() }) }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            BackRefreshTopBar(
                title = packStringResource(Res.string.notifications_title),
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
                isRefreshing = isRefreshing,
                isManualRefresh = isManualRefresh,
                isLoading = isLoading,
                extraActions = {
                    if (hasUnread) {
                        RwTooltip(tooltip = packStringResource(Res.string.notifications_mark_all_read)) {
                            RwIconButton(
                                onClick = { viewModel.markAllAsRead() },
                                modifier = Modifier.testTag("mark_all_read_button"),
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = packStringResource(Res.string.notifications_mark_all_read),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("notifications_screen"),
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
                    val loadingDesc = packStringResource(Res.string.loading_notifications)
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
                }
            } else if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Notifications,
                        message = packStringResource(Res.string.notifications_empty),
                        secondaryMessage = packStringResource(Res.string.notifications_empty_hint),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("notification_list"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(bottom = Spacing.xl),
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationItem(
                                notification = notification,
                                onClick = {
                                    val navigated = navigateToTarget(notification, onNavigate)
                                    if (navigated) {
                                        viewModel.markAsRead(notification.id)
                                    }
                                },
                                onMarkRead = { viewModel.markAsRead(notification.id) },
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

    } // ProvideShortcutActions
}

@Composable
private fun NotificationItem(
    notification: UserNotification,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val indicatorColor = colors.buttonPrimaryBg
    val isUnread = !notification.read

    RwCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .then(
                if (isUnread) {
                    val unreadLabel = packStringResource(Res.string.notifications_unread)
                    Modifier.semantics { stateDescription = unreadLabel }
                } else {
                    Modifier
                }
            )
            .testTag("notification_item_${notification.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isUnread) {
                        Modifier.drawBehind {
                            val barWidth = 4.dp.toPx()
                            drawRoundRect(
                                color = indicatorColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(barWidth / 2f),
                            )
                        }
                    } else Modifier
                )
                .padding(Spacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            // Type icon
            Icon(
                imageVector = notificationTypeIcon(notification.type),
                contentDescription = null,
                tint = colors.chromeTextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Spacing.md))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = if (isUnread) {
                        AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        AppTypography.body
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = notification.message,
                    style = AppTypography.bodySmall,
                    color = colors.chromeTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val teamNameValue = notification.teamName
                    if (teamNameValue != null) {
                        RwBadge(
                            text = teamNameValue,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = formatRelativeTimestamp(notification.timestamp),
                        style = AppTypography.label,
                        color = colors.chromeTextTimestamp,
                    )
                }
            }

            // Mark-as-read icon (only for unread)
            if (isUnread) {
                Spacer(Modifier.width(Spacing.sm))
                RwTooltip(tooltip = packStringResource(Res.string.notifications_mark_as_read)) {
                    RwIconButton(
                        onClick = {
                            onMarkRead()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = packStringResource(Res.string.notifications_mark_as_read),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun notificationTypeIcon(type: UserNotificationType): ImageVector = when (type) {
    UserNotificationType.APPROVAL_REQUESTED -> Icons.Outlined.Approval
    UserNotificationType.RELEASE_COMPLETED -> Icons.Outlined.RocketLaunch
    UserNotificationType.TEAM_INVITE_RECEIVED -> Icons.Outlined.GroupAdd
    UserNotificationType.JOIN_REQUEST_DECIDED -> Icons.Outlined.PersonAdd
    UserNotificationType.MEMBER_ROLE_CHANGED -> Icons.Outlined.ManageAccounts
    UserNotificationType.ACCOUNT_PENDING_APPROVAL -> Icons.Outlined.SupervisedUserCircle
    UserNotificationType.UNKNOWN -> Icons.Outlined.Notifications
}

/** Returns true if navigation occurred, false if target type is unknown/missing. */
private fun navigateToTarget(notification: UserNotification, onNavigate: (Screen) -> Unit): Boolean {
    val targetType = notification.targetType ?: return false
    val targetId = notification.targetId ?: return false
    return when (targetType) {
        "release" -> { onNavigate(Screen.ReleaseView(com.github.mr3zee.model.ReleaseId(targetId))); true }
        "team" -> { onNavigate(Screen.TeamDetail(com.github.mr3zee.model.TeamId(targetId))); true }
        "admin-users" -> { onNavigate(Screen.AdminUsers); true }
        else -> false
    }
}

// ── NotificationBellItem composable for sidebar ──────────────────────

@Composable
fun NotificationBellItem(
    unreadCount: Long,
    isActive: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val badgeColor = colors.buttonPrimaryBg

    if (isCollapsed) {
        RwTooltip(tooltip = packStringResource(Res.string.notifications_title)) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                RwIconButton(
                    onClick = onClick,
                    modifier = Modifier.testTag("notification_bell"),
                ) {
                    Box {
                        Icon(
                            if (isActive) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = packStringResource(Res.string.notifications_title),
                            tint = if (isActive) colors.sidebarActiveText else colors.chromeTextSecondary,
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .background(badgeColor, shape = androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Expanded mode: wrap SidebarNavItem in a Box to overlay badge
        Box(modifier = modifier.padding(horizontal = Spacing.xs)) {
            SidebarNavItem(
                icon = Icons.Outlined.Notifications,
                activeIcon = Icons.Filled.Notifications,
                label = packStringResource(Res.string.notifications_title),
                isActive = isActive,
                isCollapsed = false,
                onClick = onClick,
                testTag = "notification_bell",
            )
            if (unreadCount > 0) {
                val badgeText = if (unreadCount > 9) "9+" else unreadCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Spacing.sm)
                        .background(badgeColor, shape = androidx.compose.foundation.shape.CircleShape)
                        .size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        style = AppTypography.label,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
