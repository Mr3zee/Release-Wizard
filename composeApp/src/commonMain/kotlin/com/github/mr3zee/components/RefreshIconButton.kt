package com.github.mr3zee.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.common_refresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshIconButton(
    onClick: () -> Unit,
    isRefreshing: Boolean,
    isManualRefresh: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
    )
    val spinning = isManualRefresh && isRefreshing

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(packStringResource(Res.string.common_refresh)) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        RwIconButton(
            onClick = onClick,
            modifier = Modifier.testTag("refresh_button"),
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = packStringResource(Res.string.common_refresh),
                modifier = Modifier
                    .rotate(if (spinning) rotation else 0f)
                    .testTag(if (spinning) "refresh_icon_spinning" else "refresh_icon_idle"),
            )
        }
    }
}
