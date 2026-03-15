package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom text field replacing M3 OutlinedTextField.
 * Uses Foundation BasicTextField with custom border + background decoration.
 * No M3 label animation — uses a simple placeholder.
 *
 * Internal horizontal padding is 16.dp to match M3 OutlinedTextField's padding
 * (critical for TemplateAutocompleteField dropdown positioning).
 */
@Composable
fun RwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = AppTypography.body,
    label: String? = null,
    placeholder: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = LocalAppColors.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> colors.inputBorderFocused
            else -> colors.inputBorder
        },
        animationSpec = tween(durationMillis = 100),
    )

    val borderWidth = if (isFocused) 2.dp else 1.dp
    val bgColor = colors.inputBg
    val disabledAlpha = if (enabled) 1f else 0.6f

    Column(modifier = Modifier.alpha(disabledAlpha)) {
        if (label != null) {
            Text(
                text = label,
                style = AppTypography.label,
                color = if (isError) MaterialTheme.colorScheme.error else colors.chromeTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(color = colors.chromeTextPrimary),
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = modifier,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.sm)
                        .background(bgColor, AppShapes.sm)
                        .border(borderWidth, borderColor, AppShapes.sm)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.inputPlaceholder,
                            )
                        }
                        CompositionLocalProvider(LocalContentColor provides colors.chromeTextPrimary) {
                            innerTextField()
                        }
                    }
                    if (trailingIcon != null) {
                        trailingIcon()
                    }
                }
            },
        )

        if (supportingText != null) {
            Box(modifier = Modifier.padding(top = 4.dp, start = 16.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isError) MaterialTheme.colorScheme.error else colors.chromeTextTertiary,
                ) {
                    supportingText()
                }
            }
        }
    }
}
