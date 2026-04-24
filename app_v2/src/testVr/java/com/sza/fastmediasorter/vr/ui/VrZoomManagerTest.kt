package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.vr.render.VrLayerDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * VR-only unit coverage must live in `testVr` so the standard flavor does not
 * try to compile sources that only exist under `src/vr/java`.
 */
@Config(sdk = [34])
class VrZoomManagerTest {

    private lateinit var lastDescriptor: VrLayerDescriptor
    private lateinit var manager: VrZoomManager
    private val baseWidth = 4f
    private val baseHeight = 2.25f

    @Before
    fun setUp() {
        lastDescriptor = VrLayerDescriptor(widthMeters = baseWidth, heightMeters = baseHeight)
        manager = VrZoomManager(onZoomChanged = { lastDescriptor = it })
        manager.setBaseDescriptor(
            VrLayerDescriptor(widthMeters = baseWidth, heightMeters = baseHeight),
        )
    }

    @Test
    fun reset_setsZoomTo1() {
        manager.onDiscreteStep(increase = true)
        manager.onDiscreteStep(increase = true)
        manager.reset()
        assertEquals(1.0f, manager.zoom(), 0.0001f)
    }

    @Test
    fun onDiscreteStepIncrease_movesToNextLevel() {
        manager.onDiscreteStep(increase = true)
        assertEquals(1.2f, manager.zoom(), 0.0001f)
    }

    @Test
    fun onDiscreteStepDecrease_movesToPrevLevel() {
        manager.onDiscreteStep(increase = false)
        assertEquals(0.8f, manager.zoom(), 0.0001f)
    }

    @Test
    fun onDiscreteStepIncrease_clampsAtMaxZoom() {
        repeat(20) { manager.onDiscreteStep(increase = true) }
        assertTrue("zoom should not exceed MAX_ZOOM", manager.zoom() <= VrZoomManager.MAX_ZOOM + 1e-4f)
    }

    @Test
    fun onDiscreteStepDecrease_clampsAtMinZoom() {
        repeat(20) { manager.onDiscreteStep(increase = false) }
        assertTrue("zoom should not fall below MIN_ZOOM", manager.zoom() >= VrZoomManager.MIN_ZOOM - 1e-4f)
    }

    @Test
    fun gripDeltaPositive_increasesZoom() {
        val before = manager.zoom()
        manager.onGripDelta(+0.5f)
        assertTrue("grip +delta should raise zoom", manager.zoom() > before)
    }

    @Test
    fun gripDeltaNegative_decreasesZoom() {
        manager.onGripDelta(+0.5f)
        val peak = manager.zoom()
        manager.onGripDelta(-0.5f)
        assertTrue("grip -delta should lower zoom", manager.zoom() < peak)
    }

    @Test
    fun setBaseDescriptor_keepsZoomFactorApplied() {
        manager.onDiscreteStep(increase = true)
        manager.setBaseDescriptor(VrLayerDescriptor(widthMeters = 8f, heightMeters = 4.5f))
        assertEquals(9.6f, lastDescriptor.widthMeters, 0.0001f)
        assertEquals(5.4f, lastDescriptor.heightMeters, 0.0001f)
    }
}