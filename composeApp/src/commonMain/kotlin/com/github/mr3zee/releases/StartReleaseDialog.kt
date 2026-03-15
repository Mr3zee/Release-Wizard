package com.github.mr3zee.releases

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartReleaseDialog(
    projects: List<ProjectTemplate>,
    onStart: (ProjectId) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedProject by remember { mutableStateOf<ProjectTemplate?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.start_release_title)) },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.testTag("project_dropdown"),
            ) {
                OutlinedTextField(
                    value = selectedProject?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(packStringResource(Res.string.start_release_project_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProject = project
                                expanded = false
                            },
                            modifier = Modifier.testTag("project_option_${project.id.value}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedProject?.let { onStart(it.id) } },
                enabled = selectedProject != null,
                modifier = Modifier.testTag("start_release_confirm"),
            ) {
                Text(packStringResource(Res.string.start_release_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
        modifier = Modifier.testTag("start_release_dialog"),
    )
}
