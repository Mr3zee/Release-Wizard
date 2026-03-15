package com.github.mr3zee.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.util.resolve
import org.jetbrains.compose.resources.stringResource
import releasewizard.composeapp.generated.resources.*

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
                    text = stringResource(Res.string.auth_app_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (isRegisterMode) stringResource(Res.string.auth_create_account) else stringResource(Res.string.auth_sign_in_continue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; viewModel.dismissError() },
                    label = { Text(stringResource(Res.string.auth_username)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username"),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.dismissError() },
                    label = { Text(stringResource(Res.string.auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password"),
                )

                val currentError = error
                if (currentError != null) {
                    Text(
                        text = currentError.resolve(),
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
                        Text(if (isRegisterMode) stringResource(Res.string.auth_create_account_button) else stringResource(Res.string.auth_sign_in_button))
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
                        if (isRegisterMode) stringResource(Res.string.auth_toggle_to_signin)
                        else stringResource(Res.string.auth_toggle_to_register),
                    )
                }
            }
        }
    }
}
