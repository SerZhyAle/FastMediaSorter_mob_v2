package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherScreenLockManagerTest {

    private class FakeAccessibilityActions(private val result: Boolean) : GestureAccessibilityActions {
        var performed: ScreenshotGestureAction? = null

        override fun perform(action: ScreenshotGestureAction): Boolean {
            performed = action
            return result
        }
    }

    @Test
    fun `real lock reports success and raises no overlay`() {
        val seam = FakeAccessibilityActions(result = true)
        var fallbackCalls = 0
        val manager = LauncherScreenLockManager(setOf(seam)) { fallbackCalls++ }

        assertTrue(manager.turnScreenOff())
        assertEquals(ScreenshotGestureAction.LOCK_SCREEN, seam.performed)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `refused lock reports failure and raises the overlay once`() {
        val seam = FakeAccessibilityActions(result = false)
        var fallbackCalls = 0
        val manager = LauncherScreenLockManager(setOf(seam)) { fallbackCalls++ }

        assertFalse(manager.turnScreenOff())
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `flavor without the seam reports failure and raises the overlay`() {
        var fallbackCalls = 0
        val manager = LauncherScreenLockManager(emptySet()) { fallbackCalls++ }

        assertFalse(manager.turnScreenOff())
        assertEquals(1, fallbackCalls)
    }
}
