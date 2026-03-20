package com.github.mr3zee.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwRadioButton
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.SidebarNavItem
import com.github.mr3zee.i18n.LanguagePack
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.theme.ThemePreference
import kotlinx.coroutines.flow.StateFlow
import releasewizard.composeapp.generated.resources.*

/**
 * Team switcher displayed in the sidebar. Shows a dropdown of all user teams.
 * When collapsed, shows a circular avatar with the team's first letter.
 */
@Composable
fun SidebarTeamSwitcher(
    userTeams: List<UserTeamInfo>,
    activeTeamId: StateFlow<TeamId?>,
    onTeamChanged: (TeamId) -> Unit,
    collapsed: Boolean,
) {
    val colors = LocalAppColors.current
    val currentTeamId by activeTeamId.collectAsState()
    val activeTeamName = userTeams.find { it.teamId == currentTeamId }?.teamName ?: ""
    var showDropdown by remember { mutableStateOf(false) }

    if (collapsed) {
        // Circular avatar with first letter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            RwTooltip(tooltip = activeTeamName.ifEmpty { packStringResource(Res.string.sidebar_team_none) }) {
                UnstyledButton(
                    onClick = { if (userTeams.size > 1) showDropdown = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.sidebarActiveBg)
                        .testTag("sidebar_team_switcher"),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = activeTeamName.firstOrNull()?.uppercase() ?: "?",
                            style = AppTypography.label.copy(fontWeight = FontWeight.Bold),
                            color = colors.sidebarActiveText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            TeamDropdown(
                expanded = showDropdown,
                onDismiss = { showDropdown = false },
                userTeams = userTeams,
                currentTeamId = currentTeamId,
                onTeamChanged = {
                    onTeamChanged(it)
                    showDropdown = false
                },
            )
        }
    } else {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            UnstyledButton(
                onClick = { if (userTeams.size > 1) showDropdown = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sidebar_team_switcher"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeTeamName,
                        style = AppTypography.bodySmall,
                        color = colors.chromeTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (userTeams.size > 1) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = packStringResource(Res.string.projects_switch_team),
                            tint = colors.chromeTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            TeamDropdown(
                expanded = showDropdown,
                onDismiss = { showDropdown = false },
                userTeams = userTeams,
                currentTeamId = currentTeamId,
                onTeamChanged = {
                    onTeamChanged(it)
                    showDropdown = false
                },
            )
        }
    }
}

@Composable
private fun TeamDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    userTeams: List<UserTeamInfo>,
    currentTeamId: TeamId?,
    onTeamChanged: (TeamId) -> Unit,
) {
    val colors = LocalAppColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // Column + verticalScroll + heightIn instead of LazyColumn (CLAUDE.md constraint)
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            userTeams.forEach { teamInfo ->
                DropdownMenuItem(
                    text = {
                        Text(
                            teamInfo.teamName,
                            color = if (teamInfo.teamId == currentTeamId) colors.sidebarActiveText
                            else colors.chromeTextPrimary,
                        )
                    },
                    onClick = { onTeamChanged(teamInfo.teamId) },
                    modifier = Modifier.testTag("sidebar_team_picker_${teamInfo.teamId.value}"),
                )
            }
        }
    }
}

/**
 * Settings section in the sidebar. Expandable with theme, language, and shortcuts.
 * In collapsed mode, shows a gear icon that opens a floating popup.
 */
