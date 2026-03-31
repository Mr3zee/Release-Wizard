package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors

@Composable
fun RwSegmentedTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagPrefix: String = "segment_tab",
) {
    val colors = LocalAppColors.current
    val segmentShape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(segmentShape)
            .border(1.dp, colors.chipBorder, segmentShape)
            .drawBehind { drawRect(colors.chipBg) },
    ) {
        tabs.forEachIndexed { index, label ->
            SegmentTab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                label = label,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_$index"),
            )
        }
    }
}

@Composable
internal fun SegmentTab(
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
