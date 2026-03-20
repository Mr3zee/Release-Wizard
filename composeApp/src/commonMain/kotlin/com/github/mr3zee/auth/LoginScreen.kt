package com.github.mr3zee.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isRegisterMode) {
        confirmPassword = ""
        showPassword = false
        showConfirmPassword = false
    }

    val canSubmit = username.isNotBlank() && password.isNotBlank() && !isLoading
        && (!isRegisterMode || (password == confirmPassword && confirmPassword.isNotBlank()))
    val onSubmit: () -> Unit = {
        if (canSubmit) {
            if (isRegisterMode) {
                viewModel.register(username, password)
            } else {
                viewModel.login(username, password)
            }
        }
    }

    LaunchedEffect(Unit) {
        usernameFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login_screen"),
        contentAlignment = Alignment.Center,
    ) {
        RwCard(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(Spacing.lg),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = packStringResource(Res.string.auth_app_title),
                    style = AppTypography.display,
                )
                Text(
                    text = if (isRegisterMode) packStringResource(Res.string.auth_create_account) else packStringResource(Res.string.auth_sign_in_continue),
                    style = AppTypography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RwTextField(
                    value = username,
                    onValueChange = { username = it; viewModel.dismissError() },
                    label = packStringResource(Res.string.auth_username),
                    placeholder = packStringResource(Res.string.auth_username),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                passwordFocusRequester.requestFocus()
                                true
                            } else false
                        }
                        .testTag("login_username"),
                )

                RwTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.dismissError() },
                    label = packStringResource(Res.string.auth_password),
                    placeholder = packStringResource(Res.string.auth_password),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    supportingText = if (isRegisterMode) {
                        { Text(packStringResource(Res.string.auth_password_requirements)) }
                    } else null,
                    trailingIcon = {
                        RwTooltip(tooltip = if (showPassword) packStringResource(Res.string.common_hide_password) else packStringResource(Res.string.common_show_password)) {
                            RwIconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.focusProperties { canFocus = false }.testTag("login_password_toggle_visibility"),
                            ) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) packStringResource(Res.string.common_hide_password)
                                        else packStringResource(Res.string.common_show_password),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                if (isRegisterMode) {
                                    confirmPasswordFocusRequester.requestFocus()
                                } else {
                                    onSubmit()
                                }
                                true
                            } else false
                        }
                        .testTag("login_password"),
                )

                AnimatedVisibility(
                    visible = isRegisterMode,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    val confirmPasswordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
                    RwTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; viewModel.dismissError() },
                        label = packStringResource(Res.string.auth_confirm_password),
                        placeholder = packStringResource(Res.string.auth_confirm_password),
                        singleLine = true,
                        isError = confirmPasswordMismatch,
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        supportingText = if (confirmPasswordMismatch) {
                            { Text(packStringResource(Res.string.auth_password_mismatch)) }
                        } else null,
                        trailingIcon = {
                            RwTooltip(tooltip = if (showConfirmPassword) packStringResource(Res.string.common_hide_password) else packStringResource(Res.string.common_show_password)) {
                                RwIconButton(
                                    onClick = { showConfirmPassword = !showConfirmPassword },
                                    modifier = Modifier.focusProperties { canFocus = false }.testTag("login_confirm_password_toggle_visibility"),
                                ) {
                                    Icon(
                                        if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showConfirmPassword) packStringResource(Res.string.common_hide_password)
                                            else packStringResource(Res.string.common_show_password),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(confirmPasswordFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                    onSubmit()
                                    true
                                } else false
                            }
                            .testTag("login_confirm_password"),
                    )
                }

                AnimatedVisibility(visible = error != null) {
                    val currentError = error
                    if (currentError != null) {
                        Text(
                            text = currentError.resolve(),
                            color = MaterialTheme.colorScheme.error,
                            style = AppTypography.bodySmall,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().testTag("login_error"),
                        )
                    }
                }

                RwButton(
                    onClick = onSubmit,
                    variant = RwButtonVariant.Primary,
                    enabled = canSubmit,
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
                        Text(if (isRegisterMode) packStringResource(Res.string.auth_create_account_button) else packStringResource(Res.string.auth_sign_in_button))
                    }
                }

                RwButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        viewModel.dismissError()
                    },
                    variant = RwButtonVariant.Ghost,
                    modifier = Modifier.testTag("toggle_auth_mode"),
                ) {
                    Text(
                        if (isRegisterMode) packStringResource(Res.string.auth_toggle_to_signin)
                        else packStringResource(Res.string.auth_toggle_to_register),
                    )
                }
            }
        }
    }
}
