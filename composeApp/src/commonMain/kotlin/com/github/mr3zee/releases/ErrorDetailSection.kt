package com.github.mr3zee.releases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.copyToClipboard
import com.github.mr3zee.util.formatTimestamp
import kotlin.time.Instant
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun ErrorDetailSection(
    error: String,
    finishedAt: Instant?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().testTag("error_detail_section"),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = null) { expanded = !expanded }
                    .testTag("error_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = packStringResource(Res.string.common_error),
                    style = AppTypography.subheading,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) packStringResource(Res.string.releases_collapse_error) else packStringResource(Res.string.releases_expand_error),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    if (finishedAt != null) {
                        Text(
                            text = packStringResource(Res.string.releases_error_failed_at, formatTimestamp(finishedAt)),
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.testTag("error_timestamp"),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                    SelectionContainer {
                        Text(
                            text = error,
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.testTag("error_message"),
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    RwButton(
                        onClick = { copyToClipboard(error) },
                        modifier = Modifier.testTag("copy_error_button"),
                        variant = RwButtonVariant.Secondary,
                    ) {
                        Text(packStringResource(Res.string.common_copy_to_clipboard))
                    }
                }
            }
        }
    }
}
