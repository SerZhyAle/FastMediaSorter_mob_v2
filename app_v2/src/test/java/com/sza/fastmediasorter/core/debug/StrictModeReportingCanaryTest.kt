package com.sza.fastmediasorter.core.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeReportingCanaryTest {

    @Test
    fun `consumes a violation carrying the canary frame`() {
        val violation = throwableWithFrame(StrictModeReportingCanary::class.java.name)

        assertTrue(StrictModeReportingCanary().consume(violation, NOW_MS))
    }

    @Test
    fun `leaves an application violation to the normal report`() {
        val violation = throwableWithFrame("com.sza.fastmediasorter.feature.UnknownWorker")

        assertFalse(StrictModeReportingCanary().consume(violation, NOW_MS))
    }

    @Test
    fun `probes on the first resume of the process`() {
        assertTrue(StrictModeReportingCanary.isProbeDue(NOW_MS, lastProbeMs = 0L))
    }

    @Test
    fun `does not probe again inside the interval`() {
        assertFalse(StrictModeReportingCanary.isProbeDue(NOW_MS, lastProbeMs = NOW_MS - 1L))
    }

    @Test
    fun `probes again once the interval has passed`() {
        assertTrue(StrictModeReportingCanary.isProbeDue(NOW_MS + ONE_MINUTE_MS, lastProbeMs = NOW_MS))
    }

    private fun throwableWithFrame(className: String): Throwable = Throwable().apply {
        stackTrace = arrayOf(StackTraceElement(className, "run", "Source.kt", 1))
    }

    private companion object {
        const val NOW_MS = 100_000L
        const val ONE_MINUTE_MS = 60_000L
    }
}
