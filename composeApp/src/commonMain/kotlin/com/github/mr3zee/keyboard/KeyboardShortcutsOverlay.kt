package com.github.mr3zee.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.DarkAppColors
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.RuntimeContext
import com.github.mr3zee.util.currentRuntimeContext
import releasewizard.composeapp.generated.resources.*

@Composable
fun KeyboardShortcutsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val isDark = colors === DarkAppColors
    val backdropAlpha = if (isDark) 0.6f else 0.4f
    val cardBg = if (isDark) colors.chromeSurfaceElevated else colors.chromeSurface

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag("shortcuts_backdrop"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .heightIn(max = 600.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .shadow(16.dp, AppShapes.xl)
                    .clip(AppShapes.xl)
                    .drawBehind { drawRect(cardBg) }
                    .border(1.dp, colors.chromeBorder, AppShapes.xl)
                    .padding(Spacing.xxxl)
                    .testTag("shortcuts_overlay"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = packStringResource(Res.string.shortcuts_title),
                            style = AppTypography.heading,
                            color = colors.chromeTextPrimary,
                        )
                        RwTooltip(tooltip = packStringResource(Res.string.common_close)) {
                            RwIconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = packStringResource(Res.string.shortcuts_close),
                                )
                            }
                        }
                    }

                    val isWeb = currentRuntimeContext() == RuntimeContext.BROWSER
                    val shortcuts = if (isWeb) {
                        ShortcutsByCategory.mapValues { (_, list) ->
                            list.filter { !it.desktopOnly }
                        }
                    } else ShortcutsByCategory

                    val categories = listOf(
                        ShortcutCategory.NAVIGATION to packStringResource(Res.string.shortcuts_category_navigation),
                        ShortcutCategory.ACTIONS to packStringResource(Res.string.shortcuts_category_actions),
                        ShortcutCategory.EDITOR to packStringResource(Res.string.shortcuts_category_editor),
                    )

                    categories.forEachIndexed { index, (category, title) ->
                        val items = shortcuts[category] ?: return@forEachIndexed
                        if (items.isEmpty()) return@forEachIndexed

                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = Spacing.lg),
                                thickness = 1.dp,
                                color = colors.chromeBorder,
                            )
                        }

                        Text(
                            text = title,
                            style = AppTypography.subheading,
                            color = colors.chromeTextSecondary,
                            modifier = Modifier.padding(
                                top = if (index == 0) Spacing.lg else 0.dp,
                                bottom = Spacing.md,
                            ),
                        )

                        items.forEach { shortcut ->
                            ShortcutRow(shortcut)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: KeyboardShortcut) {
    val colors = LocalAppColors.current
    val parts = shortcut.displayParts()
    val description = shortcutDescriptionResource(shortcut.descriptionKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = AppTypography.body,
            color = colors.chromeTextPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            parts.forEach { part ->
                KeyBadge(part)
            }
        }
    }
}

@Composable
private fun KeyBadge(text: String) {
    val colors = LocalAppColors.current
    val badgeBorderDarker = colors.chromeBorder.copy(alpha = 0.8f)

    Text(
        text = text,
        style = AppTypography.code.copy(fontWeight = FontWeight.Medium),
        color = colors.chromeTextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = 24.dp)
            .clip(AppShapes.xs)
            .drawBehind {
                drawRect(colors.chromeSurface)
                val inset = 4.dp.toPx()
                val strokePx = 2.dp.toPx()
                drawLine(
                    color = badgeBorderDarker,
                    start = Offset(inset, size.height - strokePx / 2),
                    end = Offset(size.width - inset, size.height - strokePx / 2),
                    strokeWidth = strokePx,
                )
            }
            .border(1.dp, colors.chromeBorder, AppShapes.xs)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Resolves a shortcut description key to a localized string via packStringResource. */
@Composable
private fun shortcutDescriptionResource(key: String): String = when (key) {
    "shortcut_nav_projects" -> packStringResource(Res.string.shortcut_nav_projects)
    "shortcut_nav_releases" -> packStringResource(Res.string.shortcut_nav_releases)
    "shortcut_nav_connections" -> packStringResource(Res.string.shortcut_nav_connections)
    "shortcut_nav_teams" -> packStringResource(Res.string.shortcut_nav_teams)
    "shortcut_nav_back" -> packStringResource(Res.string.shortcut_nav_back)
    "shortcut_action_search" -> packStringResource(Res.string.shortcut_action_search)
    "shortcut_action_create" -> packStringResource(Res.string.shortcut_action_create)
    "shortcut_action_refresh" -> packStringResource(Res.string.shortcut_action_refresh)
    "shortcut_action_save" -> packStringResource(Res.string.shortcut_action_save)
    "shortcut_action_help" -> packStringResource(Res.string.shortcut_action_help)
    "shortcut_action_theme" -> packStringResource(Res.string.shortcut_action_theme)
    "shortcut_editor_undo" -> packStringResource(Res.string.shortcut_editor_undo)
    "shortcut_editor_redo" -> packStringResource(Res.string.shortcut_editor_redo)
    "shortcut_editor_copy" -> packStringResource(Res.string.shortcut_editor_copy)
    "shortcut_editor_paste" -> packStringResource(Res.string.shortcut_editor_paste)
    "shortcut_editor_select_all" -> packStringResource(Res.string.shortcut_editor_select_all)
    "shortcut_editor_delete" -> packStringResource(Res.string.shortcut_editor_delete)
    else -> key
}
