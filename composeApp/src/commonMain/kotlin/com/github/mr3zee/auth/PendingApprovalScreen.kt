package com.github.mr3zee.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.AppLogo
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import kotlinx.coroutines.delay
import releasewizard.composeapp.generated.resources.*

@Composable
fun PendingApprovalScreen(
    onCheckStatus: () -> Unit,
    onSignOut: () -> Unit,
) {
    var hasCheckedManually by remember { mutableStateOf(false) }

    // Auto-poll every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            onCheckStatus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pending_approval_screen"),
        contentAlignment = Alignment.Center,
    ) {
        RwCard(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(Spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                AppLogo(modifier = Modifier.size(72.dp))

                Text(
                    text = packStringResource(Res.string.pending_approval_title),
                    style = AppTypography.display,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = packStringResource(Res.string.pending_approval_message),
                    style = AppTypography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = packStringResource(Res.string.pending_approval_hint),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (hasCheckedManually) {
                    Text(
                        text = packStringResource(Res.string.pending_approval_still_pending),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pending_approval_still_pending"),
                    )
                }

                RwButton(
                    onClick = {
                        onCheckStatus()
                        // If we're still on this screen after the check, the user is still pending.
                        // The flag makes the "still pending" message appear on next recomposition.
                        hasCheckedManually = true
                    },
                    variant = RwButtonVariant.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_approval_check_status"),
                ) {
                    Text(packStringResource(Res.string.pending_approval_check_status))
                }

                RwButton(
                    onClick = onSignOut,
                    variant = RwButtonVariant.Ghost,
                    modifier = Modifier.testTag("pending_approval_sign_out"),
                ) {
                    Text(packStringResource(Res.string.pending_approval_sign_out))
                }
            }
        }
    }
}
