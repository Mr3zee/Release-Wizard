package com.github.mr3zee.profile

import com.github.mr3zee.LocalPasswordPolicyHint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.components.BackRefreshTopBar
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwDangerZone
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.formatTimestamp
import com.github.mr3zee.util.resolve
import kotlin.time.Instant
import releasewizard.composeapp.generated.resources.*

sealed class DeleteState {
    data object Idle : DeleteState()
    data object Confirming : DeleteState()
    data object EnteringCredentials : DeleteState()
    data object Deleting : DeleteState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    currentUserRole: UserRole?,
    onBack: () -> Unit,
    onNavigateToTeam: (TeamId) -> Unit,
    onNavigateToAdminUsers: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    val userInfo by viewModel.userInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showChangeUsername by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var deleteState by remember { mutableStateOf<DeleteState>(DeleteState.Idle) }

    // Change username form state
    var newUsername by remember { mutableStateOf("") }
    var usernamePassword by remember { mutableStateOf("") }
    var showUsernamePassword by remember { mutableStateOf(false) }

    // Change password form state
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmNewPassword by remember { mutableStateOf(false) }

    // Delete account form state
    var deleteUsername by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var showDeletePassword by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)

    val resolvedError = error?.resolve()
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = dismissLabel,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissError()
    }

    val resolvedSuccess = successMessage?.resolve()
    LaunchedEffect(successMessage) {
        val msg = resolvedSuccess ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short,
        )
        viewModel.dismissSuccessMessage()
    }

    // Close forms on success
    val usernameChangeSuccess by viewModel.usernameChangeSuccess.collectAsState()
    val passwordChangeSuccess by viewModel.passwordChangeSuccess.collectAsState()

    LaunchedEffect(usernameChangeSuccess) {
        if (usernameChangeSuccess) {
            showChangeUsername = false
            newUsername = ""
            usernamePassword = ""
            showUsernamePassword = false
            viewModel.consumeUsernameChangeSuccess()
        }
    }

    LaunchedEffect(passwordChangeSuccess) {
        if (passwordChangeSuccess) {
            showChangePassword = false
            currentPassword = ""
            newPassword = ""
            confirmNewPassword = ""
            showCurrentPassword = false
            showNewPassword = false
            showConfirmNewPassword = false
            viewModel.consumePasswordChangeSuccess()
        }
    }

    // Handle delete account success via StateFlow (lifecycle-safe)
    val accountDeleteSuccess by viewModel.accountDeleteSuccess.collectAsState()
    LaunchedEffect(accountDeleteSuccess) {
        if (accountDeleteSuccess) {
            deleteState = DeleteState.Idle
            viewModel.consumeAccountDeleteSuccess()
            onAccountDeleted()
        }
    }

    // Reset delete state on error (so user can try again) — hoisted outside conditional composition
    LaunchedEffect(error) {
        if (error != null && deleteState is DeleteState.Deleting) {
            deleteState = DeleteState.EnteringCredentials
        }
    }

    val isDialogOpen = showChangeUsername || showChangePassword || deleteState != DeleteState.Idle
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(
            onRefresh = { viewModel.loadProfile() },
            hasDialogOpen = isDialogOpen,
        )
    }

    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            BackRefreshTopBar(
                title = packStringResource(Res.string.profile_title),
                onBack = onBack,
                showTooltipOnBack = true,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("profile_screen"),
    ) { padding ->
        if (isLoading && userInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Info Section ──────────────────────────────────
                RwCard(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .padding(Spacing.lg)
                        .testTag("profile_info_section"),
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            userInfo?.username ?: "",
                            style = AppTypography.heading,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("profile_username"),
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        val role = currentUserRole ?: userInfo?.role
                        if (role != null) {
                            RwBadge(
                                text = when (role) {
                                    UserRole.ADMIN -> packStringResource(Res.string.profile_role_admin)
                                    UserRole.USER -> packStringResource(Res.string.profile_role_user)
                                },
                                color = if (role == UserRole.ADMIN) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalAppColors.current.chromeTextMetadata
                                },
                                testTag = "profile_role_badge",
                            )
                        }

                        // Teams
                        val teams = userInfo?.teams ?: emptyList()
                        if (teams.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                packStringResource(Res.string.profile_teams_section),
                                style = AppTypography.subheading,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            teams.forEach { team ->
                                TeamInfoRow(
                                    team = team,
                                    onClick = { onNavigateToTeam(team.teamId) },
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                packStringResource(Res.string.profile_no_teams),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Member since
                        val createdAt = userInfo?.createdAt
                        if (createdAt != null) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                packStringResource(
                                    Res.string.profile_member_since,
                                    formatTimestamp(Instant.fromEpochMilliseconds(createdAt)),
                                ),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Change Username ──────────────────────────────
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                ) {
                    RwButton(
                        onClick = {
                            showChangeUsername = true
                            showChangePassword = false
                            newUsername = ""
                            usernamePassword = ""
                            showUsernamePassword = false
                        },
                        variant = RwButtonVariant.Secondary,
                        modifier = Modifier.testTag("profile_change_username_button"),
                    ) {
                        Text(packStringResource(Res.string.profile_change_username))
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    ChangeUsernameForm(
                        visible = showChangeUsername,
                        newUsername = newUsername,
                        onNewUsernameChange = { newUsername = it },
                        password = usernamePassword,
                        onPasswordChange = { usernamePassword = it },
                        showPassword = showUsernamePassword,
                        onTogglePassword = { showUsernamePassword = !showUsernamePassword },
                        isSubmitting = isLoading,
                        onSubmit = {
                            viewModel.changeUsername(
                                newUsername.trim(),
                                usernamePassword.ifBlank { null },
                            )
                        },
                        hasPassword = userInfo?.hasPassword != false,
                        onDismiss = {
                            showChangeUsername = false
                            newUsername = ""
                            usernamePassword = ""
                        },
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Change Password ──────────────────────────────
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                ) {
                    RwButton(
                        onClick = {
                            showChangePassword = true
                            showChangeUsername = false
                            currentPassword = ""
                            newPassword = ""
                            confirmNewPassword = ""
                            showCurrentPassword = false
                            showNewPassword = false
                            showConfirmNewPassword = false
                        },
                        variant = RwButtonVariant.Secondary,
                        modifier = Modifier.testTag("profile_change_password_button"),
                    ) {
                        Text(
                            if (userInfo?.hasPassword != false) packStringResource(Res.string.profile_change_password)
                            else packStringResource(Res.string.profile_set_password)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    ChangePasswordForm(
                        visible = showChangePassword,
                        currentPassword = currentPassword,
                        onCurrentPasswordChange = { currentPassword = it },
                        showCurrentPassword = showCurrentPassword,
                        onToggleCurrentPassword = { showCurrentPassword = !showCurrentPassword },
                        newPassword = newPassword,
                        onNewPasswordChange = { newPassword = it },
                        showNewPassword = showNewPassword,
                        onToggleNewPassword = { showNewPassword = !showNewPassword },
                        confirmNewPassword = confirmNewPassword,
                        onConfirmNewPasswordChange = { confirmNewPassword = it },
                        showConfirmNewPassword = showConfirmNewPassword,
                        onToggleConfirmNewPassword = { showConfirmNewPassword = !showConfirmNewPassword },
                        isSubmitting = isLoading,
                        onSubmit = {
                            viewModel.changePassword(
                                currentPassword.ifBlank { null },
                                newPassword,
                            )
                        },
                        onDismiss = {
                            showChangePassword = false
                            currentPassword = ""
                            newPassword = ""
                            confirmNewPassword = ""
                        },
                        hasPassword = userInfo?.hasPassword != false,
                    )
                }

                // ── Admin Tools ──────────────────────────────────
                val effectiveRole = currentUserRole ?: userInfo?.role
                if (effectiveRole == UserRole.ADMIN) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    HorizontalDivider(
                        modifier = Modifier.widthIn(max = 800.dp).padding(horizontal = Spacing.lg),
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    Column(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg)
                            .testTag("profile_admin_tools_section"),
                    ) {
                        Text(
                            packStringResource(Res.string.profile_admin_tools),
                            style = AppTypography.subheading,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwButton(
                            onClick = onNavigateToAdminUsers,
                            variant = RwButtonVariant.Secondary,
                            modifier = Modifier.testTag("profile_manage_users_button"),
                        ) {
                            Text(packStringResource(Res.string.profile_manage_users))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
                HorizontalDivider(
                    modifier = Modifier.widthIn(max = 800.dp).padding(horizontal = Spacing.lg),
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Danger Zone ──────────────────────────────────
                RwDangerZone(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .padding(horizontal = Spacing.lg),
                    testTag = "profile_danger_zone",
                ) {
                    // Idle state: show button
                    AnimatedVisibility(
                        visible = deleteState is DeleteState.Idle,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                packStringResource(Res.string.profile_delete_account_description),
                                style = AppTypography.body,
                            )
                            RwButton(
                                onClick = { deleteState = DeleteState.Confirming },
                                variant = RwButtonVariant.Danger,
                                modifier = Modifier.testTag("profile_delete_account_button"),
                            ) {
                                Text(packStringResource(Res.string.profile_delete_account))
                            }
                        }
                    }

                    // Confirming state: inline confirmation
                    RwInlineConfirmation(
                        visible = deleteState is DeleteState.Confirming,
                        message = packStringResource(Res.string.profile_delete_warning),
                        confirmLabel = packStringResource(Res.string.common_delete),
                        onConfirm = {
                            deleteState = DeleteState.EnteringCredentials
                            deleteUsername = ""
                            deletePassword = ""
                            showDeletePassword = false
                        },
                        onDismiss = { deleteState = DeleteState.Idle },
                        isDestructive = true,
                        testTag = "profile_delete_confirm",
                    )

                    // EnteringCredentials state: inline form
                    DeleteCredentialsForm(
                        visible = deleteState is DeleteState.EnteringCredentials,
                        username = deleteUsername,
                        onUsernameChange = { deleteUsername = it },
                        password = deletePassword,
                        onPasswordChange = { deletePassword = it },
                        showPassword = showDeletePassword,
                        onTogglePassword = { showDeletePassword = !showDeletePassword },
                        expectedUsername = userInfo?.username ?: "",
                        onSubmit = {
                            deleteState = DeleteState.Deleting
                            viewModel.deleteAccount(
                                deleteUsername.trim(),
                                deletePassword.ifBlank { null },
                            )
                        },
                        onDismiss = {
                            deleteState = DeleteState.Idle
                            deleteUsername = ""
                            deletePassword = ""
                        },
                        hasPassword = userInfo?.hasPassword != false,
                    )

                    // Deleting state: spinner
                    AnimatedVisibility(
                        visible = deleteState is DeleteState.Deleting,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
            )
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun TeamInfoRow(
    team: UserTeamInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            team.teamName,
            style = AppTypography.body,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        RwBadge(
            text = team.role.displayName(),
            color = LocalAppColors.current.chromeTextMetadata,
        )
    }
}

@Composable
private fun PasswordVisibilityToggle(
    showPassword: Boolean,
    onToggle: () -> Unit,
    testTag: String,
) {
    Icon(
        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        contentDescription = if (showPassword) {
            packStringResource(Res.string.common_hide_password)
        } else {
            packStringResource(Res.string.common_show_password)
        },
        modifier = Modifier
            .size(16.dp)
            .focusProperties { canFocus = false }
            .testTag(testTag)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onToggle() },
    )
}

@Composable
private fun ChangeUsernameForm(
    visible: Boolean,
    newUsername: String,
    onNewUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    hasPassword: Boolean = true,
) {
    val canSubmit = newUsername.isNotBlank() && (password.isNotBlank() || !hasPassword) && !isSubmitting

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.profile_change_username_title),
        onDismiss = onDismiss,
        dismissEnabled = !isSubmitting,
        onSubmit = if (canSubmit) onSubmit else null,
        testTag = "profile_change_username_form",
        actions = {
            RwButton(
                onClick = onSubmit,
                variant = RwButtonVariant.Primary,
                enabled = canSubmit,
                modifier = Modifier.testTag("profile_change_username_submit"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.profile_submit_username))
                }
            }
        },
    ) {
        RwTextField(
            value = newUsername,
            onValueChange = onNewUsernameChange,
            label = packStringResource(Res.string.profile_new_username),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasPassword) {
            RwTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = packStringResource(Res.string.profile_current_password),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        showPassword = showPassword,
                        onToggle = onTogglePassword,
                        testTag = "profile_change_username_password_toggle",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChangePasswordForm(
    visible: Boolean,
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    showCurrentPassword: Boolean,
    onToggleCurrentPassword: () -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    showNewPassword: Boolean,
    onToggleNewPassword: () -> Unit,
    confirmNewPassword: String,
    onConfirmNewPasswordChange: (String) -> Unit,
    showConfirmNewPassword: Boolean,
    onToggleConfirmNewPassword: () -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    hasPassword: Boolean = true,
) {
    val passwordPolicyHint = LocalPasswordPolicyHint.current
    val passwordMismatch = confirmNewPassword.isNotEmpty() && newPassword != confirmNewPassword
    val canSubmit = (currentPassword.isNotBlank() || !hasPassword) && newPassword.isNotBlank() &&
        confirmNewPassword.isNotBlank() && !passwordMismatch && !isSubmitting

    RwInlineForm(
        visible = visible,
        title = if (hasPassword) packStringResource(Res.string.profile_change_password_title)
            else packStringResource(Res.string.profile_set_password),
        onDismiss = onDismiss,
        dismissEnabled = !isSubmitting,
        onSubmit = if (canSubmit) onSubmit else null,
        testTag = "profile_change_password_form",
        actions = {
            RwButton(
                onClick = onSubmit,
                variant = RwButtonVariant.Primary,
                enabled = canSubmit,
                modifier = Modifier.testTag("profile_change_password_submit"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.profile_submit_password))
                }
            }
        },
    ) {
        if (hasPassword) {
            RwTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = packStringResource(Res.string.profile_current_password),
                singleLine = true,
                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        showPassword = showCurrentPassword,
                        onToggle = onToggleCurrentPassword,
                        testTag = "profile_change_password_current_toggle",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RwTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = packStringResource(Res.string.profile_new_password),
            singleLine = true,
            visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = {
                Text(passwordPolicyHint ?: packStringResource(Res.string.auth_password_requirements))
            },
            trailingIcon = {
                PasswordVisibilityToggle(
                    showPassword = showNewPassword,
                    onToggle = onToggleNewPassword,
                    testTag = "profile_change_password_new_toggle",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        RwTextField(
            value = confirmNewPassword,
            onValueChange = onConfirmNewPasswordChange,
            label = packStringResource(Res.string.profile_confirm_new_password),
            singleLine = true,
            isError = passwordMismatch,
            visualTransformation = if (showConfirmNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = if (passwordMismatch) {
                { Text(packStringResource(Res.string.profile_password_mismatch)) }
            } else null,
            trailingIcon = {
                PasswordVisibilityToggle(
                    showPassword = showConfirmNewPassword,
                    onToggle = onToggleConfirmNewPassword,
                    testTag = "profile_change_password_confirm_toggle",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteCredentialsForm(
    visible: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    expectedUsername: String,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    hasPassword: Boolean = true,
) {
    val canSubmit = username.trim() == expectedUsername && (password.isNotBlank() || !hasPassword)

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.profile_delete_account),
        onDismiss = onDismiss,
        onSubmit = if (canSubmit) onSubmit else null,
        testTag = "profile_delete_credentials_form",
        actions = {
            RwButton(
                onClick = onSubmit,
                variant = RwButtonVariant.Danger,
                enabled = canSubmit,
                modifier = Modifier.testTag("profile_delete_credentials_submit"),
            ) {
                Text(packStringResource(Res.string.common_delete))
            }
        },
    ) {
        Text(
            packStringResource(Res.string.profile_delete_warning_brief),
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        RwTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = packStringResource(Res.string.profile_delete_confirm_username),
            placeholder = expectedUsername,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasPassword) {
            RwTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = packStringResource(Res.string.profile_current_password),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        showPassword = showPassword,
                        onToggle = onTogglePassword,
                        testTag = "profile_delete_password_toggle",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
