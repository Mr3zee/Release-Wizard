package com.github.mr3zee.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

/**
 * Inline form card replacing AlertDialog for creation/edit forms.
 * Appears with expandVertically animation. Title row with close button,
 * content slot for form fields, and action row for confirm button.
 */
@Composable
fun RwInlineForm(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    testTag: String = "inline_form",
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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

        LaunchedEffect(Unit) {
            kotlinx.coroutines.yield() // Ensure layout is complete before requesting focus
            focusRequester.requestFocus()
        }

        RwCard(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && dismissEnabled) {
                        onDismiss()
                        true
                    } else false
                }
                .testTag(testTag),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Title row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = AppTypography.subheading,
                        modifier = Modifier.weight(1f),
                    )
                    RwIconButton(
                        onClick = onDismiss,
                        enabled = dismissEnabled,
                        modifier = Modifier.testTag("${testTag}_close"),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = packStringResource(Res.string.common_dismiss),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Form content slot
                content()

                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions()
                }
            }
        }
    }
}
