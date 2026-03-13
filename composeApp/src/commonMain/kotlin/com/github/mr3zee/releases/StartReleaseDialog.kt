package com.github.mr3zee.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate

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
        title = { Text("Start Release") },
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
                    label = { Text("Project") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
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
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("start_release_dialog"),
    )
}