@Composable
fun SidebarSettingsContent(
    collapsed: Boolean,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    languagePack: LanguagePack,
    onLanguagePackChange: (LanguagePack) -> Unit,
    onShowShortcuts: () -> Unit,
) {
    val colors = LocalAppColors.current

    if (collapsed) {
        // Gear icon with floating popup
        var showPopup by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            RwTooltip(tooltip = packStringResource(Res.string.sidebar_settings)) {
                RwIconButton(
                    onClick = { showPopup = true },
                    modifier = Modifier.testTag("sidebar_settings"),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = packStringResource(Res.string.sidebar_settings),
                        tint = colors.chromeTextSecondary,
                    )
                }
            }
            DropdownMenu(
                expanded = showPopup,
                onDismissRequest = { showPopup = false },
            ) {
                SettingsMenuItems(
                    themePreference = themePreference,
                    onThemeChange = { onThemeChange(it); showPopup = false },
                    languagePack = languagePack,
                    onLanguagePackChange = { onLanguagePackChange(it); showPopup = false },
                    onShowShortcuts = { onShowShortcuts(); showPopup = false },
                )
            }
        }
    } else {
        // Expandable section
        var expanded by remember { mutableStateOf(false) }
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(200),
        )

        Column(modifier = Modifier.padding(horizontal = Spacing.xs)) {
            // Settings header — clickable to expand, with chevron indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SidebarNavItem(
                        icon = Icons.Outlined.Settings,
                        activeIcon = Icons.Filled.Settings,
                        label = packStringResource(Res.string.sidebar_settings),
                        isActive = false,
                        isCollapsed = false,
                        onClick = { expanded = !expanded },
                        testTag = "sidebar_settings",
                        semanticRole = androidx.compose.ui.semantics.Role.Button,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        packStringResource(Res.string.sidebar_settings_collapse)
                    } else {
                        packStringResource(Res.string.sidebar_settings_expand)
                    },
                    tint = colors.chromeTextTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                )
            }

            // Expandable sub-items
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                Column(
                    modifier = Modifier.padding(start = Spacing.xxl),
                ) {
                    // Theme toggle
                    val themeLabel = when (themePreference) {
                        ThemePreference.SYSTEM -> packStringResource(Res.string.settings_theme_auto)
                        ThemePreference.LIGHT -> packStringResource(Res.string.settings_theme_light)
                        ThemePreference.DARK -> packStringResource(Res.string.settings_theme_dark)
                    }
                    SettingsSubItem(
                        label = themeLabel,
                        onClick = {
                            val next = when (themePreference) {
                                ThemePreference.SYSTEM -> ThemePreference.LIGHT
                                ThemePreference.LIGHT -> ThemePreference.DARK
                                ThemePreference.DARK -> ThemePreference.SYSTEM
                            }
                            onThemeChange(next)
                        },
                        testTag = "sidebar_settings_theme",
                    )

                    // Language picker
                    var showLanguagePicker by remember { mutableStateOf(false) }
                    Box {
                        SettingsSubItem(
                            label = languagePack.displayName,
                            onClick = { showLanguagePicker = true },
                            testTag = "sidebar_settings_language",
                        )
                        DropdownMenu(
                            expanded = showLanguagePicker,
                            onDismissRequest = { showLanguagePicker = false },
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                LanguagePack.entries.forEach { pack ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RwRadioButton(
                                                    selected = pack == languagePack,
                                                    onClick = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(Modifier.width(Spacing.sm))
                                                Column {
                                                    Text(pack.displayName, style = AppTypography.body)
                                                    if (pack != LanguagePack.ENGLISH) {
                                                        Text(
                                                            pack.preview,
                                                            style = AppTypography.bodySmall,
                                                            color = colors.chromeTextSecondary,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            onLanguagePackChange(pack)
                                            showLanguagePicker = false
                                        },
                                        modifier = Modifier.testTag("sidebar_language_${pack.name}"),
                                    )
                                }
                            }
                        }
                    }

                    // Keyboard shortcuts
                    SettingsSubItem(
                        label = packStringResource(Res.string.shortcuts_menu_item),
                        onClick = onShowShortcuts,
                        testTag = "sidebar_settings_shortcuts",
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItems(
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    languagePack: LanguagePack,
    onLanguagePackChange: (LanguagePack) -> Unit,
    onShowShortcuts: () -> Unit,
) {
    val colors = LocalAppColors.current
    val themeLabel = when (themePreference) {
        ThemePreference.SYSTEM -> packStringResource(Res.string.settings_theme_auto)
        ThemePreference.LIGHT -> packStringResource(Res.string.settings_theme_light)
        ThemePreference.DARK -> packStringResource(Res.string.settings_theme_dark)
    }
    DropdownMenuItem(
        text = { Text(themeLabel) },
        onClick = {
            val next = when (themePreference) {
                ThemePreference.SYSTEM -> ThemePreference.LIGHT
                ThemePreference.LIGHT -> ThemePreference.DARK
                ThemePreference.DARK -> ThemePreference.SYSTEM
            }
            onThemeChange(next)
        },
        modifier = Modifier.testTag("sidebar_settings_theme"),
    )
    // Language picker (also available in collapsed popup per UX review)
    LanguagePack.entries.forEach { pack ->
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RwRadioButton(
                        selected = pack == languagePack,
                        onClick = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        pack.displayName,
                        style = AppTypography.body,
                        color = if (pack == languagePack) colors.sidebarActiveText else colors.chromeTextPrimary,
                    )
                }
            },
            onClick = { onLanguagePackChange(pack) },
            modifier = Modifier.testTag("sidebar_language_${pack.name}"),
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(packStringResource(Res.string.shortcuts_menu_item)) },
        onClick = onShowShortcuts,
        modifier = Modifier.testTag("sidebar_settings_shortcuts"),
    )
}

@Composable
private fun SettingsSubItem(
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = LocalAppColors.current
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .testTag(testTag),
    ) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = colors.chromeTextSecondary,
            modifier = Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.xs),
        )
    }
}
