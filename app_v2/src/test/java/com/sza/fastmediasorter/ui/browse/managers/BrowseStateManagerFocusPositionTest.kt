package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0845: browser commands must target the item that actually holds D-pad/keyboard focus,
 * not merely the first visible one. Covers the focus -> first-visible -> 0 fallback chain of
 * [BrowseStateManager.getCurrentFocusPosition].
 */
class BrowseStateManagerFocusPositionTest {

    private val adapter = mockk<MediaFileAdapter>(relaxed = true)
    private val callbacks = mockk<BrowseStateManager.StateCallbacks>(relaxed = true)

    private fun manager(recyclerView: RecyclerView) =
        BrowseStateManager(recyclerView, adapter, callbacks)

    @Test
    fun focusedChild_returnsItsAdapterPosition() {
        val focused = mockk<View>()
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        every { recyclerView.focusedChild } returns focused
        every { recyclerView.getChildAdapterPosition(focused) } returns 7

        assertEquals(7, manager(recyclerView).getCurrentFocusPosition())
    }

    @Test
    fun noFocusedChild_fallsBackToFirstVisible() {
        val layoutManager = mockk<LinearLayoutManager>(relaxed = true)
        every { layoutManager.findFirstVisibleItemPosition() } returns 3
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        every { recyclerView.focusedChild } returns null
        every { recyclerView.layoutManager } returns layoutManager

        assertEquals(3, manager(recyclerView).getCurrentFocusPosition())
    }

    @Test
    fun focusedChildWithNoPosition_fallsBackToFirstVisible() {
        val focused = mockk<View>()
        val layoutManager = mockk<LinearLayoutManager>(relaxed = true)
        every { layoutManager.findFirstVisibleItemPosition() } returns 2
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        every { recyclerView.focusedChild } returns focused
        every { recyclerView.getChildAdapterPosition(focused) } returns RecyclerView.NO_POSITION
        every { recyclerView.layoutManager } returns layoutManager

        assertEquals(2, manager(recyclerView).getCurrentFocusPosition())
    }

    @Test
    fun noFocusAndNoLinearLayoutManager_returnsZero() {
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        every { recyclerView.focusedChild } returns null
        every { recyclerView.layoutManager } returns null

        assertEquals(0, manager(recyclerView).getCurrentFocusPosition())
    }
}
