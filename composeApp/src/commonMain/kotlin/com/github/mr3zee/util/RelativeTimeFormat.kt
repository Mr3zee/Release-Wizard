package com.github.mr3zee.util

import androidx.compose.runtime.Composable
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats a timestamp (epoch millis) as a human-readable relative time string.
 * Uses kotlin.time.Clock (KMP-safe, no java.time dependency).
 * Uses string resources for language pack support.
 */
@Composable
fun formatRelativeTimestamp(timestampMillis: Long): String {
    val instant = kotlin.time.Instant.fromEpochMilliseconds(timestampMillis)
    val now = kotlin.time.Clock.System.now()
    val elapsed = now - instant

    return when {
        elapsed < 0.seconds -> packStringResource(Res.string.automation_checked_just_now)
        elapsed < 60.seconds -> packStringResource(Res.string.automation_checked_just_now)
        elapsed < 60.minutes -> {
            val mins = elapsed.inWholeMinutes.toInt()
            packPluralStringResource(Res.plurals.automation_checked_minutes_ago, mins, mins)
        }
        elapsed < 24.hours -> {
            val hrs = elapsed.inWholeHours.toInt()
            packPluralStringResource(Res.plurals.automation_checked_hours_ago, hrs, hrs)
        }
        elapsed < 7.days -> {
            val d = elapsed.inWholeDays.toInt()
            packPluralStringResource(Res.plurals.automation_checked_days_ago, d, d)
        }
        elapsed < 30.days -> {
            val weeks = (elapsed.inWholeDays / 7).toInt()
            packPluralStringResource(Res.plurals.relative_time_weeks_ago, weeks, weeks)
        }
        else -> {
            val months = (elapsed.inWholeDays / 30).toInt().coerceAtLeast(1)
            packPluralStringResource(Res.plurals.relative_time_months_ago, months, months)
        }
    }
}
