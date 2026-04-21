package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VrPanelSizePreferenceTest {

    // In-memory SharedPreferences backed by a real HashMap — no Robolectric needed.
    private val backingMap = mutableMapOf<String, Any>()
    private val mockPrefs: SharedPreferences = buildFakePrefs(backingMap)
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        backingMap.clear()
        every { mockContext.getSharedPreferences("vr_panel_size", Context.MODE_PRIVATE) } returns mockPrefs
    }

    @Test
    fun `load without prior save returns defaults`() {
        val (w, h) = VrPanelSizePreference.load(mockContext)
        assertEquals(VrPanelSizePreference.DEFAULT_W, w)
        assertEquals(VrPanelSizePreference.DEFAULT_H, h)
    }

    @Test
    fun `save and load returns persisted values`() {
        VrPanelSizePreference.save(mockContext, 1280, 720)
        val (w, h) = VrPanelSizePreference.load(mockContext)
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    @Test
    fun `save below MIN_W does not persist`() {
        VrPanelSizePreference.save(mockContext, 400, 600) // width below MIN_W=800
        // backingMap should remain empty — no write happened
        assertFalse(backingMap.containsKey("panel_w_px"))
    }

    @Test
    fun `save below MIN_H does not persist`() {
        VrPanelSizePreference.save(mockContext, 1200, 200) // height below MIN_H=400
        assertFalse(backingMap.containsKey("panel_h_px"))
    }

    @Test
    fun `save exactly at MIN boundary persists`() {
        VrPanelSizePreference.save(mockContext, 800, 400) // exactly at MIN
        assertTrue(backingMap.containsKey("panel_w_px"))
        val (w, h) = VrPanelSizePreference.load(mockContext)
        assertEquals(800, w)
        assertEquals(400, h)
    }

    @Test
    fun `hasUserSize returns false before first save`() {
        assertFalse(VrPanelSizePreference.hasUserSize(mockContext))
    }

    @Test
    fun `hasUserSize returns true after valid save`() {
        VrPanelSizePreference.save(mockContext, 1920, 1080)
        assertTrue(VrPanelSizePreference.hasUserSize(mockContext))
    }

    // ---------------------------------------------------------------------------
    // Helpers — minimal in-memory SharedPreferences stub
    // ---------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun buildFakePrefs(map: MutableMap<String, Any>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs  = mockk<SharedPreferences>(relaxed = true)

        every { editor.putInt(any(), any()) } answers {
            map[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.apply() } returns Unit
        every { prefs.edit() } returns editor
        every { prefs.getInt(any(), any()) } answers {
            (map[firstArg()] as? Int) ?: secondArg()
        }
        every { prefs.contains(any()) } answers { map.containsKey(firstArg()) }

        return prefs
    }
}
