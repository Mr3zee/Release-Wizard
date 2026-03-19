package com.github.mr3zee.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.common_danger_zone

/**
 * A visually distinct section for irreversible/destructive actions.
 * Uses error-tinted background and border to signal danger.
 */
@Composable
fun RwDangerZone(
    modifier: Modifier = Modifier,
    testTag: String = "danger_zone",
    content: @Composable () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(AppShapes.md)
            .background(errorColor.copy(alpha = 0.08f), AppShapes.md)
            .border(1.dp, errorColor.copy(alpha = 0.3f), AppShapes.md)
            .padding(Spacing.lg),
    ) {
        Text(
            text = packStringResource(Res.string.common_danger_zone),
            style = AppTypography.subheading,
            color = errorColor,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        content()
    }
}
