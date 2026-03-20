package com.github.mr3zee.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.LocalAppColors
import org.jetbrains.compose.resources.painterResource
import releasewizard.composeapp.generated.resources.Res
import releasewizard.composeapp.generated.resources.logo_dark_bg
import releasewizard.composeapp.generated.resources.logo_white_bg
import releasewizard.composeapp.generated.resources.app_logo_description

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = packStringResource(Res.string.app_logo_description),
) {
    val isDark = LocalAppColors.current.isDark
    val logoRes = if (isDark) Res.drawable.logo_dark_bg else Res.drawable.logo_white_bg
    Image(
        painter = painterResource(logoRes),
        contentDescription = contentDescription,
        modifier = modifier.testTag("app_logo"),
    )
}
