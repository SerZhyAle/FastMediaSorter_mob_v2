package com.sza.fastmediasorter.core.format

import android.content.Context
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

/**
 * S2795: pins the two claims the seam exists to make - the app's unit system decides the clock length
 * and the field order, and neither the device's clock switch nor the interface language may override
 * them - plus the conversion coefficients, where a wrong factor produces a plausible wrong number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class QuantityFormatterTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val defaultLocale: Locale = Locale.getDefault()
    private val defaultZone: TimeZone = TimeZone.getDefault()

    /** 2026-03-05, 14:07 UTC. */
    private val moment = 1772719620000L

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreEnvironment() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `metric renders a 24 hour clock`() {
        Locale.setDefault(Locale.US)
        val text = newFormatter().format(Quantity.Instant(moment), UnitSystem.METRIC)
        assertEquals("14:07", text)
    }

    @Test
    fun `imperial renders a 12 hour clock with a marker`() {
        Locale.setDefault(Locale.US)
        val text = newFormatter().format(Quantity.Instant(moment), UnitSystem.IMPERIAL)
        assertTrue(text, text.startsWith("2:07"))
        assertTrue(text, text.contains("PM", ignoreCase = true))
    }

    @Test
    fun `metric puts the year first even in a locale that writes the day first`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val text = newFormatter().format(Quantity.Date(moment), UnitSystem.METRIC)
        assertEquals("2026-03-05", text)
    }

    @Test
    fun `metric keeps the 24 hour clock in a locale that writes 12`() {
        Locale.setDefault(Locale.US)
        val text = newFormatter().format(Quantity.Instant(moment), UnitSystem.METRIC)
        assertEquals("14:07", text)
    }

    @Test
    fun `imperial puts the month first even in a locale that writes the day first`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val text = newFormatter().format(Quantity.Date(moment), UnitSystem.IMPERIAL)
        assertEquals("03/05/2026", text)
    }

    @Test
    fun `speed converts at the boundary values`() {
        assertEquals(0.0, UnitScale.metresPerSecondToKmh(0.0), TOLERANCE)
        assertEquals(3.6, UnitScale.metresPerSecondToKmh(1.0), TOLERANCE)
        assertEquals(2.2369362920544, UnitScale.metresPerSecondToMph(1.0), TOLERANCE)
        assertEquals(223.69362920544, UnitScale.metresPerSecondToMph(HUNDRED), TOLERANCE)
    }

    @Test
    fun `altitude converts at the boundary values`() {
        assertEquals(0.0, UnitScale.metresToFeet(0.0), TOLERANCE)
        assertEquals(3.280839895013, UnitScale.metresToFeet(1.0), TOLERANCE)
        assertEquals(328.0839895013, UnitScale.metresToFeet(HUNDRED), TOLERANCE)
    }

    @Test
    fun `speed carries a localized unit in both systems`() {
        Locale.setDefault(Locale.US)
        val formatter = newFormatter()
        val metric = formatter.format(Quantity.Speed(1.0), UnitSystem.METRIC)
        val imperial = formatter.format(Quantity.Speed(1.0), UnitSystem.IMPERIAL)
        assertEquals("4 km/h", metric)
        assertEquals("2 mph", imperial)
    }

    @Test
    fun `spoken form names the unit in full`() {
        Locale.setDefault(Locale.US)
        val spoken = newFormatter().contentDescription(Quantity.Altitude(FIVE), UnitSystem.METRIC)
        assertEquals("5 metres", spoken)
    }

    private fun newFormatter() = QuantityFormatter(context)

    private companion object {
        const val TOLERANCE = 1e-9
        const val HUNDRED = 100.0
        const val FIVE = 5.0
    }
}
