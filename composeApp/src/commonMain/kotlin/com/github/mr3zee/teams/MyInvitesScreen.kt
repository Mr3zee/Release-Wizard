package com.github.mr3zee.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.TeamInvite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInvitesScreen(
    viewModel: MyInvitesViewModel,
    onBack: () -> Unit,
    onInviteAccepted: () -> Unit = {},
) {
    val invites by viewModel.invites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Invites") },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Text("Back") }
                },
            )
        },
        modifier = Modifier.testTag("my_invites_screen"),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && invites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadInvites() }) { Text("Retry") }
                }
            }
        } else if (invites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No pending invites.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (error != null) {
                    item {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
                items(invites, key = { it.id }) { invite ->
                    InviteCard(
                        invite = invite,
                        onAccept = {
                            viewModel.acceptInvite(invite.id) { onInviteAccepted() }
                        },
                        onDecline = { viewModel.declineInvite(invite.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteCard(invite: TeamInvite, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("invite_card_${invite.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(invite.teamName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Invited by ${invite.invitedByUsername}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAccept) { Text("Accept") }
                TextButton(onClick = onDecline) { Text("Decline") }
            }
        }
    }
}
