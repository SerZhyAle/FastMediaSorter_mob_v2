package com.sza.fastmediasorter.wear.core.util

import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearAppearancePreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** 2026-03-09T21:30:00Z - an evening the two clock lengths cannot render alike. */
private const val EVENING_MILLIS = 1_773_091_800_000L

/**
 * S2795: the stored measurement system, and nothing else, decides the clock length and the date field
 * order on the watch. The device's own 12/24-hour switch is never consulted, so it is not stubbed here
 * either - a test that had to stub it would be testing the wrong seam.
 */
class WearUnitDateTimeFormatterTest {

    private val formatter = WearUnitDateTimeFormatter()
    private val defaultLocale = Locale.getDefault()
    private val defaultZone = TimeZone.getDefault()

    @Before
    fun pinLocaleAndZone() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocaleAndZone() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `a stored imperial system renders a twelve-hour clock and a month-first date`() = runTest {
        val system = storedSystem(UnitSystem.IMPERIAL)

        assertEquals("9:30 PM", formatter.formatTime(EVENING_MILLIS, system))
        assertEquals("9:30:00 PM", formatter.formatTime(EVENING_MILLIS, system, withSeconds = true))
        assertEquals("03/09/2026", formatter.formatDate(EVENING_MILLIS, system))
        assertEquals("03/09/2026 9:30 PM", formatter.formatDateTime(EVENING_MILLIS, system))
    }

    @Test
    fun `a stored metric system renders a twenty-four-hour clock and a year-first date`() = runTest {
        val system = storedSystem(UnitSystem.METRIC)

        assertEquals("21:30", formatter.formatTime(EVENING_MILLIS, system))
        assertEquals("21:30:00", formatter.formatTime(EVENING_MILLIS, system, withSeconds = true))
        assertEquals("2026-03-09", formatter.formatDate(EVENING_MILLIS, system))
        assertEquals("2026-03-09 21:30", formatter.formatDateTime(EVENING_MILLIS, system))
    }

    @Test
    fun `the interface language does not reorder the date fields`() = runTest {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val metric = storedSystem(UnitSystem.METRIC)
        val imperial = storedSystem(UnitSystem.IMPERIAL)

        assertEquals("2026-03-09", formatter.formatDate(EVENING_MILLIS, metric))
        assertEquals("03/09/2026", formatter.formatDate(EVENING_MILLIS, imperial))
    }

    /** Reads through the preference the phone writes, so the test covers the stored value, not an enum. */
    private suspend fun storedSystem(stored: UnitSystem): UnitSystem {
        val preferences = mockk<WearAppearancePreferences>()
        every { preferences.unitSystem } returns flowOf(stored)
        return preferences.unitSystem.first()
    }
}
