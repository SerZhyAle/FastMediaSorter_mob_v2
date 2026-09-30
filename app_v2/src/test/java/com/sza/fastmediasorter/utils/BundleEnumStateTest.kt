package com.sza.fastmediasorter.utils

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3793: the pending picker discriminator must survive a saved-state round trip, and a stale or
 * absent entry must read back as "nothing pending" rather than throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class BundleEnumStateTest {

    private enum class Slot { LEFT, RIGHT }

    @Test
    fun `value survives a round trip`() {
        val bundle = Bundle().apply { putEnumName(KEY, Slot.RIGHT) }

        assertEquals(Slot.RIGHT, bundle.getEnumByName<Slot>(KEY))
    }

    @Test
    fun `missing key and null bundle read as null`() {
        assertNull(Bundle().getEnumByName<Slot>(KEY))
        assertNull((null as Bundle?).getEnumByName<Slot>(KEY))
    }

    @Test
    fun `unknown constant name reads as null`() {
        val bundle = Bundle().apply { putString(KEY, "REMOVED_CONSTANT") }

        assertNull(bundle.getEnumByName<Slot>(KEY))
    }

    @Test
    fun `null value clears an earlier entry`() {
        val bundle = Bundle().apply {
            putEnumName(KEY, Slot.LEFT)
            putEnumName(KEY, null)
        }

        assertFalse(bundle.containsKey(KEY))
        assertNull(bundle.getEnumByName<Slot>(KEY))
    }

    private companion object {
        const val KEY = "pending_slot"
    }
}
