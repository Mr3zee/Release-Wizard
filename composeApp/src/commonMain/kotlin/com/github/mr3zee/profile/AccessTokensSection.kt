package com.github.mr3zee.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.github.mr3zee.api.CreatePatResponse
import com.github.mr3zee.api.PatInfo
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwDropdownMenu
import com.github.mr3zee.components.RwDropdownMenuItem
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.i18n.packStringResource
import org.jetbrains.compose.resources.StringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.copyToClipboard
import com.github.mr3zee.util.formatTimestamp
import com.github.mr3zee.util.resolve
import kotlin.time.Clock
import kotlin.time.Instant
import releasewizard.composeapp.generated.resources.*

/** Expiry option for the create token dropdown */
private data class ExpiryOption(val days: Int?, val labelRes: StringResource, val isCustom: Boolean = false)

private val EXPIRY_OPTIONS = listOf(
    ExpiryOption(30, Res.string.pat_expiry_30d),
    ExpiryOption(60, Res.string.pat_expiry_60d),
    ExpiryOption(90, Res.string.pat_expiry_90d),
    ExpiryOption(365, Res.string.pat_expiry_1y),
    ExpiryOption(null, Res.string.pat_expiry_never),
    ExpiryOption(null, Res.string.pat_expiry_custom, isCustom = true),
)

