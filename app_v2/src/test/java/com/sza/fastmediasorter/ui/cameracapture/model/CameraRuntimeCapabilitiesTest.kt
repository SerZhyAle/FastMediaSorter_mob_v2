package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for [CameraRuntimeCapabilities.buildZoomPresets] (S0753): the widened
 * candidate set must clamp to the lens range, always keep a reachable maximum, and de-duplicate.
 * S1261 adds the cross-lens floor pill predicate ([CameraRuntimeCapabilities.showsCrossLensFloor]).
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
        // 8x stays: the native optical max always keeps its own button (S1189).
        assertEquals(listOf(1f, 3f, 5f, 8f, 10f, 20f, 30f), presets)
    }

    @Test
    fun `display rounding uses half steps below 5 and wholes from 5 upward`() {
        // S1260 owner rule: 1.6 -> 1.5, 3.1 -> 3, 4.9 -> 5, 8.1 -> 8, 16.4 -> 16.
        assertEquals(1.5f, CameraRuntimeCapabilities.roundEquivalentForDisplay(1.6f))
        assertEquals(3f, CameraRuntimeCapabilities.roundEquivalentForDisplay(3.1f))
        assertEquals(5f, CameraRuntimeCapabilities.roundEquivalentForDisplay(4.9f))
        assertEquals(8f, CameraRuntimeCapabilities.roundEquivalentForDisplay(8.1f))
        assertEquals(16f, CameraRuntimeCapabilities.roundEquivalentForDisplay(16.4f))
        assertEquals(0.5f, CameraRuntimeCapabilities.roundEquivalentForDisplay(0.6f))
    }

    @Test
    fun `presets whose display labels collide keep the native bound`() {
        // Native max 9.8 labels as "10" and so does the 10x digital step - only one pill survives,
        // and it is the native bound (S1260).
        val presets = CameraRuntimeCapabilities.buildZoomPresets(
            minZoom = 1f,
            maxZoom = 9.8f,
            multiplier = 1f,
            digitalCap = 4f,
        )
        val labels = presets.map { CameraRuntimeCapabilities.roundEquivalentForDisplay(it) }
        assertEquals(labels.distinct(), labels)
        assertTrue(presets.contains(9.8f))
        assertTrue(!presets.contains(10f))
    }

    @Test
    fun `bound lens reaching the device floor natively hides the cross-lens pill`() {
        // S25 FE logical camera 0: own range starts at 0.57, device floor 0.57 - reachable natively.
        val caps = CameraRuntimeCapabilities(
            minZoomRatio = 0.57f,
            maxZoomRatio = 10f,
            zoomMultiplier = 1f,
            minEquivalentZoomRatio = 0.57f,
        )
        assertFalse(caps.showsCrossLensFloor)
    }

    @Test
    fun `bound lens stuck at 1x with a sub-1x device floor shows the cross-lens pill`() {
        // S25 FE ultra-wide entry bound directly: own floor 1.0 while the device reaches 0.57.
        val caps = CameraRuntimeCapabilities(
            minZoomRatio = 1f,
            maxZoomRatio = 8f,
            zoomMultiplier = 1f,
            minEquivalentZoomRatio = 0.57f,
        )
        assertTrue(caps.showsCrossLensFloor)
        // Pill prints the S1260 display rounding: 0.57 -> 0.5.
        assertEquals(0.5f, caps.crossLensFloorDisplay)
    }

    @Test
    fun `equal floors keep the row unchanged`() {
        // POCO guard (strategic goal 4): device floor equals the bound lens floor - no extra pill.
        val caps = CameraRuntimeCapabilities(
            minZoomRatio = 1f,
            maxZoomRatio = 8f,
            zoomMultiplier = 1f,
            minEquivalentZoomRatio = 1f,
        )
        assertFalse(caps.showsCrossLensFloor)
    }

    @Test
    fun `front lens never shows the cross-lens pill`() {
        val caps = CameraRuntimeCapabilities(
            activeLensFacing = CameraSelector.LENS_FACING_FRONT,
            minZoomRatio = 1f,
            maxZoomRatio = 8f,
            zoomMultiplier = 1f,
            minEquivalentZoomRatio = 0.57f,
        )
        assertFalse(caps.showsCrossLensFloor)
    }

    @Test
    fun `tele bound lens shows the pill against its own equivalent floor`() {
        // Bound tele (multiplier 3, own native floor 1): reachable equivalent floor is 3.0, the
        // device floor 0.57 sits far below - the pill must appear.
        val caps = CameraRuntimeCapabilities(
            minZoomRatio = 1f,
            maxZoomRatio = 8f,
            zoomMultiplier = 3f,
            minEquivalentZoomRatio = 0.57f,
        )
        assertTrue(caps.showsCrossLensFloor)
        assertEquals(3f, caps.ownEquivalentFloor)
    }

    // S1675: rear-lens floors for the pill row shown where the bound lens has no range of its own.

    @Test
    fun `rear lens floors are display-rounded and ascending`() {
        // Raw floors as the lenses report them: ultra-wide 0.57, main 1.0, tele 3.1.
        val floors = CameraRuntimeCapabilities.buildRearLensFloors(listOf(1f, 0.57f, 3.1f))
        assertEquals(listOf(0.5f, 1f, 3f), floors)
    }

    @Test
    fun `floors that round to the same label collapse to one entry`() {
        // Two lenses a hair apart print the same button - the user must not see it twice.
        val floors = CameraRuntimeCapabilities.buildRearLensFloors(listOf(0.55f, 0.57f, 1f))
        assertEquals(listOf(0.5f, 1f), floors)
    }

    @Test
    fun `a non-positive floor is dropped`() {
        val floors = CameraRuntimeCapabilities.buildRearLensFloors(listOf(0f, -1f, 1f))
        assertEquals(listOf(1f), floors)
    }

    @Test
    fun `no rear lens yields no floors`() {
        assertTrue(CameraRuntimeCapabilities.buildRearLensFloors(emptyList()).isEmpty())
    }

    @Test
    fun `a single floor is returned so the caller decides emptiness`() {
        // The builder must not silently swallow the single-lens case - that is the caller's rule.
        assertEquals(listOf(1f), CameraRuntimeCapabilities.buildRearLensFloors(listOf(1f)))
    }

    @Test
    fun `the bound lens floor prints by the same rule as the pills`() {
        val caps = CameraRuntimeCapabilities(minZoomRatio = 1f, maxZoomRatio = 1f, zoomMultiplier = 0.57f)
        assertEquals(0.5f, caps.ownEquivalentFloorDisplay)
    }
}
