package com.sza.fastmediasorter.ui.common.widget

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsibleMarkerTest {

    @Test
    fun `expanded marker points down in both reading directions`() {
        assertEquals(0f, CollapsibleMarker.rotation(true, View.LAYOUT_DIRECTION_LTR), 0f)
        assertEquals(0f, CollapsibleMarker.rotation(true, View.LAYOUT_DIRECTION_RTL), 0f)
    }

    @Test
    fun `collapsed marker points right in LTR`() {
        assertEquals(-90f, CollapsibleMarker.rotation(false, View.LAYOUT_DIRECTION_LTR), 0f)
    }

    @Test
    fun `collapsed marker points left in RTL`() {
        assertEquals(90f, CollapsibleMarker.rotation(false, View.LAYOUT_DIRECTION_RTL), 0f)
    }
}
