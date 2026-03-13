package com.github.mr3zee.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Release Wizard",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (isRegisterMode) "Create an account" else "Sign in to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; viewModel.dismissError() },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username"),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.dismissError() },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password"),
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = {
                        if (isRegisterMode) {
                            viewModel.register(username, password)
                        } else {
                            viewModel.login(username, password)
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (isRegisterMode) "register_button" else "login_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (isRegisterMode) "Create Account" else "Sign In")
                    }
                }

                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        viewModel.dismissError()
                    },
                    modifier = Modifier.testTag("toggle_auth_mode"),
                ) {
                    Text(
                        if (isRegisterMode) "Already have an account? Sign in"
                        else "Don't have an account? Register",
                    )
                }
            }
        }
    }
}
