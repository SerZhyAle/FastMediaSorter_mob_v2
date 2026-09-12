package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.FakeWearHealthDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearExitReason
import com.sza.fastmediasorter.wear.domain.repository.WearThermalState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NINE_DAYS_THREE_HOURS_SEVEN_MINUTES = 788_820_000L
private const val CHARGE_MICRO_AMP_HOURS = 247_500

class HealthInfoContributorTest {

    @Test
    fun `a watch that answers nothing states why instead of vanishing`() = runTest {
        val section = health(FakeWearHealthDataSource().blind())

        assertTrue(section.fields.isEmpty())
        assertEquals(R.string.system_info_empty_unreadable, section.emptyReasonRes)
    }

    @Test
    fun `every thermal state has its own name`() = runTest {
        val named = WearThermalState.entries.map { state ->
            val source = FakeWearHealthDataSource().apply { thermalState = state }
            labelOf(health(source), R.string.system_info_health_thermal)
        }

        assertEquals(named.size, named.distinct().size)
    }

    @Test
    fun `every exit reason has its own name`() = runTest {
        val named = WearExitReason.entries.map { reason ->
            val source = FakeWearHealthDataSource().apply { lastExitReason = reason }
            labelOf(health(source), R.string.system_info_health_last_exit)
        }

        assertEquals(named.size, named.distinct().size)
    }

    @Test
    fun `a gauge that cannot count charge drops its line and keeps the rest`() = runTest {
        val section = health(FakeWearHealthDataSource().apply { batteryChargeCounterMicroAmpHours = null })

        assertNull(section.fields.firstOrNull { it.labelRes == R.string.system_info_health_battery_charge })
        assertTrue(section.fields.any { it.labelRes == R.string.system_info_health_uptime })
    }

    @Test
    fun `uptime is read in days, hours and minutes`() = runTest {
        val source = FakeWearHealthDataSource().apply {
            uptimeMillis = NINE_DAYS_THREE_HOURS_SEVEN_MINUTES
        }

        assertEquals("9d 03h 07m", textOf(health(source), R.string.system_info_health_uptime))
    }

    @Test
    fun `charge is reported in whole milliamp hours`() = runTest {
        val source = FakeWearHealthDataSource().apply {
            batteryChargeCounterMicroAmpHours = CHARGE_MICRO_AMP_HOURS
        }

        assertEquals("247 mAh", textOf(health(source), R.string.system_info_health_battery_charge))
    }

    @Test
    fun `a restricted background reads differently from a normal one`() = runTest {
        val limited = FakeWearHealthDataSource().apply { backgroundRestricted = true }
        val normal = FakeWearHealthDataSource().apply { backgroundRestricted = false }

        assertEquals(
            R.string.system_info_health_background_limited,
            labelOf(health(limited), R.string.system_info_health_background)
        )
        assertEquals(
            R.string.system_info_health_background_normal,
            labelOf(health(normal), R.string.system_info_health_background)
        )
    }

    private suspend fun health(source: FakeWearHealthDataSource): WearSystemInfoSection =
        HealthInfoContributor(source).sections().single()

    private fun labelOf(section: WearSystemInfoSection, labelRes: Int): Int {
        val field: WearSystemInfoField = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Label).res
    }

    private fun textOf(section: WearSystemInfoSection, labelRes: Int): String {
        val field: WearSystemInfoField = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Text).text
    }
}
