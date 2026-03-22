package com.github.mr3zee.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.editor_markdown_edit
import releasewizard.composeapp.generated.resources.editor_markdown_preview

private enum class MarkdownFieldMode { EDIT, PREVIEW }

/**
 * Markdown text field with edit/preview toggle.
 *
 * In edit mode, shows a multiline [RwTextField].
 * In preview mode, shows rendered markdown via [RwMarkdownText].
 * When [readOnly] is true, only shows the rendered preview without the toggle.
 */
@Composable
fun RwMarkdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    maxLength: Int = 2000,
    testTag: String = "markdown_field",
) {
    val colors = LocalAppColors.current
    var mode by remember { mutableStateOf(MarkdownFieldMode.EDIT) }

    Column(modifier = modifier) {
        // Label
        if (label != null) {
            Text(label, style = AppTypography.label)
            Spacer(Modifier.height(Spacing.xs))
        }

        // Toggle chips (hidden in readOnly mode)
        if (!readOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RwChip(
                    selected = mode == MarkdownFieldMode.EDIT,
                    onClick = { mode = MarkdownFieldMode.EDIT },
                    label = { Text(packStringResource(Res.string.editor_markdown_edit), style = AppTypography.label) },
                    enabled = enabled,
                    role = Role.Tab,
                    modifier = Modifier.weight(1f).focusProperties { canFocus = false }.testTag("${testTag}_edit_tab"),
                )
                RwChip(
                    selected = mode == MarkdownFieldMode.PREVIEW,
                    onClick = { mode = MarkdownFieldMode.PREVIEW },
                    label = { Text(packStringResource(Res.string.editor_markdown_preview), style = AppTypography.label) },
                    enabled = enabled,
                    role = Role.Tab,
                    modifier = Modifier.weight(1f).focusProperties { canFocus = false }.testTag("${testTag}_preview_tab"),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        // Content area
        if (readOnly || mode == MarkdownFieldMode.PREVIEW) {
            if (value.isBlank()) {
                Text(
                    text = placeholder ?: "",
                    style = AppTypography.body,
                    color = colors.inputPlaceholder,
                )
            } else {
                RwMarkdownText(
                    markdown = value,
                    modifier = Modifier.fillMaxWidth().testTag("${testTag}_preview"),
                )
            }
        } else {
            val isOverLimit = value.length > maxLength
            RwTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                singleLine = false,
                maxLines = 8,
                enabled = enabled,
                isError = isOverLimit,
                supportingText = {
                    Text(
                        "${value.length} / $maxLength",
                        style = AppTypography.caption,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error else colors.chromeTextTertiary,
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("${testTag}_input"),
            )
        }
    }
}
