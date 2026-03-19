package com.github.mr3zee.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.i18n.packStringResource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import releasewizard.composeapp.generated.resources.*

/**
 * Inline confirmation banner replacing AlertDialog for confirmations.
 * Appears with expandVertically animation inside the content area.
 */
@Composable
fun RwInlineConfirmation(
    visible: Boolean,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = true,
    extraAction: Pair<String, () -> Unit>? = null,
    testTag: String = "inline_confirmation",
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(250),
        ) + fadeIn(tween(150)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(250),
        ) + fadeOut(tween(100)),
        modifier = modifier,
    ) {
        val focusRequester = remember { FocusRequester() }
        var confirmEnabled by remember { mutableStateOf(false) }

        // Debounce + focus: enable confirm button after 300ms and request focus for Escape handling
        LaunchedEffect(Unit) {
            confirmEnabled = false
            kotlinx.coroutines.yield() // Ensure layout is complete before requesting focus
            focusRequester.requestFocus()
            delay(300.milliseconds)
            confirmEnabled = true
        }

        val containerColor = if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (isDestructive) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        RwCard(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else false
                }
                .testTag(testTag),
            containerColor = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    color = contentColor,
                    style = AppTypography.body,
                    modifier = Modifier.weight(1f),
                )
                if (extraAction != null) {
                    RwButton(
                        onClick = extraAction.second,
                        variant = RwButtonVariant.Ghost,
                        modifier = Modifier.testTag("${testTag}_extra"),
                    ) {
                        Text(extraAction.first)
                    }
                }
                RwButton(
                    onClick = onDismiss,
                    variant = RwButtonVariant.Ghost,
                    modifier = Modifier.testTag("${testTag}_cancel"),
                ) {
                    Text(packStringResource(Res.string.common_cancel))
                }
                RwButton(
                    onClick = onConfirm,
                    variant = if (isDestructive) RwButtonVariant.Danger else RwButtonVariant.Primary,
                    enabled = confirmEnabled,
                    modifier = Modifier.testTag("${testTag}_confirm"),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
