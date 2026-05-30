package com.sza.fastmediasorter.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [BrowseManualOrderPrefs] persists per-(resourceId, dirPath) ordering into real
 * SharedPreferences (Robolectric). Covers save/load round-trip, blank-handling, the
 * missing-key null path, and clear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseManualOrderPrefsTest {

    private lateinit var prefs: BrowseManualOrderPrefs

    @Before
    fun setUp() {
        prefs = BrowseManualOrderPrefs(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `load returns null when nothing saved`() {
        assertNull(prefs.loadOrder(1L, "/dir"))
    }

    @Test
    fun `save then load round-trips the ordered paths`() {
        val order = listOf("/dir/c.jpg", "/dir/a.jpg", "/dir/b.jpg")
        prefs.saveOrder(1L, "/dir", order)
        assertEquals(order, prefs.loadOrder(1L, "/dir"))
    }

    @Test
    fun `keys are scoped per resource and directory`() {
        prefs.saveOrder(1L, "/dir", listOf("/x"))
        assertNull(prefs.loadOrder(2L, "/dir"))
        assertNull(prefs.loadOrder(1L, "/other"))
    }

    @Test
    fun `saving an empty list loads back as null`() {
        prefs.saveOrder(1L, "/dir", emptyList())
        assertNull(prefs.loadOrder(1L, "/dir"))
    }

    @Test
    fun `blank entries are filtered out on load`() {
        prefs.saveOrder(1L, "/dir", listOf("/a", "", "/b", "   "))
        assertEquals(listOf("/a", "/b"), prefs.loadOrder(1L, "/dir"))
    }

    @Test
    fun `clearOrder removes a saved order`() {
        prefs.saveOrder(1L, "/dir", listOf("/a"))
        prefs.clearOrder(1L, "/dir")
        assertNull(prefs.loadOrder(1L, "/dir"))
    }
}
