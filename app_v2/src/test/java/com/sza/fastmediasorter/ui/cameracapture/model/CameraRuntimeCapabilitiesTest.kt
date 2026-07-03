package com.sza.fastmediasorter.ui.cameracapture.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for [CameraRuntimeCapabilities.buildZoomPresets] (S0753): the widened
 * candidate set must clamp to the lens range, always keep a reachable maximum, and de-duplicate.
 */
class CameraRuntimeCapabilitiesTest {

    @Test
    fun `wide lens exposes the full step set`() {
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 0.5f, maxZoom = 30f)
        // Below 1x only the lens minimum (0.5); at/above 1x the 1/3/5/10/20/30 steps.
        assertEquals(listOf(0.5f, 1f, 3f, 5f, 10f, 20f, 30f), presets)
    }

    @Test
    fun `mid lens drops unreachable steps and appends the real maximum`() {
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 1f, maxZoom = 8f)
        // 10/20/30 are out of range; 8x (the lens ceiling) is kept so the user can reach the maximum.
        assertEquals(listOf(1f, 3f, 5f, 8f), presets)
    }

    @Test
    fun `fixed lens with no zoom range yields no presets`() {
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 1f, maxZoom = 1f)
        assertTrue(presets.isEmpty())
    }

    @Test
    fun `narrow lens keeps only the in-range steps`() {
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 1f, maxZoom = 2f)
        assertEquals(listOf(1f, 2f), presets)
    }

    @Test
    fun `maximum equal to a table value is not duplicated`() {
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 1f, maxZoom = 3f)
        assertEquals(listOf(1f, 3f), presets)
    }

    @Test
    fun `drops the step just below the max`() {
        // A 3.3x ceiling makes the 3x step redundant, so it is dropped (Samsung-style).
        val presets = CameraRuntimeCapabilities.buildZoomPresets(minZoom = 1f, maxZoom = 3.3f)
        assertEquals(listOf(1f, 3.3f), presets)
    }

    @Test
    fun `digital cap extends presets beyond the lens optical max`() {
        // Lens optical max 8x with a 4x digital cap reaches 30x, so 10/20/30 appear.
        val presets = CameraRuntimeCapabilities.buildZoomPresets(
            minZoom = 1f,
            maxZoom = 8f,
            multiplier = 1f,
            digitalCap = 4f,
        )
        assertEquals(listOf(1f, 3f, 5f, 10f, 20f, 30f), presets)
    }
}
