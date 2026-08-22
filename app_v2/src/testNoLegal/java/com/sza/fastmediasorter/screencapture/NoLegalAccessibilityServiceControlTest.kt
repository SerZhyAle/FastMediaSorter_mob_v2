package com.sza.fastmediasorter.screencapture

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoLegalAccessibilityServiceControlTest {

    private val control = NoLegalAccessibilityServiceControl()

    @Before
    fun clearHolderBefore() {
        ScreenshotAccessibilityServiceHolder.instance = null
    }

    @After
    fun clearHolderAfter() {
        ScreenshotAccessibilityServiceHolder.instance = null
    }

    @Test
    fun `isServiceActive is false when no service is connected`() {
        assertFalse(control.isServiceActive())
    }

    @Test
    fun `openPowerDialog is false when no service is connected`() {
        assertFalse(control.openPowerDialog())
    }

    @Test
    fun `disableSelf is false when no service is connected`() {
        assertFalse(control.disableSelf())
    }

    @Test
    fun `openPowerDialog dispatches once to the live service`() {
        val service = mockk<ScreenshotAccessibilityService>()
        every { service.openPowerDialog() } returns true
        ScreenshotAccessibilityServiceHolder.instance = service

        assertTrue(control.openPowerDialog())

        verify(exactly = 1) { service.openPowerDialog() }
    }

    @Test
    fun `openPowerDialog forwards a refused dispatch`() {
        val service = mockk<ScreenshotAccessibilityService>()
        every { service.openPowerDialog() } returns false
        ScreenshotAccessibilityServiceHolder.instance = service

        assertFalse(control.openPowerDialog())

        verify(exactly = 1) { service.openPowerDialog() }
    }
}
