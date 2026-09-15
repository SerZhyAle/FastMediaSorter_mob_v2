package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import org.junit.Assert.assertEquals
import org.junit.Test

class BroadcastLensLabelFormatterTest {

    private val names: (String) -> String = { facing -> facing.replaceFirstChar { it.uppercase() } }

    @Test
    fun `back lenses are told apart by magnification`() {
        val options = listOf(
            BroadcastLensOption("0/2", BroadcastLensOption.FACING_BACK, 0.57f),
            BroadcastLensOption("0", BroadcastLensOption.FACING_BACK, 1f),
            BroadcastLensOption("0/4", BroadcastLensOption.FACING_BACK, 3f),
        )

        assertEquals(listOf("Back 0.6x", "Back 1x", "Back 3x"), BroadcastLensLabelFormatter.labels(options, names))
    }

    @Test
    fun `identical front names are numbered`() {
        val options = listOf(
            BroadcastLensOption("1", BroadcastLensOption.FACING_FRONT, 1f),
            BroadcastLensOption("1/5", BroadcastLensOption.FACING_FRONT, 1f),
            BroadcastLensOption("0", BroadcastLensOption.FACING_BACK, 1f),
        )

        assertEquals(listOf("Front 1", "Front 2", "Back 1x"), BroadcastLensLabelFormatter.labels(options, names))
    }

    @Test
    fun `lens id splits into logical and physical camera ids`() {
        assertEquals("0", BroadcastLensOption.logicalIdOf("0/3"))
        assertEquals("3", BroadcastLensOption.physicalIdOf("0/3"))
        assertEquals(null, BroadcastLensOption.physicalIdOf("1"))
    }
}
