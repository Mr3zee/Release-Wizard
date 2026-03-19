package com.github.mr3zee.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.LocalAppColors

/**
 * Themed switch wrapping M3 [Switch].
 * Uses the app's primary color for the checked-state track and applies a focus ring.
 */
@Composable
fun RwSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.focusRing(
            cornerRadius = 16.dp,
            interactionSource = interactionSource,
        ),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.buttonPrimaryText,
            checkedTrackColor = primary,
            checkedBorderColor = primary,
            uncheckedThumbColor = colors.chromeTextSecondary,
            uncheckedTrackColor = colors.inputBg,
            uncheckedBorderColor = colors.inputBorder,
        ),
        interactionSource = interactionSource,
    )
}
