package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protects the explicit sort dialog order from silent regressions.
 */
class BrowseDialogHelperTest {

    @Test
    fun `dialog sort order contains every SortMode exactly once`() {
        val order = BrowseSortDialogManager.DIALOG_SORT_ORDER

        assertEquals(SortMode.entries.size, order.size)
        assertEquals(SortMode.entries.toSet(), order.toSet())
    }

    @Test
    fun `dialog sort order keeps requested priority positions`() {
        val order = BrowseSortDialogManager.DIALOG_SORT_ORDER

        assertEquals(SortMode.RANDOM, order.first())
        assertTrue(order.indexOf(SortMode.MANUAL) > order.indexOf(SortMode.SIZE_DESC))
        assertTrue(order.indexOf(SortMode.TYPE_ASC) > order.indexOf(SortMode.DURATION_DESC))
        assertTrue(order.indexOf(SortMode.TYPE_DESC) > order.indexOf(SortMode.DURATION_DESC))
    }

    @Test
    fun `dialog sort order matches the documented sequence`() {
        val expectedOrder = listOf(
            SortMode.RANDOM,
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.DATE_ASC,
            SortMode.DATE_DESC,
            SortMode.DATE_TAKEN_ASC,
            SortMode.DATE_TAKEN_DESC,
            SortMode.SIZE_ASC,
            SortMode.SIZE_DESC,
            SortMode.MANUAL,
            SortMode.DURATION_ASC,
            SortMode.DURATION_DESC,
            SortMode.TYPE_ASC,
            SortMode.TYPE_DESC,
            SortMode.ARTIST_ASC,
            SortMode.ARTIST_DESC,
            SortMode.TITLE_ASC,
            SortMode.TITLE_DESC
        )

        assertEquals(expectedOrder, BrowseSortDialogManager.DIALOG_SORT_ORDER)
    }
}