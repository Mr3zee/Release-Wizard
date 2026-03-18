package com.github.mr3zee.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.theme.AppTypography
import releasewizard.composeapp.generated.resources.*

@Composable
fun MavenTriggerCreatedDialog(
    trigger: MavenTrigger,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.maven_created_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${trigger.groupId}:${trigger.artifactId}",
                    style = AppTypography.subheading,
                )
                Text(packStringResource(Res.string.maven_created_message))
            }
        },
        confirmButton = {
            RwButton(
                onClick = onDismiss,
                variant = RwButtonVariant.Primary,
            ) {
                Text(packStringResource(Res.string.common_ok))
            }
        },
    )
}
