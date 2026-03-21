package com.github.mr3zee.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing

@Composable
fun SidebarNavItem(
    icon: ImageVector,
    activeIcon: ImageVector,
    label: String,
    isActive: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
    contentColor: Color = Color.Unspecified,
    semanticRole: Role = Role.Tab,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val resolvedContentColor = when {
        contentColor != Color.Unspecified -> contentColor
        isActive -> colors.sidebarActiveText
        else -> colors.chromeTextSecondary
    }

    val bgColor = when {
        isPressed && isActive -> colors.sidebarActiveHoverBg
        isPressed -> colors.buttonGhostPress
        isActive && isHovered -> colors.sidebarActiveHoverBg
        isActive -> colors.sidebarActiveBg
        isHovered -> colors.buttonGhostHover
        else -> Color.Transparent
    }

    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(150),
    )

    val displayIcon = if (isActive) activeIcon else icon

    val itemContent: @Composable () -> Unit = if (isCollapsed) {
        {
            RwTooltip(tooltip = label) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        displayIcon,
                        contentDescription = label,
                        tint = resolvedContentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    } else {
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    displayIcon,
                    contentDescription = null,
                    tint = resolvedContentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = label,
                    style = if (isActive) {
                        AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        AppTypography.body
                    },
                    color = resolvedContentColor,
                )
            }
        }
    }

    // Active indicator bar for rail mode
    val indicatorColor = colors.sidebarActiveText

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
            .focusRing(cornerRadius = 10.dp, interactionSource = interactionSource)
            .clip(AppShapes.md)
            .background(animatedBgColor)
            .then(
                if (isActive && isCollapsed) {
                    Modifier.drawBehind {
                        val barWidth = 4.dp.toPx()
                        val barHeight = size.height - 16.dp.toPx()
                        val barY = (size.height - barHeight) / 2f
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(0f, barY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f),
                        )
                    }
                } else Modifier
            )
            .semantics {
                role = semanticRole
                selected = isActive
            }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    ) {
        itemContent()
    }
}
