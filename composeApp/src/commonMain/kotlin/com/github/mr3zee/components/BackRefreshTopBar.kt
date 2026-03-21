package com.github.mr3zee.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

/**
 * A TopAppBar with back navigation button, optional refresh icon, and a linear progress
 * indicator below the bar when refreshing. Extracted from AuditLogScreen,
 * MyInvitesScreen, ProfileScreen, AdminUsersScreen, and similar patterns.
 *
 * @param title The title displayed in the app bar.
 * @param onBack Callback when the back button is pressed.
 * @param onRefresh Callback when the refresh icon button is pressed. When null, no refresh button is shown.
 * @param isRefreshing Whether a refresh operation is in progress.
 * @param isManualRefresh Whether the refresh was manually triggered (affects progress opacity).
 * @param isLoading Whether an initial load is in progress (hides progress indicator when true).
 * @param showTooltipOnBack Whether to wrap the back button in a tooltip.
 * @param extraActions Additional action buttons to show before the refresh button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackRefreshTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isManualRefresh: Boolean = false,
    isLoading: Boolean = false,
    showTooltipOnBack: Boolean = false,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    Box {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                val backButton: @Composable () -> Unit = {
                    RwButton(
                        onClick = onBack,
                        variant = RwButtonVariant.Ghost,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = packStringResource(Res.string.common_navigate_back),
                        )
                        Text(packStringResource(Res.string.common_back))
                    }
                }
                if (showTooltipOnBack) {
                    RwTooltip(tooltip = packStringResource(Res.string.common_back)) {
                        backButton()
                    }
                } else {
                    backButton()
                }
            },
            actions = {
                extraActions?.invoke(this)
                if (onRefresh != null) {
                    RefreshIconButton(
                        onClick = onRefresh,
                        isRefreshing = isRefreshing,
                        isManualRefresh = isManualRefresh,
                    )
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
}
