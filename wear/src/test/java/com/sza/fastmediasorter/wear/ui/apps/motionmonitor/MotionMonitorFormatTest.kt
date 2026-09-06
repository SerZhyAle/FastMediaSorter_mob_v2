package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMonitorFormatTest {

    @Test
    fun `axes are shown to two decimals, all of them`() {
        assertEquals("0.12  9.81  -0.03", formatAxes(listOf(0.1234f, 9.8066f, -0.0251f)))
    }

    @Test
    fun `a rotation vector's fourth component is not dropped`() {
        val formatted = formatAxes(listOf(0.1f, 0.2f, 0.3f, 0.9f))

        assertEquals(4, formatted.split("  ").size)
    }

    @Test
    fun `a step count is whole, because a fractional step is a lie`() {
        assertEquals("5312", formatStepCount(listOf(5312.0f)))
    }

    @Test
    fun `a step counter that has reported nothing formats to empty, not to zero`() {
        assertEquals("", formatStepCount(emptyList()))
    }

    @Test
    fun `the age is seconds with one decimal`() {
        assertEquals("1.2", formatAgeSeconds(1234L))
    }

    @Test
    fun `the permission button appears only for a refused grant`() {
        assertTrue(stateWith(WearSensorAvailability.PermissionDenied).canRequestPermission)
    }

    @Test
    fun `no permission button when the edition simply does not carry the capability`() {
        assertFalse(stateWith(WearSensorAvailability.NotInThisEdition).canRequestPermission)
    }

    @Test
    fun `no permission button when the watch has no step sensor at all`() {
        assertFalse(stateWith(WearSensorAvailability.NoHardware).canRequestPermission)
    }

    private fun stateWith(availability: WearSensorAvailability) = MotionMonitorUiState(
        activity = listOf(
            MotionStreamRow(
                id = WearSensorStreamId.STEP_COUNTER,
                availability = availability,
                values = emptyList(),
                eventCount = 0,
                hertz = 0.0,
                ageMillis = null
            )
        )
    )
}
