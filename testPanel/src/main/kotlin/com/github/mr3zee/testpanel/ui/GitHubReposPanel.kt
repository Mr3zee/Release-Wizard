package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.*

@Composable
fun GitHubReposPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Repos & Workflows", style = MaterialTheme.typography.headlineSmall)

        AddRepoForm(state)

        if (mockState.ghRepos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No repos configured",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (repo in mockState.ghRepos) {
                    val repoKey = "${repo.owner}/${repo.repo}"
                    val workflows = mockState.ghWorkflows[repoKey].orEmpty()
                    RepoCard(repo, repoKey, workflows, state)
                }
            }
        }
    }
}

@Composable
private fun AddRepoForm(state: TestPanelState) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        Button(onClick = { showForm = true }) {
            Text("Add Repo")
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var owner by remember { mutableStateOf("") }
            var repo by remember { mutableStateOf("") }

            Text("New Repository", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it },
                label = { Text("Owner") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it },
                label = { Text("Repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (owner.isNotBlank() && repo.isNotBlank()) {
                            state.addGhRepo(GhRepo(owner = owner, repo = repo))
                            owner = ""
                            repo = ""
                            showForm = false
                        }
                    },
                    enabled = owner.isNotBlank() && repo.isNotBlank(),
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
private fun RepoCard(
    repo: GhRepo,
    repoKey: String,
    workflows: List<GhWorkflow>,
    state: TestPanelState,
) {
    var expanded by remember(repo.owner, repo.repo) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(repoKey, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${workflows.size} workflow(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Collapse" else "Workflows")
                    }
                    TextButton(
                        onClick = { state.removeGhRepo(repo.owner, repo.repo) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Remove")
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                WorkflowsSection(repoKey, workflows, state)
            }
        }
    }
}

@Composable
private fun WorkflowsSection(
    repoKey: String,
    workflows: List<GhWorkflow>,
    state: TestPanelState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workflows", style = MaterialTheme.typography.labelLarge)

        for (workflow in workflows) {
            WorkflowItem(repoKey, workflow, state)
        }

        AddWorkflowForm(repoKey, state)
    }
}

@Composable
private fun WorkflowItem(
    repoKey: String,
    workflow: GhWorkflow,
    state: TestPanelState,
) {
    var showParams by remember(workflow.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    workflow.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showParams = !showParams }) {
                    Text(
                        if (showParams) "Hide params (${workflow.inputParameters.size})"
                        else "Params (${workflow.inputParameters.size})",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(
                    onClick = { state.removeGhWorkflow(repoKey, workflow.id) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("X")
                }
            }
        }

        if (showParams) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                for ((index, param) in workflow.inputParameters.withIndex()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${param.name} = ${param.value}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            val updated = workflow.copy(
                                inputParameters = workflow.inputParameters.toMutableList().apply { removeAt(index) },
                            )
                            state.updateGhWorkflow(repoKey, workflow.id, updated)
                        }) {
                            Text("X", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                AddWorkflowParameterForm(repoKey, workflow, state)
            }
        }
    }
}

@Composable
private fun AddWorkflowParameterForm(
    repoKey: String,
    workflow: GhWorkflow,
    state: TestPanelState,
) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        TextButton(onClick = { showForm = true }) { Text("+ Add Parameter") }
        return
    }

    var pName by remember { mutableStateOf("") }
    var pValue by remember { mutableStateOf("") }
    var pType by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = pName,
            onValueChange = { pName = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = pValue,
            onValueChange = { pValue = it },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = pType,
            onValueChange = { pType = it },
            label = { Text("Type (optional)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                if (pName.isNotBlank()) {
                    val updated = workflow.copy(
                        inputParameters = workflow.inputParameters + TcParameter(
                            name = pName,
                            value = pValue,
                            typeRawValue = pType,
                        ),
                    )
                    state.updateGhWorkflow(repoKey, workflow.id, updated)
                    pName = ""
                    pValue = ""
                    pType = ""
                    showForm = false
                }
            },
            enabled = pName.isNotBlank(),
        ) {
            Text("Add")
        }
        OutlinedButton(onClick = { showForm = false }) {
            Text("Cancel")
        }
    }
}

@Composable
private fun AddWorkflowForm(repoKey: String, state: TestPanelState) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        TextButton(onClick = { showForm = true }) { Text("+ Add Workflow") }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var name by remember { mutableStateOf("") }
            var path by remember { mutableStateOf(".github/workflows/ci.yml") }
            var yamlContent by remember { mutableStateOf("") }

            Text("New Workflow", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yamlContent,
                onValueChange = { yamlContent = it },
                label = { Text("YAML Content") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                minLines = 3,
                maxLines = 8,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (name.isNotBlank() && path.isNotBlank()) {
                            state.addGhWorkflow(
                                repoKey,
                                GhWorkflow(
                                    id = 0, // will be assigned by state
                                    name = name,
                                    path = path,
                                    yamlContent = yamlContent,
                                ),
                            )
                            name = ""
                            path = ".github/workflows/ci.yml"
                            yamlContent = ""
                            showForm = false
                        }
                    },
                    enabled = name.isNotBlank() && path.isNotBlank(),
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
