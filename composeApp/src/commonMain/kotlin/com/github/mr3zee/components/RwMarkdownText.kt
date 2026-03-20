package com.github.mr3zee.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography

/**
 * Read-only markdown renderer that integrates with the app's design system.
 *
 * Disables image rendering (security: prevents tracking pixels / network probing)
 * and whitelists link schemes to https/http only (prevents javascript: XSS on web target).
 */
@Composable
fun RwMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isBlank()) return

    val appColors = LocalAppColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val parentUriHandler = LocalUriHandler.current

    val safeUriHandler = remember(parentUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val normalized = uri.trim().lowercase()
                if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
                    parentUriHandler.openUri(uri)
                }
                // Silently ignore javascript:, data:, vbscript:, etc.
            }
        }
    }

    val colors = remember(appColors) {
        DefaultMarkdownColors(
            text = appColors.chromeTextPrimary,
            codeBackground = appColors.chromeSurfaceSecondary,
            inlineCodeBackground = appColors.chromeSurfaceSecondary,
            dividerColor = appColors.chromeBorder,
            tableBackground = Color.Transparent,
        )
    }

    val typography = remember(primaryColor) {
        DefaultMarkdownTypography(
            h1 = AppTypography.display,
            h2 = AppTypography.heading,
            h3 = AppTypography.subheading,
            h4 = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
            h5 = AppTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            h6 = AppTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
            text = AppTypography.body,
            code = AppTypography.code,
            inlineCode = AppTypography.code,
            quote = AppTypography.bodySmall.copy(fontWeight = FontWeight.Normal),
            paragraph = AppTypography.body,
            ordered = AppTypography.body,
            bullet = AppTypography.body,
            list = AppTypography.body,
            textLink = TextLinkStyles(
                style = AppTypography.body.copy(color = primaryColor).toSpanStyle(),
            ),
            table = AppTypography.bodySmall,
        )
    }

    // Disable image rendering for security
    val components = remember {
        markdownComponents(
            image = { /* no-op: images disabled */ },
        )
    }

    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        Markdown(
            content = markdown,
            modifier = modifier,
            colors = colors,
            typography = typography,
            components = components,
        )
    }
}
