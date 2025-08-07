package io.github.mr3zee.rwizard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mr3zee.rwizard.domain.model.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: UUID,
    onNavigateBack: () -> Unit,
    onEditProject: (UUID) -> Unit,
    onStartRelease: (UUID) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Project Detail Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Project ID: $projectId")
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { onEditProject(projectId) }) {
                    Text("Edit Project")
                }
                Button(onClick = { onStartRelease(projectId) }) {
                    Text("Start Release")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    projectId: UUID?,
    onNavigateBack: () -> Unit,
    onSaveProject: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (projectId == null) "Create Project" else "Edit Project") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (projectId == null) "Create Project Screen" else "Edit Project Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (projectId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Project ID: $projectId")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onSaveProject) {
                Text("Save Project")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseCreationScreen(
    projectId: UUID,
    onNavigateBack: () -> Unit,
    onStartRelease: (UUID) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Release") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Create Release Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Project ID: $projectId")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onStartRelease(UUID.v4()) }) {
                Text("Start Release")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseMonitorScreen(
    releaseId: UUID,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Release Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Release Monitor Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Release ID: $releaseId")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    onNavigateBack: () -> Unit,
    onCreateConnection: () -> Unit,
    onEditConnection: (UUID) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Connections Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCreateConnection) {
                Text("Create Connection")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditorScreen(
    connectionId: UUID?,
    onNavigateBack: () -> Unit,
    onSaveConnection: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (connectionId == null) "Create Connection" else "Edit Connection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (connectionId == null) "Create Connection Screen" else "Edit Connection Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (connectionId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connection ID: $connectionId")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onSaveConnection) {
                Text("Save Connection")
            }
        }
    }
}
