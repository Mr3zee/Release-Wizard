package com.github.mr3zee.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing

/**
 * Generic empty state composable with an icon, primary text, and optional secondary text.
 * Used across list screens (teams, audit log, admin users, invites, etc.) when no items exist.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    secondaryMessage: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Icon(
            icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = message,
            style = AppTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (secondaryMessage != null) {
            Text(
                text = secondaryMessage,
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            action()
        }
    }
}
