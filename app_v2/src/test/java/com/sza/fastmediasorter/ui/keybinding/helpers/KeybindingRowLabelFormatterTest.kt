package com.sza.fastmediasorter.ui.keybinding.helpers

import com.sza.fastmediasorter.domain.input.InputTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1134: covers the VR trigger-label mapping. Uses a real Robolectric application context so the string
 * resources resolve for real - a named gesture code returns its gesture label, and every other code
 * returns the clean generic "VR %d", never the old raw "VR[type]" fallback that the screen used to show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdkVersion=35 needs an explicit SDK pin.
class KeybindingRowLabelFormatterTest {

    private val formatter = KeybindingRowLabelFormatter(RuntimeEnvironment.getApplication())

    @Test
    fun `genuine VR gesture codes map to their gesture labels`() {
        assertEquals("VR Recenter", formatter.format(InputTrigger.VrEvent(10)))
        assertEquals("Controller Ray", formatter.format(InputTrigger.VrEvent(17)))
        assertEquals("Swipe Left", formatter.format(InputTrigger.VrEvent(19)))
        assertEquals("Swipe Right", formatter.format(InputTrigger.VrEvent(20)))
        assertEquals("Swipe Up", formatter.format(InputTrigger.VrEvent(21)))
        assertEquals("Swipe Down", formatter.format(InputTrigger.VrEvent(22)))
        assertEquals("Double Pinch", formatter.format(InputTrigger.VrEvent(23)))
    }

    @Test
    fun `codes without a gesture identity fall back to the clean generic label`() {
        assertEquals("VR 0", formatter.format(InputTrigger.VrEvent(0)))
        assertEquals("VR 5", formatter.format(InputTrigger.VrEvent(5)))
        assertEquals("VR 15", formatter.format(InputTrigger.VrEvent(15)))
    }

    @Test
    fun `generic fallback never emits the old raw bracket form`() {
        val label = formatter.format(InputTrigger.VrEvent(99))

        assertEquals("VR 99", label)
        assertFalse(label.contains("["))
    }
}
