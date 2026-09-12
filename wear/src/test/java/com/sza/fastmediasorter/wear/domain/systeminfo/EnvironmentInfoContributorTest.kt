package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentKind
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentReading
import com.sza.fastmediasorter.wear.domain.repository.WearReadingAccuracy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvironmentInfoContributorTest {

    @Test
    fun `a measured reading carries its unit`() = runTest {
        val section = report(
            WearEnvironmentReading.Measured(
                WearEnvironmentKind.ILLUMINANCE,
                value = 214.4f,
                accuracy = WearReadingAccuracy.HIGH
            )
        )

        val value = section.fields.single().value as WearSystemInfoValue.Text
        assertEquals("214 lx", value.text)
    }

    @Test
    fun `pressure and magnetic field keep one decimal`() = runTest {
        val section = report(
            WearEnvironmentReading.Measured(
                WearEnvironmentKind.PRESSURE,
                value = 1013.24f,
                accuracy = null
            ),
            WearEnvironmentReading.Measured(
                WearEnvironmentKind.MAGNETIC_FIELD,
                value = 48.37f,
                accuracy = WearReadingAccuracy.MEDIUM
            )
        )

        assertEquals("1013.2 hPa", (section.fields[0].value as WearSystemInfoValue.Text).text)
        assertEquals("48.4 uT", (section.fields[1].value as WearSystemInfoValue.Text).text)
    }

    @Test
    fun `an unrated sample is still shown as its measurement`() = runTest {
        val section = report(
            WearEnvironmentReading.Measured(
                WearEnvironmentKind.ILLUMINANCE,
                value = 12f,
                accuracy = null
            )
        )

        assertEquals("12 lx", (section.fields.single().value as WearSystemInfoValue.Text).text)
    }

    @Test
    fun `an unreliable sample is named, never printed as a number`() = runTest {
        val section = report(
            WearEnvironmentReading.Measured(
                WearEnvironmentKind.MAGNETIC_FIELD,
                value = 9000f,
                accuracy = WearReadingAccuracy.UNRELIABLE
            )
        )

        val value = section.fields.single().value as WearSystemInfoValue.Label
        assertEquals(R.string.system_info_environment_unreliable, value.res)
    }

    @Test
    fun `a silent sensor reads differently from a missing one`() = runTest {
        val section = report(
            WearEnvironmentReading.Silent(WearEnvironmentKind.PRESSURE),
            WearEnvironmentReading.Unsupported(WearEnvironmentKind.MAGNETIC_FIELD)
        )

        assertEquals(
            R.string.system_info_environment_silent,
            (section.fields[0].value as WearSystemInfoValue.Label).res
        )
        assertEquals(
            R.string.system_info_empty_unsupported,
            (section.fields[1].value as WearSystemInfoValue.Label).res
        )
    }

    @Test
    fun `an unsupported kind keeps its row instead of vanishing`() = runTest {
        val section = report(
            WearEnvironmentReading.Unsupported(WearEnvironmentKind.PRESSURE)
        )

        assertEquals(1, section.fields.size)
        assertEquals(R.string.system_info_environment_pressure, section.fields.single().labelRes)
    }

    @Test
    fun `a watch that would not answer reads differently from one carrying nothing`() = runTest {
        assertEquals(R.string.system_info_empty_unreadable, section(null).emptyReasonRes)
        assertEquals(R.string.system_info_empty_unsupported, section(emptyList()).emptyReasonRes)
    }

    @Test
    fun `a filled section carries no emptiness reason`() = runTest {
        assertNull(report(WearEnvironmentReading.Silent(WearEnvironmentKind.PRESSURE)).emptyReasonRes)
    }

    @Test
    fun `the section sits between the sensor list and the radio`() {
        val contributor = EnvironmentInfoContributor(FakeEnvironmentDataSource(emptyList()))

        assertEquals(WearSystemInfoOrder.ENVIRONMENT, contributor.order)
    }

    private suspend fun report(vararg readings: WearEnvironmentReading): WearSystemInfoSection =
        section(readings.toList())

    private suspend fun section(readings: List<WearEnvironmentReading>?): WearSystemInfoSection =
        EnvironmentInfoContributor(FakeEnvironmentDataSource(readings)).sections().single()
}

private class FakeEnvironmentDataSource(
    private val readings: List<WearEnvironmentReading>?
) : WearEnvironmentDataSource {

    override suspend fun sample(): List<WearEnvironmentReading>? = readings
}
