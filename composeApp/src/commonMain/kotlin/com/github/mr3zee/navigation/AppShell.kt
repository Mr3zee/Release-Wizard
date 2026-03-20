package com.github.mr3zee.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.AppLogo
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTooltip
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import com.github.mr3zee.components.SidebarNavItem
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import kotlinx.coroutines.delay
import releasewizard.composeapp.generated.resources.*

private val SIDEBAR_EXPANDED_WIDTH = 220.dp
private val SIDEBAR_COLLAPSED_WIDTH = 56.dp
private val AUTO_COLLAPSE_THRESHOLD = 900.dp

@Composable
fun AppShell(
    sidebarVisible: Boolean,
    currentSection: NavSection?,
    onSectionClick: (NavSection) -> Unit,
    teamSwitcher: @Composable (collapsed: Boolean) -> Unit,
    settingsContent: @Composable (collapsed: Boolean) -> Unit,
    username: String? = null,
    onProfileClick: () -> Unit = {},
    onSignOut: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val borderColor = colors.chromeBorder

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isNarrow = maxWidth < AUTO_COLLAPSE_THRESHOLD
        var manuallyCollapsed by remember { mutableStateOf(false) }
        val collapsed = isNarrow || manuallyCollapsed

        val sidebarWidth by animateDpAsState(
            targetValue = when {
                !sidebarVisible -> 0.dp
                collapsed -> SIDEBAR_COLLAPSED_WIDTH
                else -> SIDEBAR_EXPANDED_WIDTH
            },
            animationSpec = tween(200),
        )

        Row(Modifier.fillMaxSize()) {
            // ── Sidebar ──────────────────────────────────────────────
            if (sidebarWidth > 0.dp) {
                Column(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .background(colors.chromeSurface)
                        .drawBehind {
                            // Right border
                            drawLine(
                                color = borderColor,
                                start = Offset(size.width, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        .testTag("sidebar"),
                ) {
                    // ── App branding ──────────────────────────────────
                    if (!collapsed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppLogo(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = packStringResource(Res.string.sidebar_app_name),
                                style = AppTypography.heading,
                                color = colors.chromeTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        // Collapsed: logo icon
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppLogo(modifier = Modifier.size(24.dp))
                        }
                    }

                    HorizontalDivider(color = colors.chromeBorder)

                    // ── Team switcher ──────────────────────────────────
                    teamSwitcher(collapsed)

                    HorizontalDivider(color = colors.chromeBorder)

                    // ── Navigation items ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    ) {
                        SidebarNavItem(
                            icon = Icons.Outlined.FolderOpen,
                            activeIcon = Icons.Filled.Folder,
                            label = packStringResource(Res.string.sidebar_projects),
                            isActive = currentSection == NavSection.PROJECTS,
                            isCollapsed = collapsed,
                            onClick = { onSectionClick(NavSection.PROJECTS) },
                            testTag = "sidebar_nav_projects",
                        )
                        SidebarNavItem(
                            icon = Icons.Outlined.RocketLaunch,
                            activeIcon = Icons.Filled.Rocket,
                            label = packStringResource(Res.string.sidebar_releases),
                            isActive = currentSection == NavSection.RELEASES,
                            isCollapsed = collapsed,
                            onClick = { onSectionClick(NavSection.RELEASES) },
                            testTag = "sidebar_nav_releases",
                        )
                        SidebarNavItem(
                            icon = Icons.Outlined.Link,
                            activeIcon = Icons.Filled.Link,
                            label = packStringResource(Res.string.sidebar_connections),
                            isActive = currentSection == NavSection.CONNECTIONS,
                            isCollapsed = collapsed,
                            onClick = { onSectionClick(NavSection.CONNECTIONS) },
                            testTag = "sidebar_nav_connections",
                        )
                        SidebarNavItem(
                            icon = Icons.Outlined.Group,
                            activeIcon = Icons.Filled.Group,
                            label = packStringResource(Res.string.sidebar_teams),
                            isActive = currentSection == NavSection.TEAMS,
                            isCollapsed = collapsed,
                            onClick = { onSectionClick(NavSection.TEAMS) },
                            testTag = "sidebar_nav_teams",
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // ── Profile ──────────────────────────────────
                    HorizontalDivider(color = colors.chromeBorder)
                    SidebarNavItem(
                        icon = Icons.Outlined.AccountCircle,
                        activeIcon = Icons.Filled.AccountCircle,
                        label = username ?: "",
                        isActive = false,
                        isCollapsed = collapsed,
                        onClick = { if (username != null) onProfileClick() },
                        testTag = "sidebar_profile",
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )

                    HorizontalDivider(color = colors.chromeBorder)

                    // ── Settings ──────────────────────────────────────
                    settingsContent(collapsed)

                    // ── Sign Out ──────────────────────────────────────
                    SignOutItem(
                        collapsed = collapsed,
                        onSignOut = onSignOut,
                    )

                    // ── Collapse toggle ───────────────────────────────
                    if (!isNarrow) {
                        HorizontalDivider(color = colors.chromeBorder)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xs),
                            contentAlignment = if (collapsed) Alignment.Center else Alignment.CenterEnd,
                        ) {
                            val tooltipText = if (collapsed) {
                                packStringResource(Res.string.sidebar_expand)
                            } else {
                                packStringResource(Res.string.sidebar_collapse)
                            }
                            RwTooltip(tooltip = tooltipText) {
                                RwIconButton(
                                    onClick = { manuallyCollapsed = !manuallyCollapsed },
                                    modifier = Modifier.testTag("sidebar_collapse_toggle"),
                                ) {
                                    Icon(
                                        if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = tooltipText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Content area ─────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    }
}

@Composable
private fun SignOutItem(
    collapsed: Boolean,
    onSignOut: () -> Unit,
) {
    val colors = LocalAppColors.current
    var confirmPending by remember { mutableStateOf(false) }

    // Auto-dismiss the confirmation after 3 seconds
    LaunchedEffect(confirmPending) {
        if (confirmPending) {
            delay(3000)
            confirmPending = false
        }
    }

    // Two-click confirmation in both expanded and collapsed modes
    val handleClick = {
        if (!confirmPending) {
            confirmPending = true
        } else {
            confirmPending = false
            onSignOut()
        }
    }

    val signOutLabel = if (confirmPending) {
        packStringResource(Res.string.sidebar_sign_out_confirm)
    } else {
        packStringResource(Res.string.auth_sign_out)
    }

    if (collapsed) {
        RwTooltip(tooltip = signOutLabel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                RwIconButton(
                    onClick = handleClick,
                    modifier = Modifier.testTag("sidebar_sign_out"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = packStringResource(Res.string.auth_sign_out),
                        tint = if (confirmPending) colors.buttonDangerBg else colors.chromeTextSecondary,
                    )
                }
            }
        }
    } else {
        SidebarNavItem(
            icon = Icons.AutoMirrored.Filled.Logout,
            activeIcon = Icons.AutoMirrored.Filled.Logout,
            label = signOutLabel,
            isActive = false,
            isCollapsed = false,
            onClick = handleClick,
            testTag = "sidebar_sign_out",
            contentColor = if (confirmPending) colors.buttonDangerBg else colors.chromeTextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}
