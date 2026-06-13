package com.sza.fastmediasorter.ui.settings

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsKeyboardNavigationManagerTest {

    private val callback = mockk<SettingsKeyboardNavigationManager.Callback>(relaxed = true)
    private lateinit var manager: SettingsKeyboardNavigationManager

    @Before
    fun setUp() {
        every { callback.tabCount() } returns 4
        every { callback.currentTab() } returns 0
        every { callback.isSearchVisible() } returns false
        every { callback.isTextEditorFocused() } returns false
        every { callback.clearFocusedTextEditor() } returns false
        manager = SettingsKeyboardNavigationManager(callback)
    }

    @Test
    fun `escape clears focused editor before navigateBack`() {
        every { callback.isTextEditorFocused() } returns true
        every { callback.clearFocusedTextEditor() } returns true

        val consumed = manager.handleKeyDown(
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
        )

        assertTrue(consumed)
        verify(exactly = 1) { callback.clearFocusedTextEditor() }
        verify(exactly = 0) { callback.navigateBack() }
    }

    @Test
    fun `focused editor bypasses shortcut layer for ctrl f`() {
        every { callback.isTextEditorFocused() } returns true

        val event = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0, KeyEvent.META_CTRL_ON)
        val consumed = manager.handleKeyDown(KeyEvent.KEYCODE_F, event)

        assertFalse(consumed)
        verify(exactly = 0) { callback.openSearchOverlay() }
    }

    @Test
    fun `enter uses callback activation result`() {
        every { callback.activateFocused() } returns true

        val consumed = manager.handleKeyDown(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
        )

        assertTrue(consumed)
        verify(exactly = 1) { callback.activateFocused() }
    }

    @Test
    fun `enter stays unconsumed when nothing activates`() {
        every { callback.activateFocused() } returns false

        val consumed = manager.handleKeyDown(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
        )

        assertFalse(consumed)
        verify(exactly = 1) { callback.activateFocused() }
    }
}
