package com.github.mr3zee.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.AppLogo
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import releasewizard.composeapp.generated.resources.*

@Composable
fun ResetPasswordScreen(
    viewModel: ResetPasswordViewModel,
    onGoToLogin: () -> Unit,
) {
    val isValidating by viewModel.isValidating.collectAsState()
    val isTokenValid by viewModel.isTokenValid.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()
    val error by viewModel.error.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val resolvedError = error?.resolve()
    val displayError = validationError ?: resolvedError

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reset_password_screen"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Validating token
            isValidating -> {
                CircularProgressIndicator()
            }

            // Token invalid
            isTokenValid == false -> {
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
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = packStringResource(Res.string.reset_password_invalid),
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        RwButton(
                            onClick = onGoToLogin,
                            variant = RwButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(packStringResource(Res.string.reset_password_go_to_login))
                        }
                    }
                }
            }

            // Success
            isComplete -> {
                RwCard(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .padding(Spacing.lg)
                        .testTag("reset_password_success"),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = packStringResource(Res.string.reset_password_success),
                            style = AppTypography.body,
                            textAlign = TextAlign.Center,
                        )
                        RwButton(
                            onClick = onGoToLogin,
                            variant = RwButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(packStringResource(Res.string.reset_password_go_to_login))
                        }
                    }
                }
            }

            // Password form (token valid, not yet completed)
            isTokenValid == true -> {
                val passwordMismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                val canSubmit = newPassword.isNotBlank() && confirmPassword.isNotBlank() &&
                    !passwordMismatch && !isSubmitting

                RwCard(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .padding(Spacing.lg)
                        .testTag("reset_password_form"),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        AppLogo(modifier = Modifier.size(72.dp))
                        Text(
                            text = packStringResource(Res.string.reset_password_title),
                            style = AppTypography.display,
                        )

                        RwTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; viewModel.dismissError() },
                            label = packStringResource(Res.string.reset_password_new_password),
                            singleLine = true,
                            visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            supportingText = {
                                Text(packStringResource(Res.string.auth_password_requirements))
                            },
                            trailingIcon = {
                                RwTooltip(
                                    tooltip = if (showNewPassword) {
                                        packStringResource(Res.string.common_hide_password)
                                    } else {
                                        packStringResource(Res.string.common_show_password)
                                    },
                                ) {
                                    RwIconButton(
                                        onClick = { showNewPassword = !showNewPassword },
                                        modifier = Modifier.focusProperties { canFocus = false },
                                    ) {
                                        Icon(
                                            if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showNewPassword) {
                                                packStringResource(Res.string.common_hide_password)
                                            } else {
                                                packStringResource(Res.string.common_show_password)
                                            },
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        RwTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; viewModel.dismissError() },
                            label = packStringResource(Res.string.reset_password_confirm_password),
                            singleLine = true,
                            isError = passwordMismatch,
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            supportingText = if (passwordMismatch) {
                                { Text(packStringResource(Res.string.reset_password_mismatch)) }
                            } else null,
                            trailingIcon = {
                                RwTooltip(
                                    tooltip = if (showConfirmPassword) {
                                        packStringResource(Res.string.common_hide_password)
                                    } else {
                                        packStringResource(Res.string.common_show_password)
                                    },
                                ) {
                                    RwIconButton(
                                        onClick = { showConfirmPassword = !showConfirmPassword },
                                        modifier = Modifier.focusProperties { canFocus = false },
                                    ) {
                                        Icon(
                                            if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showConfirmPassword) {
                                                packStringResource(Res.string.common_hide_password)
                                            } else {
                                                packStringResource(Res.string.common_show_password)
                                            },
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (displayError != null) {
                            Text(
                                text = displayError,
                                color = MaterialTheme.colorScheme.error,
                                style = AppTypography.bodySmall,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RwButton(
                            onClick = {
                                viewModel.resetPassword(newPassword, confirmPassword)
                            },
                            variant = RwButtonVariant.Primary,
                            enabled = canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_password_submit"),
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(packStringResource(Res.string.reset_password_submit))
                            }
                        }
                    }
                }
            }
        }
    }
}
