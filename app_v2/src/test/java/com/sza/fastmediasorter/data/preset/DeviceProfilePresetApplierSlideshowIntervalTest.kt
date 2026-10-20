package com.sza.fastmediasorter.data.preset

import android.content.Context
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppSettings
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfilePresetApplierSlideshowIntervalTest {

    private val applier = DeviceProfilePresetApplier(
        mockk<Context>(relaxed = true),
        mockk<LauncherModeContract>(relaxed = true)
    )

    private fun applied(raw: String): Int =
        applier.applyOverride(AppSettings(), "slideshowInterval", raw).slideshowInterval

    @Test
    fun `an interval below the floor is raised to one second`() {
        assertEquals(1, applied("0"))
        assertEquals(1, applied("-5"))
    }

    @Test
    fun `an interval above the settings range is lowered to its ceiling`() {
        assertEquals(MAX_SECONDS, applied("100000"))
    }

    @Test
    fun `an interval inside the range is kept`() {
        assertEquals(IN_RANGE_SECONDS, applied("$IN_RANGE_SECONDS"))
    }

    @Test
    fun `an unparseable interval keeps the current value`() {
        assertEquals(AppSettings().slideshowInterval, applied("fast"))
    }

    private companion object {
        const val MAX_SECONDS = 3600
        const val IN_RANGE_SECONDS = 7
    }
}
