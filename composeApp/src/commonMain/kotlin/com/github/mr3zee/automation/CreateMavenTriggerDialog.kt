package com.github.mr3zee.automation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.CreateMavenTriggerRequest
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun CreateMavenTriggerDialog(
    isSaving: Boolean,
    onConfirm: (CreateMavenTriggerRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var repoUrl by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var artifactId by remember { mutableStateOf("") }
    var parameterKey by remember { mutableStateOf("version") }
    var includeSnapshots by remember { mutableStateOf(false) }

    val isValid = repoUrl.isNotBlank() &&
        (repoUrl.startsWith("http://") || repoUrl.startsWith("https://")) &&
        groupId.isNotBlank() &&
        artifactId.isNotBlank() &&
        parameterKey.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.maven_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val repoUrlInvalid = repoUrl.isNotBlank() &&
                    !repoUrl.startsWith("http://") && !repoUrl.startsWith("https://")
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text(packStringResource(Res.string.maven_repo_url_label)) },
                    placeholder = { Text(packStringResource(Res.string.maven_repo_url_hint)) },
                    isError = repoUrlInvalid,
                    supportingText = if (repoUrlInvalid) {
                        { Text("Must start with http:// or https://", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = { Text(packStringResource(Res.string.maven_group_id_label)) },
                    placeholder = { Text(packStringResource(Res.string.maven_group_id_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artifactId,
                    onValueChange = { artifactId = it },
                    label = { Text(packStringResource(Res.string.maven_artifact_id_label)) },
                    placeholder = { Text(packStringResource(Res.string.maven_artifact_id_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = parameterKey,
                    onValueChange = { parameterKey = it },
                    label = { Text(packStringResource(Res.string.maven_parameter_key_label)) },
                    placeholder = { Text(packStringResource(Res.string.maven_parameter_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = includeSnapshots,
                        onCheckedChange = { includeSnapshots = it },
                    )
                    Text(packStringResource(Res.string.maven_include_snapshots_label))
                }
            }
        },
        confirmButton = {
            RwButton(
                onClick = {
                    onConfirm(
                        CreateMavenTriggerRequest(
                            repoUrl = repoUrl.trim(),
                            groupId = groupId.trim(),
                            artifactId = artifactId.trim(),
                            parameterKey = parameterKey.trim(),
                            includeSnapshots = includeSnapshots,
                        )
                    )
                },
                variant = RwButtonVariant.Primary,
                enabled = isValid && !isSaving,
            ) {
                Text(if (isSaving) packStringResource(Res.string.common_saving) else packStringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}
