package com.github.mr3zee.automation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.util.copyToClipboard
import releasewizard.composeapp.generated.resources.*

@Composable
fun WebhookSecretDialog(
    secret: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},  // Force user to explicitly confirm
        title = { Text(packStringResource(Res.string.webhook_secret_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Secret display with copy button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { copyToClipboard(secret) },
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = packStringResource(Res.string.common_copy_to_clipboard),
                        )
                    }
                }
                // Warning text
                Text(
                    text = packStringResource(Res.string.webhook_secret_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            RwButton(
                onClick = onDismiss,
                variant = RwButtonVariant.Primary,
            ) {
                Text(packStringResource(Res.string.webhook_secret_saved))
            }
        },
    )
}
