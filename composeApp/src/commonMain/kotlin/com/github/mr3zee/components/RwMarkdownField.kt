package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

        // Segmented toggle (hidden in readOnly mode)
        if (!readOnly) {
            val segmentShape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(segmentShape)
                    .border(1.dp, colors.chipBorder, segmentShape)
                    .drawBehind { drawRect(colors.chipBg) },
            ) {
                SegmentTab(
                    selected = mode == MarkdownFieldMode.EDIT,
                    onClick = { mode = MarkdownFieldMode.EDIT },
                    label = packStringResource(Res.string.editor_markdown_edit),
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("${testTag}_edit_tab"),
                )
                SegmentTab(
                    selected = mode == MarkdownFieldMode.PREVIEW,
                    onClick = { mode = MarkdownFieldMode.PREVIEW },
                    label = packStringResource(Res.string.editor_markdown_preview),
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("${testTag}_preview_tab"),
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

@Composable
private fun SegmentTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.chipBgSelected else colors.chipBg,
        animationSpec = tween(durationMillis = 100),
    )
    val textColor = if (selected) colors.chipTextSelected else colors.chipText

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind { drawRect(bgColor) }
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Text(label, style = AppTypography.label)
        }
    }
}
