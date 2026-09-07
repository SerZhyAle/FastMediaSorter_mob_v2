package com.sza.fastmediasorter.wear.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit test for S1718 auto-rotation preference key naming and uniqueness.
 */
class AutoRotationPreferenceTest {

    @Test
    fun `auto-rotation preference addresses the expected key name`() {
        assertEquals(
            "wear_auto_rotation_enabled",
            WearPreferenceKeys.AUTO_ROTATION_ENABLED.name
        )
    }

    @Test
    fun `auto-rotation key does not collide with other wear preference keys`() {
        val keyName = WearPreferenceKeys.AUTO_ROTATION_ENABLED.name
        assertNotEquals(keyName, WearPreferenceKeys.VIEW_MODE.name)
        assertNotEquals(keyName, WearPreferenceKeys.KEEP_SCREEN_AWAKE.name)
    }
}
