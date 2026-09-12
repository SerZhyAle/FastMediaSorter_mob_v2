package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the clock gadget preferences survive a new store instance. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ClockGadgetStateStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferencesBefore() = clearPreferences()

    @After
    fun clearPreferencesAfter() = clearPreferences()

    private fun clearPreferences() {
        context.getSharedPreferences(ClockGadgetStateStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `defaults keep seconds and the theme colour`() {
        val state = ClockGadgetStateStore(context).read()

        assertTrue(state.secondsVisible)
        assertNull(state.dialColor)
        assertEquals(ClockGadgetStateStore.DEFAULT_DIAL_TYPEFACE, state.dialTypefaceName)
    }

    @Test
    fun `saved display choices are read by a new store`() {
        val firstStore = ClockGadgetStateStore(context)
        firstStore.setSecondsVisible(false)
        firstStore.setDialColor(TEST_DIAL_COLOR)
        firstStore.setDialTypefaceName(TEST_DIAL_TYPEFACE)

        val restored = ClockGadgetStateStore(context).read()

        assertFalse(restored.secondsVisible)
        assertEquals(TEST_DIAL_COLOR, restored.dialColor)
        assertEquals(TEST_DIAL_TYPEFACE, restored.dialTypefaceName)
    }

    @Test
    fun `resetting colour keeps the seconds preference`() {
        val store = ClockGadgetStateStore(context)
        store.setSecondsVisible(false)
        store.setDialColor(TEST_DIAL_COLOR)
        store.setDialColor(null)

        val state = store.read()

        assertFalse(state.secondsVisible)
        assertNull(state.dialColor)
    }

    private companion object {
        const val TEST_DIAL_COLOR = 0xFF123456.toInt()
        const val TEST_DIAL_TYPEFACE = "serif"
    }
}
