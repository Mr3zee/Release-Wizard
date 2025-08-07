package io.github.mr3zee.rwizard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mr3zee.rwizard.domain.model.UUID
import io.github.mr3zee.rwizard.domain.model.Project
import io.github.mr3zee.rwizard.ui.components.ProjectCard
import io.github.mr3zee.rwizard.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onNavigateToProject: (UUID) -> Unit,
    onCreateProject: () -> Unit,
    onNavigateToConnections: () -> Unit
) {
    // Mock data for now - will be replaced with actual data loading
    val projects by remember { mutableStateOf(emptyList<Project>()) }
    val isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Projects",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connections button
                OutlinedButton(
                    onClick = onNavigateToConnections,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Manage Connections",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connections")
                }
                
                // Create project button
                Button(
                    onClick = onCreateProject,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create Project",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Project")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Content
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            projects.isEmpty() -> {
                EmptyState(
                    title = "No projects yet",
                    subtitle = "Create your first release project to get started",
                    actionText = "Create Project",
                    onActionClick = onCreateProject
                )
            }
            
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onNavigateToProject(project.id) }
                        )
                    }
                }
            }
        }
    }
}
