package com.sza.fastmediasorter.ui.launcher.gadget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2213: pins the substitution rule the launcher reset depends on.
 *
 * Strategic §11 criterion 4 - a user with several weather cells on different cities must see no
 * substitution in any cell he configured himself - is the criterion a device pass is least likely to
 * reach, because reproducing it needs a desktop deliberately built with two cities before the reset.
 */
class LauncherWeatherParamFallbackTest {

    @Test
    fun `a non-weather key passes its param through untouched`() {
        val resolved = LauncherWeatherParamFallback.resolve(
            key = LauncherGadgetRegistry.KEY_WORLD_CLOCK,
            param = "Europe/Kyiv",
            savedLocation = KYIV,
        )

        assertEquals("Europe/Kyiv", resolved)
    }

    @Test
    fun `a weather cell with its own place keeps it even when a different one is saved`() {
        val resolved = LauncherWeatherParamFallback.resolve(
            key = LauncherGadgetRegistry.KEY_WEATHER,
            param = LONDON,
            savedLocation = KYIV,
        )

        assertEquals(LONDON, resolved)
    }

    @Test
    fun `a weather cell with no readable place takes the saved one`() {
        val resolved = LauncherWeatherParamFallback.resolve(
            key = LauncherGadgetRegistry.KEY_WEATHER,
            param = null,
            savedLocation = KYIV,
        )

        assertEquals(KYIV, resolved)
    }

    @Test
    fun `a weather cell with no place and nothing saved stays without one`() {
        val resolved = LauncherWeatherParamFallback.resolve(
            key = LauncherGadgetRegistry.KEY_WEATHER,
            param = null,
            savedLocation = "",
        )

        assertNull(resolved)
    }

    private companion object {
        const val KYIV = "50.45,30.52,Kyiv"
        const val LONDON = "51.5,-0.12,London"
    }
}
