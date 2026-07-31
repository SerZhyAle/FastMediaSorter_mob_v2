package com.sza.fastmediasorter.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProgressReporterTest {

    private var nowMs = 0L
    private val reporter = TransferProgressReporter { nowMs }

    @Test
    fun `calculates rate across the sample window`() {
        reporter.report("copy", 0L, 1_000L, "ui", 0L)
        nowMs = 2_000L

        val report = reporter.report("copy", 400L, 1_000L, "ui", 0L)

        assertEquals(40, report.percent)
        assertEquals(200L, report.speedBytesPerSecond)
    }

    @Test
    fun `throttles consumers independently`() {
        reporter.report("copy", 0L, 1_000L, "notification", 1_000L)
        nowMs = 500L

        val notification = reporter.report("copy", 100L, 1_000L, "notification", 1_000L)
        val dialog = reporter.report("copy", 100L, 1_000L, "dialog", 1_000L)

        assertFalse(notification.shouldPublish)
        assertTrue(dialog.shouldPublish)
    }

    @Test
    fun `terminal report clears state before a new operation`() {
        reporter.report("copy", 0L, 100L, "ui", 1_000L)
        nowMs = 100L
        reporter.report("copy", 100L, 100L, "ui", 1_000L)

        nowMs = 200L
        val newOperation = reporter.report("copy", 0L, 100L, "ui", 1_000L)

        assertTrue(newOperation.shouldPublish)
        assertEquals(0L, newOperation.speedBytesPerSecond)
    }

    @Test
    fun `operation ids keep samples isolated`() {
        reporter.report("copy-a", 0L, 1_000L, "ui", 0L)
        reporter.report("copy-b", 0L, 1_000L, "ui", 0L)
        nowMs = 1_000L
        reporter.report("copy-a", 500L, 1_000L, "ui", 0L)

        val secondOperation = reporter.report("copy-b", 100L, 1_000L, "ui", 0L)

        assertEquals(100L, secondOperation.speedBytesPerSecond)
    }
}
