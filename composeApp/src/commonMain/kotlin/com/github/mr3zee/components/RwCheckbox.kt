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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.LocalAppColors

/**
 * Custom rounded checkbox replacing M3 Checkbox.
 * Draws a rounded rectangle with a checkmark when checked.
 */
@Composable
fun RwCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val primary = MaterialTheme.colorScheme.primary

    val bgColor by animateColorAsState(
        targetValue = if (checked) primary else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) primary else colors.inputBorder,
        animationSpec = tween(durationMillis = 100),
    )

    val checkColor by animateColorAsState(
        targetValue = if (checked) Color.White else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
    )

    val clickMod = if (onCheckedChange != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onClick = { onCheckedChange(!checked) },
        )
    } else {
        Modifier
    }

    // Reuse a single Path instance across draw frames
    val checkPath = remember { Path() }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(AppShapes.xs)
            .drawBehind {
                drawRoundRect(
                    color = bgColor,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                // Draw checkmark
                checkPath.reset()
                checkPath.moveTo(size.width * 0.2f, size.height * 0.5f)
                checkPath.lineTo(size.width * 0.42f, size.height * 0.72f)
                checkPath.lineTo(size.width * 0.8f, size.height * 0.28f)
                drawPath(
                    path = checkPath,
                    color = checkColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            .border(1.5.dp, borderColor, AppShapes.xs)
            .then(clickMod),
    )
}
