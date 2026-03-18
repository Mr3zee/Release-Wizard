package com.github.mr3zee.automation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun CreateWebhookTriggerDialog(
    isSaving: Boolean,
    onConfirm: (CreateTriggerRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.webhook_create_title)) },
        text = {
            Text(packStringResource(Res.string.webhook_parameters_label))
        },
        confirmButton = {
            RwButton(
                onClick = { onConfirm(CreateTriggerRequest()) },
                variant = RwButtonVariant.Primary,
                enabled = !isSaving,
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}
