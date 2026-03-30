package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.TestPanelState
import com.github.mr3zee.testpanel.model.TcProject

@Composable
fun ProjectsPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Projects", style = MaterialTheme.typography.headlineSmall)

        // Add project form
        AddProjectForm(state)

        // Project list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (project in mockState.projects) {
                ProjectCard(project, state)
            }
        }
    }
}

@Composable
private fun AddProjectForm(state: TestPanelState) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        Button(onClick = { showForm = true }) {
            Text("Add Project")
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var id by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            var parentId by remember { mutableStateOf("_Root") }

            Text("New Project", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("Project ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = parentId,
                onValueChange = { parentId = it },
                label = { Text("Parent Project ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (id.isNotBlank() && name.isNotBlank()) {
                            state.addProject(
                                TcProject(
                                    id = id,
                                    name = name,
                                    parentProjectId = parentId.takeIf { it.isNotBlank() },
                                )
                            )
                            id = ""
                            name = ""
                            parentId = "_Root"
                            showForm = false
                        }
                    },
                    enabled = id.isNotBlank() && name.isNotBlank(),
                ) {
                    Text("Add")
                }
                OutlinedButton(onClick = { showForm = false }) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: TcProject, state: TestPanelState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(project.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "ID: ${project.id}  |  Parent: ${project.parentProjectId ?: "none"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (project.id != "_Root") {
                TextButton(
                    onClick = { state.removeProject(project.id) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Remove")
                }
            }
        }
    }
}
