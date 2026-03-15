package com.github.mr3zee.schedules

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CronUtilsTest {

    @Test
    fun `valid cron expression parses correctly`() {
        // Every minute
        val next = CronUtils.computeNextRun("* * * * *")
        assertNotNull(next, "Valid cron '* * * * *' should produce a next run time")
    }

    @Test
    fun `midnight cron produces future time`() {
        val next = CronUtils.computeNextRun("0 0 * * *")
        assertNotNull(next, "Daily midnight cron should produce a next run time")
        assertTrue(next > Clock.System.now(), "Next run should be in the future")
    }

    @Test
    fun `hourly cron produces future time`() {
        val next = CronUtils.computeNextRun("0 * * * *")
        assertNotNull(next, "Hourly cron should produce a next run time")
        assertTrue(next > Clock.System.now(), "Next run should be in the future")
    }

    @Test
    fun `weekly cron expression parses correctly`() {
        val next = CronUtils.computeNextRun("0 9 * * 1") // Every Monday at 9:00
        assertNotNull(next, "Weekly cron should produce a next run time")
    }

    @Test
    fun `invalid cron expression returns null`() {
        val next = CronUtils.computeNextRun("not a cron expression")
        assertNull(next, "Invalid cron should return null")
    }

    @Test
    fun `empty cron expression returns null`() {
        val next = CronUtils.computeNextRun("")
        assertNull(next, "Empty cron should return null")
    }

    @Test
    fun `cron with too many fields returns null`() {
        // Unix cron has 5 fields; 6 fields is invalid for UNIX type
        val next = CronUtils.computeNextRun("0 0 * * * *")
        assertNull(next, "Cron with 6 fields should return null for UNIX type")
    }

    @Test
    fun `end of month cron parses correctly`() {
        // 28th of every month
        val next = CronUtils.computeNextRun("0 0 28 * *")
        assertNotNull(next, "End-of-month cron should produce a next run time")
    }

    @Test
    fun `specific day of week cron produces correct result`() {
        // Sunday at noon
        val next = CronUtils.computeNextRun("0 12 * * 0")
        assertNotNull(next, "Sunday noon cron should produce a next run time")
    }

    @Test
    fun `cron parser is consistent across multiple calls`() {
        val next1 = CronUtils.computeNextRun("30 2 * * *")
        val next2 = CronUtils.computeNextRun("30 2 * * *")
        assertNotNull(next1)
        assertNotNull(next2)
        // Both calls made at nearly the same time should produce similar results
        assertTrue(
            kotlin.math.abs((next1.toEpochMilliseconds() - next2.toEpochMilliseconds())) < 1000,
            "Two consecutive calls should produce nearly identical results",
        )
    }
}
