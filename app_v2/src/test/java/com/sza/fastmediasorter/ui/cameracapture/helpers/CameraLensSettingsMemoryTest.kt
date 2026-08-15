package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1658: the encoded memory is read back out of user preferences after a restart, so a codec defect
 * would silently hand a lens the wrong capture set rather than fail visibly. Surviving that round
 * trip is the point of the store, and this is where it is pinned.
 */
class CameraLensSettingsMemoryTest {

    private val wideSettings = CameraLensSettingsMemory.LensSettings(
        profile = PhotoProfile.NIGHT,
        whiteBalanceMode = 3,
        manualIso = 800,
        manualShutterNs = 20_000_000L,
        exposureCompensationIndex = -2,
    )

    private val teleSettings = CameraLensSettingsMemory.LensSettings(
        profile = PhotoProfile.PORTRAIT,
        whiteBalanceMode = null,
        manualIso = null,
        manualShutterNs = null,
        exposureCompensationIndex = 0,
    )

    @Test
    fun `two lenses survive the round trip field for field`() {
        val memory = CameraLensSettingsMemory()
        memory.remember("0", wideSettings)
        memory.remember("2:4", teleSettings)

        val restored = CameraLensSettingsMemory.decode(memory.encode())

        assertEquals(wideSettings, restored.recall("0"))
        assertEquals(teleSettings, restored.recall("2:4"))
    }

    @Test
    fun `an absent manual value comes back null rather than zero`() {
        val memory = CameraLensSettingsMemory()
        memory.remember("2:4", teleSettings)

        val restored = CameraLensSettingsMemory.decode(memory.encode()).recall("2:4")

        assertNull(restored?.whiteBalanceMode)
        assertNull(restored?.manualIso)
        assertNull(restored?.manualShutterNs)
        assertEquals(0, restored?.exposureCompensationIndex)
    }

    @Test
    fun `an empty payload decodes to an empty memory`() {
        val restored = CameraLensSettingsMemory.decode("")

        assertNull(restored.recall("0"))
        assertEquals("", restored.encode())
    }

    @Test
    fun `a malformed record is dropped without costing its neighbours`() {
        val memory = CameraLensSettingsMemory()
        memory.remember("0", wideSettings)
        val corrupted = memory.encode() + ";1,NOT_A_PROFILE,,,,0;2,PORTRAIT,,,"

        val restored = CameraLensSettingsMemory.decode(corrupted)

        assertEquals(wideSettings, restored.recall("0"))
        assertNull(restored.recall("1"))
        assertNull(restored.recall("2"))
    }

    @Test
    fun `retainOnly drops exactly the lenses the device no longer offers`() {
        val memory = CameraLensSettingsMemory()
        memory.remember("0", wideSettings)
        memory.remember("2:4", teleSettings)

        memory.retainOnly(setOf("0"))

        assertEquals(wideSettings, memory.recall("0"))
        assertNull(memory.recall("2:4"))
    }
}
