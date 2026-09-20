package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaItemViewTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
    }

    @Test
    fun `selection state sets isSelected and leaves the background drawable untouched`() {
        val view = MediaItemView(context)
        val backgroundBefore = view.background

        view.setSelectionState(true)

        assertTrue(view.isSelected)
        assertSame("selection must not replace the row background", backgroundBefore, view.background)
    }

    @Test
    fun `clearing the selection keeps the same background instance`() {
        val view = MediaItemView(context)
        view.setSelectionState(true)
        val backgroundWhileSelected = view.background

        view.setSelectionState(false)

        assertFalse(view.isSelected)
        assertSame(backgroundWhileSelected, view.background)
    }

    @Test
    fun `row carries a focus selector background and an interaction overlay foreground`() {
        val view = MediaItemView(context)

        assertTrue("row must have a state-list background", view.background != null)
        assertTrue("row must have a ripple foreground", view.foreground != null)
        assertTrue(view.isFocusable)
        assertFalse(view.isFocusableInTouchMode)
    }

    @Test
    fun `visible checkbox follows the selection state`() {
        val view = MediaItemView(context)
        view.setSelectionVisible(true)

        view.setSelectionState(true)

        val box = view.findViewById<android.widget.CheckBox>(R.id.cbSelect)
        assertEquals(View.VISIBLE, box.visibility)
        assertTrue(box.isChecked)
    }

    @Test
    fun `layout mode switches move the thumbnail constraints`() {
        val view = MediaItemView(context)
        assertEquals(MediaItemView.LayoutMode.LIST, view.layoutMode)
        val listHeight = thumbnailParams(view).height

        view.setLayoutMode(MediaItemView.LayoutMode.GRID)
        assertEquals(MediaItemView.LayoutMode.GRID, view.layoutMode)
        assertNotEquals(listHeight, thumbnailParams(view).height)

        view.setLayoutMode(MediaItemView.LayoutMode.PLANK)
        assertEquals(MediaItemView.LayoutMode.PLANK, view.layoutMode)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.item_thumbnail_size),
            thumbnailParams(view).width,
        )
    }

    @Test
    fun `text setters fill their own child and hide it when empty`() {
        val view = MediaItemView(context)

        view.setTitle("IMG_0001.jpg")
        view.setSubtitle("2.5 MB")
        view.setDetail("2026-09-19")

        assertEquals("IMG_0001.jpg", textOf(view, R.id.tvTitle))
        assertEquals("2.5 MB", textOf(view, R.id.tvSubtitle))
        assertEquals("2026-09-19", textOf(view, R.id.tvDetail))

        view.setSubtitle(null)

        assertEquals(View.GONE, view.findViewById<TextView>(R.id.tvSubtitle).visibility)
        assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.tvTitle).visibility)
    }

    @Test
    fun `badge shows its label and hides on null or empty content`() {
        val view = MediaItemView(context)
        val badgeView = view.findViewById<TextView>(R.id.tvBadge)

        view.setBadge(ItemBadge(text = "PIN", tone = ItemBadge.Tone.ACCENT))

        assertEquals(View.VISIBLE, badgeView.visibility)
        assertEquals("PIN", badgeView.text.toString())

        view.setBadge(ItemBadge())
        assertEquals(View.GONE, badgeView.visibility)

        view.setBadge(null)
        assertEquals(View.GONE, badgeView.visibility)
    }

    private fun thumbnailParams(view: MediaItemView) =
        view.thumbnailView().layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

    private fun textOf(view: MediaItemView, id: Int): String =
        view.findViewById<TextView>(id).text.toString()
}
