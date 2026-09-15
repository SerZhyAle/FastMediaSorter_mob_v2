package com.sza.fastmediasorter.core.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrictModeViolationReportTest {

    @Test
    fun `names the first app frame past StrictMode and relay frames`() {
        val violation = throwableWithFrames(
            "android.os.StrictMode\$AndroidBlockGuardPolicy",
            "libcore.io.BlockGuardOs",
            "com.sza.fastmediasorter.core.debug.StrictModeHelper",
            "com.sza.fastmediasorter.data.SettingsStore",
            "android.app.Activity",
        )

        assertEquals(
            "at com.sza.fastmediasorter.data.SettingsStore.run(Source.kt:1)",
            StrictModeViolationReport.describeSite(violation),
        )
    }

    @Test
    fun `marks a stack without app code and names the platform caller`() {
        val violation = throwableWithFrames(
            "android.os.StrictMode\$AndroidBlockGuardPolicy",
            "java.io.File",
            "android.view.ViewRootImpl",
        )

        assertEquals(
            "no app frame, at android.view.ViewRootImpl.run(Source.kt:1)",
            StrictModeViolationReport.describeSite(violation),
        )
    }

    @Test
    fun `reports first occurrence and powers of ten only`() {
        val key = "counter-test-${System.nanoTime()}"
        val reported = (1..100).mapNotNull { StrictModeViolationReport.occurrence(key) }

        assertEquals(listOf(1, 10, 100), reported)
    }

    @Test
    fun `suppresses second occurrence`() {
        val key = "suppress-test-${System.nanoTime()}"
        StrictModeViolationReport.occurrence(key)

        assertNull(StrictModeViolationReport.occurrence(key))
    }

    private fun throwableWithFrames(vararg classNames: String): Throwable = Throwable().apply {
        stackTrace = classNames.map { StackTraceElement(it, "run", "Source.kt", 1) }.toTypedArray()
    }
}