@Composable
fun AccessTokensSection(
    patViewModel: PatViewModel,
    snackbarHostState: SnackbarHostState,
    onFormOpenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens by patViewModel.tokens.collectAsState()
    val isLoading by patViewModel.isLoading.collectAsState()
    val newlyCreatedToken by patViewModel.newlyCreatedToken.collectAsState()
    val error by patViewModel.error.collectAsState()
    val successMessage by patViewModel.successMessage.collectAsState()
    val createSuccess by patViewModel.createSuccess.collectAsState()

    var showCreateForm by remember { mutableStateOf(false) }
    var tokenName by remember { mutableStateOf("") }
    var selectedExpiry by remember { mutableStateOf(EXPIRY_OPTIONS.first()) }
    var customDays by remember { mutableStateOf("") }
    var revokeConfirmId by remember { mutableStateOf<String?>(null) }

    // Notify parent when any form/confirmation is open (for keyboard shortcut blocking)
    LaunchedEffect(showCreateForm, revokeConfirmId) {
        onFormOpenChanged(showCreateForm || revokeConfirmId != null)
    }

    // Close form on successful creation (#5: don't close before async completes)
    LaunchedEffect(createSuccess) {
        if (createSuccess) {
            showCreateForm = false
            tokenName = ""
            customDays = ""
            selectedExpiry = EXPIRY_OPTIONS.first()
            patViewModel.consumeCreateSuccess()
        }
    }

    // Pre-resolve composable strings for use in LaunchedEffect
    val errorText = error?.resolve()
    val revokedText = packStringResource(Res.string.pat_revoked)
    val copiedText = packStringResource(Res.string.pat_token_copied)

    // Show error snackbar (#16: key on error object, not resolved text)
    LaunchedEffect(error) {
        val text = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Long)
        patViewModel.dismissError()
    }

    // Show success snackbar
    LaunchedEffect(successMessage) {
        successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(revokedText, duration = SnackbarDuration.Short)
        patViewModel.dismissSuccess()
    }

    Column(modifier = modifier.testTag("pat_section")) {
        // Section header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                packStringResource(Res.string.pat_section_title),
                style = AppTypography.subheading,
            )
            val tokenCount = tokens?.size ?: 0
            if (tokenCount > 0) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                RwBadge(
                    text = "$tokenCount",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            packStringResource(Res.string.pat_section_description),
            style = AppTypography.bodySmall,
            color = LocalAppColors.current.chromeTextMetadata,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        // One-time token display
        val created = newlyCreatedToken
        if (created != null) {
            NewTokenBanner(
                token = created,
                onDismiss = { patViewModel.dismissNewlyCreatedToken() },
                snackbarHostState = snackbarHostState,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Loading indicator (#15)
        val currentTokens = tokens
        if (currentTokens == null && isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Token list
        if (currentTokens != null && currentTokens.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                currentTokens.forEach { token ->
                    TokenItem(
                        token = token,
                        isRevokeConfirming = revokeConfirmId == token.id,
                        onRevokeClick = { revokeConfirmId = token.id },
                        onRevokeConfirm = {
                            patViewModel.revokeToken(token.id)
                            revokeConfirmId = null
                        },
                        onRevokeDismiss = { revokeConfirmId = null },
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        } else if (currentTokens != null && !isLoading) {
            // Empty state (#20: use onSurfaceVariant)
            Text(
                packStringResource(Res.string.pat_empty),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        }

        // Create button
        RwButton(
            onClick = { showCreateForm = true },
            variant = RwButtonVariant.Secondary,
            enabled = !showCreateForm && !isLoading,
            modifier = Modifier.testTag("pat_create_button"),
        ) {
            Text(packStringResource(Res.string.pat_create_button))
        }

        // Create form (#5: form stays open until async succeeds via createSuccess)
        val effectiveExpiryDays = if (selectedExpiry.isCustom) customDays.toIntOrNull() else selectedExpiry.days
        val canSubmit = tokenName.isNotBlank() && !isLoading &&
            (!selectedExpiry.isCustom || (customDays.toIntOrNull() ?: 0) > 0)

        RwInlineForm(
            visible = showCreateForm,
            title = packStringResource(Res.string.pat_create_form_title),
            onDismiss = {
                showCreateForm = false
                tokenName = ""
                customDays = ""
                selectedExpiry = EXPIRY_OPTIONS.first()
            },
            onSubmit = if (canSubmit) {
                { patViewModel.createToken(tokenName.trim(), effectiveExpiryDays) }
            } else null,
            testTag = "pat_create_form",
            actions = {
                RwButton(
                    onClick = { patViewModel.createToken(tokenName.trim(), effectiveExpiryDays) },
                    enabled = canSubmit,
                    modifier = Modifier.testTag("pat_create_submit"),
                ) {
                    Text(packStringResource(Res.string.common_create))
                }
            },
        ) {
            RwTextField(
                value = tokenName,
                onValueChange = { tokenName = it.take(100) },
                label = packStringResource(Res.string.pat_token_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pat_create_name_field"),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            // Expiry dropdown (#22)
            ExpiryDropdown(
                selected = selectedExpiry,
                onSelect = { selectedExpiry = it },
                customDays = customDays,
                onCustomDaysChange = { customDays = it },
            )
        }
    }
}

@Composable
private fun ExpiryDropdown(
    selected: ExpiryOption,
    onSelect: (ExpiryOption) -> Unit,
    customDays: String,
    onCustomDaysChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            packStringResource(Res.string.pat_expiry_label),
            style = AppTypography.bodySmall,
            color = LocalAppColors.current.chromeTextMetadata,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box {
            RwButton(
                onClick = { expanded = true },
                variant = RwButtonVariant.Secondary,
                modifier = Modifier.testTag("pat_expiry_dropdown"),
            ) {
                Text(packStringResource(selected.labelRes))
            }
            RwDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                EXPIRY_OPTIONS.forEach { option ->
                    RwDropdownMenuItem(
                        text = { Text(packStringResource(option.labelRes)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        // Custom days input field
        if (selected.isCustom) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            RwTextField(
                value = customDays,
                onValueChange = { value -> onCustomDaysChange(value.filter { it.isDigit() }.take(4)) },
                label = packStringResource(Res.string.pat_expiry_custom_days_label),
                singleLine = true,
                modifier = Modifier.width(160.dp).testTag("pat_expiry_custom_days"),
            )
        }
        // Warning for non-expiring tokens
        if (!selected.isCustom && selected.days == null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                packStringResource(Res.string.pat_expiry_never_warning),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NewTokenBanner(
    token: CreatePatResponse,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    // Auto-copy on creation (#8: with snackbar feedback)
    val copiedMsg = packStringResource(Res.string.pat_token_copied)
    LaunchedEffect(token.token) {
        copyToClipboard(token.token)
        snackbarHostState.showSnackbar(copiedMsg, duration = SnackbarDuration.Short)
    }

    RwCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pat_newly_created_token"),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        // #18: use Spacing.md + spacedBy pattern matching WebhookSecretInlineCard
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    token.name,
                    style = AppTypography.subheading,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            RwTextField(
                value = token.token,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = AppTypography.code,
                trailingIcon = {
                    // #12: tooltip on copy button
                    RwTooltip(tooltip = packStringResource(Res.string.common_copy_to_clipboard)) {
                        RwIconButton(
                            onClick = { copyToClipboard(token.token) },
                            modifier = Modifier.testTag("pat_copy_token_button"),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = packStringResource(Res.string.common_copy_to_clipboard))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                packStringResource(Res.string.pat_token_warning),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            // #18: Primary variant to match WebhookSecretInlineCard dismiss
            RwButton(
                onClick = onDismiss,
                variant = RwButtonVariant.Primary,
                modifier = Modifier.testTag("pat_dismiss_token"),
            ) {
                Text(packStringResource(Res.string.pat_token_saved))
            }
        }
    }
}

@Composable
private fun TokenItem(
    token: PatInfo,
    isRevokeConfirming: Boolean,
    onRevokeClick: () -> Unit,
    onRevokeConfirm: () -> Unit,
    onRevokeDismiss: () -> Unit,
) {
    RwCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pat_token_item_${token.id}"),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // #6: inner Row weighted so long names don't push Revoke off-screen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        token.name,
                        style = AppTypography.subheading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    TokenStatusBadge(token)
                }
                // #7: hide Revoke button when confirmation is showing
                if (!token.revoked && !isRevokeConfirming) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    RwButton(
                        onClick = onRevokeClick,
                        variant = RwButtonVariant.Danger,
                        modifier = Modifier.testTag("pat_revoke_${token.id}"),
                    ) {
                        Text(packStringResource(Res.string.pat_revoke))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                val lastUsed = token.lastUsedAt
                Text(
                    if (lastUsed != null) {
                        packStringResource(Res.string.pat_last_used, formatTimestamp(Instant.fromEpochMilliseconds(lastUsed)))
                    } else {
                        packStringResource(Res.string.pat_never_used)
                    },
                    style = AppTypography.bodySmall,
                    color = LocalAppColors.current.chromeTextTimestamp,
                )
                Text(
                    packStringResource(Res.string.pat_created, formatTimestamp(Instant.fromEpochMilliseconds(token.createdAt))),
                    style = AppTypography.bodySmall,
                    color = LocalAppColors.current.chromeTextTimestamp,
                )
            }

            // Revoke confirmation
            RwInlineConfirmation(
                visible = isRevokeConfirming,
                message = packStringResource(Res.string.pat_revoke_confirm, token.name),
                confirmLabel = packStringResource(Res.string.pat_revoke),
                onConfirm = onRevokeConfirm,
                onDismiss = onRevokeDismiss,
                isDestructive = true,
                testTag = "pat_revoke_confirm_${token.id}",
            )
        }
    }
}

@Composable
private fun TokenStatusBadge(token: PatInfo) {
    val expiresAt = token.expiresAt
    when {
        token.revoked -> RwBadge(
            text = packStringResource(Res.string.pat_status_revoked),
            color = MaterialTheme.colorScheme.error,
        )
        expiresAt != null && expiresAt < Clock.System.now().toEpochMilliseconds() ->
            RwBadge(
                text = packStringResource(Res.string.pat_status_expired),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        expiresAt != null -> RwBadge(
            text = packStringResource(Res.string.pat_status_expires, formatTimestamp(Instant.fromEpochMilliseconds(expiresAt))),
            color = MaterialTheme.colorScheme.primary,
        )
        else -> RwBadge(
            text = packStringResource(Res.string.pat_status_active),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
