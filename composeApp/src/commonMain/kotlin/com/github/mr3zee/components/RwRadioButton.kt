package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom radio button replacing M3 RadioButton.
 * Circle with filled inner dot when selected.
 */
@Composable
fun RwRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary

    val borderColor by animateColorAsState(
        targetValue = if (selected) primary else colors.inputBorder,
        animationSpec = tween(durationMillis = 100),
    )

    val dotColor by animateColorAsState(
        targetValue = if (selected) primary else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .border(1.5.dp, borderColor, AppShapes.pill)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .drawBehind {
                    drawCircle(color = dotColor)
                }
        )
    }
}
