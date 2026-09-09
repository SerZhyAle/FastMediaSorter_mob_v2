package com.sza.fastmediasorter.core.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeViolationFilterTest {

    @Test
    fun `filters Samsung IdsController violations`() {
        val violation = throwableWithFrame("android.app.IdsController")

        assertTrue(StrictModeViolationFilter.isPlatformNoise(violation))
    }

    @Test
    fun `keeps unknown application violations visible`() {
        val violation = throwableWithFrame("com.sza.fastmediasorter.feature.UnknownWorker")

        assertFalse(StrictModeViolationFilter.isPlatformNoise(violation))
    }

    private fun throwableWithFrame(className: String): Throwable = Throwable().apply {
        stackTrace = arrayOf(StackTraceElement(className, "run", "Source.kt", 1))
    }
}
