package com.sza.fastmediasorter.ui.xr.browse

import com.sza.fastmediasorter.core.xr.VrMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1133: unit coverage for thumbstick grid navigation. The stick itself only exists on a Quest, but
 * the page arithmetic it drives and the trigger edge that activates its selection are pure Kotlin,
 * so they are proven here instead of on a headset the spec (section 10) says is rarely available.
 */
class ImmersiveBrowseInteractionDispatcherTest {

    private val renderer = ImmersiveBrowseGridRenderer()
    private val dispatcher = ImmersiveBrowseInteractionDispatcher(renderer)

    private val columns = ImmersiveBrowseGridRenderer.COLUMNS

    private fun cells(count: Int): List<ImmersiveBrowseCell> = List(count) { index ->
        ImmersiveBrowseCell(
            index = index,
            label = "cell-$index",
            isFolder = false,
            mediaType = VrMediaType.IMAGE,
            folderPath = null,
            filePath = "/media/cell-$index.jpg",
            stereoBadge = null,
        )
    }

    @Test
    fun first_step_from_no_selection_lands_on_first_cell_of_page() {
        val resolved = dispatcher.navigate(dx = 1, dy = 0, cells = cells(12), pageOffset = 0)

        assertEquals(0, resolved)
        assertTrue(dispatcher.isStickSelection)
    }

    @Test
    fun step_right_walks_one_column() {
        val page = cells(12)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        assertEquals(1, dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0))
    }

    @Test
    fun step_down_walks_one_row() {
        val page = cells(12)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        assertEquals(columns, dispatcher.navigate(dx = 0, dy = -1, cells = page, pageOffset = 0))
    }

    @Test
    fun step_up_past_the_top_row_is_swallowed() {
        val page = cells(12)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        assertEquals(0, dispatcher.navigate(dx = 0, dy = 1, cells = page, pageOffset = 0))
    }

    @Test
    fun step_down_past_the_bottom_row_is_swallowed() {
        val page = cells(12)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)
        dispatcher.navigate(dx = 0, dy = -1, cells = page, pageOffset = 0)

        assertEquals(columns, dispatcher.navigate(dx = 0, dy = -1, cells = page, pageOffset = 0))
    }

    @Test
    fun step_past_the_right_column_turns_the_page() {
        val page = cells(renderer.pageSize * 2)
        var scrolled: Int? = null
        dispatcher.onPageScroll = { scrolled = it }
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)
        repeat(columns - 1) { dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0) }

        val resolved = dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        assertEquals(1, scrolled)
        assertEquals(renderer.pageSize, resolved)
    }

    @Test
    fun step_past_the_left_column_of_the_first_page_is_swallowed() {
        val page = cells(12)
        var scrolled: Int? = null
        dispatcher.onPageScroll = { scrolled = it }
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        assertEquals(0, dispatcher.navigate(dx = -1, dy = 0, cells = page, pageOffset = 0))
        assertNull(scrolled)
    }

    @Test
    fun trigger_off_panel_activates_the_stick_selection() {
        val page = cells(12)
        var selected: ImmersiveBrowseCell? = null
        dispatcher.onCellSelected = { selected = it }
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        dispatcher.dispatch(
            uvX = 0f,
            uvY = 0f,
            isHover = false,
            isClick = true,
            cells = page,
            pageOffset = 0,
        )

        assertEquals(1, selected?.index)
    }

    @Test
    fun held_trigger_off_panel_activates_once() {
        val page = cells(12)
        var selections = 0
        dispatcher.onCellSelected = { selections++ }
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        repeat(3) {
            dispatcher.dispatch(
                uvX = 0f,
                uvY = 0f,
                isHover = false,
                isClick = true,
                cells = page,
                pageOffset = 0,
            )
        }

        assertEquals(1, selections)
    }

    @Test
    fun stick_selection_survives_the_ray_pointing_away() {
        val page = cells(12)
        dispatcher.navigate(dx = 1, dy = 0, cells = page, pageOffset = 0)

        dispatcher.dispatch(
            uvX = 0f,
            uvY = 0f,
            isHover = false,
            isClick = false,
            cells = page,
            pageOffset = 0,
        )

        assertEquals(0, dispatcher.hoveredIndex)
        assertTrue(dispatcher.isStickSelection)
    }
}
