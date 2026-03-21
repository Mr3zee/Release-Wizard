package com.github.mr3zee.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.github.mr3zee.theme.LocalAppColors

/**
 * Drop-in replacement for M3 DropdownMenuItem with visible hover feedback.
 * M3 ripple is globally suppressed in this project, so DropdownMenuItems
 * appear dead on hover. This wrapper adds a background color change + pointer cursor.
 */
@Composable
fun RwDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = LocalAppColors.current
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bg = if (isHovered && enabled) colors.buttonGhostHover else Color.Transparent

    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
            .background(bg),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}
