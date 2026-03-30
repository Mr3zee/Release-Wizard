package com.github.mr3zee.mockteamcity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.mockteamcity.model.*

@Composable
fun BuildTypesPanel(state: MockTeamCityState) {
    val mockState by state.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Build Configurations", style = MaterialTheme.typography.headlineSmall)

        AddBuildTypeForm(state, mockState.projects)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (bt in mockState.buildTypes) {
                BuildTypeCard(bt, state, mockState.buildTypes)
            }
        }
    }
}

@Composable
private fun AddBuildTypeForm(state: MockTeamCityState, projects: List<TcProject>) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        Button(onClick = { showForm = true }) {
            Text("Add Build Configuration")
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
            var projectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }

            Text("New Build Configuration", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("Build Type ID") },
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
                value = projectId,
                onValueChange = { projectId = it },
                label = { Text("Project ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (id.isNotBlank() && name.isNotBlank() && projectId.isNotBlank()) {
                            state.addBuildType(
                                TcBuildType(id = id, name = name, projectId = projectId)
                            )
                            id = ""
                            name = ""
                            showForm = false
                        }
                    },
                    enabled = id.isNotBlank() && name.isNotBlank() && projectId.isNotBlank(),
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
private fun BuildTypeCard(
    bt: TcBuildType,
    state: MockTeamCityState,
    allBuildTypes: List<TcBuildType>,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(bt.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ID: ${bt.id}  |  Project: ${bt.projectId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Collapse" else "Edit")
                    }
                    TextButton(
                        onClick = { state.removeBuildType(bt.id) },
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
                BuildTypeEditor(bt, state, allBuildTypes)
            }
        }
    }
}

@Composable
private fun BuildTypeEditor(
    bt: TcBuildType,
    state: MockTeamCityState,
    allBuildTypes: List<TcBuildType>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Parameters section
        Text("Parameters", style = MaterialTheme.typography.labelLarge)

        for ((index, param) in bt.parameters.withIndex()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${param.name} = ${param.value}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (param.typeRawValue.isNotEmpty()) {
                    Text(
                        "(${param.typeRawValue})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    val updated = bt.copy(
                        parameters = bt.parameters.toMutableList().apply { removeAt(index) },
                    )
                    state.updateBuildType(bt.id, updated)
                }) {
                    Text("X", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        AddParameterForm(bt, state)

        // Snapshot dependencies
        HorizontalDivider()
        Text("Snapshot Dependencies", style = MaterialTheme.typography.labelLarge)

        for (depId in bt.snapshotDependencyIds) {
            val depName = allBuildTypes.find { it.id == depId }?.name ?: depId
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$depName ($depId)", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val updated = bt.copy(
                        snapshotDependencyIds = bt.snapshotDependencyIds - depId,
                    )
                    state.updateBuildType(bt.id, updated)
                }) {
                    Text("X", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        AddSnapshotDepForm(bt, state, allBuildTypes)

        // Artifact templates
        HorizontalDivider()
        Text("Artifact Templates", style = MaterialTheme.typography.labelLarge)

        for (artifact in bt.artifactTemplates) {
            ArtifactDisplay(artifact, indent = 0)
        }

        AddArtifactForm(bt, state)
    }
}

@Composable
private fun AddParameterForm(bt: TcBuildType, state: MockTeamCityState) {
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
                    val updated = bt.copy(
                        parameters = bt.parameters + TcParameter(
                            name = pName,
                            value = pValue,
                            typeRawValue = pType,
                        ),
                    )
                    state.updateBuildType(bt.id, updated)
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
private fun AddSnapshotDepForm(
    bt: TcBuildType,
    state: MockTeamCityState,
    allBuildTypes: List<TcBuildType>,
) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        TextButton(onClick = { showForm = true }) { Text("+ Add Dependency") }
        return
    }

    var depId by remember { mutableStateOf("") }
    val available = allBuildTypes.filter { it.id != bt.id && it.id !in bt.snapshotDependencyIds }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = depId,
            onValueChange = { depId = it },
            label = { Text("Build Type ID") },
            singleLine = true,
        )
        Button(
            onClick = {
                if (depId.isNotBlank()) {
                    val updated = bt.copy(
                        snapshotDependencyIds = bt.snapshotDependencyIds + depId,
                    )
                    state.updateBuildType(bt.id, updated)
                    depId = ""
                    showForm = false
                }
            },
            enabled = depId.isNotBlank(),
        ) {
            Text("Add")
        }
        OutlinedButton(onClick = { showForm = false }) {
            Text("Cancel")
        }
    }

    if (available.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Quick add:", style = MaterialTheme.typography.labelSmall)
            for (a in available.take(5)) {
                TextButton(onClick = {
                    val updated = bt.copy(
                        snapshotDependencyIds = bt.snapshotDependencyIds + a.id,
                    )
                    state.updateBuildType(bt.id, updated)
                    showForm = false
                }) {
                    Text(a.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AddArtifactForm(bt: TcBuildType, state: MockTeamCityState) {
    var showForm by remember { mutableStateOf(false) }

    if (!showForm) {
        TextButton(onClick = { showForm = true }) { Text("+ Add Artifact") }
        return
    }

    var aName by remember { mutableStateOf("") }
    var aSize by remember { mutableStateOf("1024") }
    var isDir by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = aName,
            onValueChange = { aName = it },
            label = { Text("File/Dir Name") },
            singleLine = true,
        )
        if (!isDir) {
            OutlinedTextField(
                value = aSize,
                onValueChange = { aSize = it },
                label = { Text("Size") },
                singleLine = true,
                modifier = Modifier.width(100.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isDir, onCheckedChange = { isDir = it })
            Text("Directory", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                if (aName.isNotBlank()) {
                    val artifact = TcArtifactTemplate(
                        name = aName,
                        size = if (isDir) 0 else (aSize.toLongOrNull() ?: 0),
                        children = if (isDir) listOf(
                            TcArtifactTemplate(name = "placeholder.txt", size = 0)
                        ) else emptyList(),
                    )
                    val updated = bt.copy(
                        artifactTemplates = bt.artifactTemplates + artifact,
                    )
                    state.updateBuildType(bt.id, updated)
                    aName = ""
                    aSize = "1024"
                    isDir = false
                    showForm = false
                }
            },
            enabled = aName.isNotBlank(),
        ) {
            Text("Add")
        }
        OutlinedButton(onClick = { showForm = false }) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ArtifactDisplay(artifact: TcArtifactTemplate, indent: Int) {
    val prefix = "  ".repeat(indent)
    val icon = if (artifact.children.isNotEmpty()) "dir" else "file"
    val sizeInfo = if (artifact.children.isEmpty()) " (${artifact.size} bytes)" else ""
    Text(
        "$prefix[$icon] ${artifact.name}$sizeInfo",
        style = MaterialTheme.typography.bodySmall,
    )
    for (child in artifact.children) {
        ArtifactDisplay(child, indent + 1)
    }
}
