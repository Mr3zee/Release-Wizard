package com.github.mr3zee.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Keys that must never be overridden by language packs — credential fields,
 * format hints, and diagnostic content that users depend on being accurate.
 */
private val NEVER_OVERRIDE = setOf(
    "connections_github_pat",
    "connections_tc_token",
    "connections_tc_webhook_secret",
    "connections_github_webhook_secret",
    "connections_maven_password",
    "connections_maven_base_url",
    "connections_slack_webhook_url",
    "connections_tc_server_url",
    "editor_template_button",
    "editor_prop_remove",
    "editor_dirty_indicator",
)

/** Matches CMP's internal SimpleStringFormatRegex — `%1$s`, `%2$d`, etc. */
private val FormatArgRegex = Regex("""%(\d+)\$[ds]""")

private fun String.replaceFormatArgs(args: Array<out Any>): String =
    FormatArgRegex.replace(this) { match ->
        val index = match.groupValues[1].toInt() - 1
        if (index in args.indices) args[index].toString() else match.value
    }

@Composable
fun packStringResource(resource: StringResource): String {
    if (resource.key in NEVER_OVERRIDE) return stringResource(resource)
    val overrides = LocalLanguagePackData.current
    return overrides.strings[resource.key] ?: stringResource(resource)
}

@Composable
fun packStringResource(resource: StringResource, vararg formatArgs: Any): String {
    if (resource.key in NEVER_OVERRIDE) return stringResource(resource, *formatArgs)
    val overrides = LocalLanguagePackData.current
    val template = overrides.strings[resource.key]
    return if (template != null) {
        template.replaceFormatArgs(formatArgs)
    } else {
        stringResource(resource, *formatArgs)
    }
}

@Composable
fun packPluralStringResource(resource: PluralStringResource, quantity: Int): String {
    if (resource.key in NEVER_OVERRIDE) return pluralStringResource(resource, quantity)
    val overrides = LocalLanguagePackData.current
    val plural = overrides.plurals[resource.key]
    return if (plural != null) {
        if (quantity == 1) plural.one else plural.other
    } else {
        pluralStringResource(resource, quantity)
    }
}

@Composable
fun packPluralStringResource(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String {
    if (resource.key in NEVER_OVERRIDE) return pluralStringResource(resource, quantity, *formatArgs)
    val overrides = LocalLanguagePackData.current
    val plural = overrides.plurals[resource.key]
    return if (plural != null) {
        val template = if (quantity == 1) plural.one else plural.other
        template.replaceFormatArgs(formatArgs)
    } else {
        pluralStringResource(resource, quantity, *formatArgs)
    }
}
