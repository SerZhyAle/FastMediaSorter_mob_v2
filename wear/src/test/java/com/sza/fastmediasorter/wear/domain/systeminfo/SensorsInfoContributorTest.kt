package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.FakeWearHardwareDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorsInfoContributorTest {

    @Test
    fun `the sensor list is one collapsible value, not a line each`() = runTest {
        val section = sensors(FakeWearHardwareDataSource())

        val field = section.fields.single()
        val entries = (field.value as WearSystemInfoValue.Enumerated).entries
        assertEquals(2, entries.size)
        assertTrue(entries.first().startsWith("accelerometer - LSM6DSO Accelerometer - STM"))
    }

    @Test
    fun `an entry leads with what the sensor is for, not with its part number`() = runTest {
        val entries = enumerated(FakeWearHardwareDataSource())

        assertTrue(entries[1].startsWith("heart_rate - "))
    }

    @Test
    fun `a watch that will not name the purpose still lists the sensor`() = runTest {
        val unnamed = FakeWearHardwareDataSource().apply {
            sensors = sensors?.map { sensor -> sensor.copy(type = "") }
        }

        assertTrue(enumerated(unnamed).first().startsWith("LSM6DSO Accelerometer - STM"))
    }

    @Test
    fun `a watch with no sensors reads differently from one that would not answer`() = runTest {
        val none = FakeWearHardwareDataSource().apply { sensors = emptyList() }
        val silent = FakeWearHardwareDataSource().apply { sensors = null }

        assertEquals(R.string.system_info_empty_unsupported, sensors(none).emptyReasonRes)
        assertEquals(R.string.system_info_empty_unreadable, sensors(silent).emptyReasonRes)
    }

    @Test
    fun `a filled section carries no emptiness reason`() = runTest {
        assertNull(sensors(FakeWearHardwareDataSource()).emptyReasonRes)
    }

    @Test
    fun `the entry order is the one the watch answered in`() = runTest {
        val entries = enumerated(FakeWearHardwareDataSource())

        assertTrue(entries[0].startsWith("accelerometer"))
        assertTrue(entries[1].startsWith("heart_rate"))
    }

    @Test
    fun `the radio section lists bands and standards separately`() = runTest {
        val radio = RadioCapabilityContributor(FakeWearHardwareDataSource()).sections().single()

        assertEquals(
            listOf("2.4 GHz", "5 GHz"),
            enumeratedOf(radio, R.string.system_info_radio_bands)
        )
        assertEquals(
            listOf("Wi-Fi 4", "Wi-Fi 5"),
            enumeratedOf(radio, R.string.system_info_radio_standards)
        )
    }

    @Test
    fun `a radio that answers nothing states why`() = runTest {
        val silent = FakeWearHardwareDataSource().apply {
            supportedWifiBands = null
            supportedWifiStandards = null
        }

        val radio = RadioCapabilityContributor(silent).sections().single()

        assertTrue(radio.fields.isEmpty())
        assertEquals(R.string.system_info_empty_unreadable, radio.emptyReasonRes)
    }

    private suspend fun sensors(source: FakeWearHardwareDataSource): WearSystemInfoSection =
        SensorsInfoContributor(source).sections().single()

    private suspend fun enumerated(source: FakeWearHardwareDataSource): List<String> =
        (sensors(source).fields.single().value as WearSystemInfoValue.Enumerated).entries

    private fun enumeratedOf(section: WearSystemInfoSection, labelRes: Int): List<String> {
        val field = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Enumerated).entries
    }
}
