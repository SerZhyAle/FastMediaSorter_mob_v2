package com.sza.fastmediasorter.ui.main.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the S4041 table model: search, header sort, the width threshold and the view-toggle
 * cycle. These rules are the strategic spec's section 5.1, proven without a device.
 */
class ResourceTableModelTest {

    private data class Row(val id: Int, val name: String, val source: String, val path: String, val count: Int?)

    private val alpha = Row(1, "Alpha", "Local", "/storage/alpha", 10)
    private val bravo = Row(2, "bravo", "SMB", "smb://nas/music", null)
    private val charlie = Row(3, "Charlie", "Local", "/storage/charlie", 2)
    private val delta = Row(4, "Delta", "SMB", "smb://nas/photos", null)
    private val echo = Row(5, "Echo", "Local", "/storage/echo", 10)

    private val saved = listOf(alpha, bravo, charlie, delta, echo)

    private fun keys(row: Row) = ResourceTableKeys(
        name = row.name,
        source = row.source,
        content = "",
        path = row.path,
        fileCount = row.count,
    )

    private fun build(query: String = "", sort: ResourceTableSort? = null): List<Int> =
        ResourceTableRowsBuilder.build(saved, query, sort, ::keys).map { it.id }

    @Test
    fun `no query and no sort keep the saved order`() {
        assertEquals(listOf(1, 2, 3, 4, 5), build())
    }

    @Test
    fun `query matches name and path ignoring case`() {
        assertEquals(listOf(2, 4), build(query = "NAS"))
        assertEquals(listOf(3), build(query = "charL"))
    }

    @Test
    fun `blank query is the same as no query`() {
        assertEquals(listOf(1, 2, 3, 4, 5), build(query = "   "))
    }

    @Test
    fun `name sort ignores case and flips on descending`() {
        assertEquals(listOf(1, 2, 3, 4, 5), build(sort = ResourceTableSort(ResourceTableColumn.NAME)))
        assertEquals(
            listOf(5, 4, 3, 2, 1),
            build(sort = ResourceTableSort(ResourceTableColumn.NAME, ascending = false)),
        )
    }

    @Test
    fun `equal keys keep their saved order in both directions`() {
        assertEquals(listOf(1, 3, 5, 2, 4), build(sort = ResourceTableSort(ResourceTableColumn.SOURCE)))
        assertEquals(
            listOf(2, 4, 1, 3, 5),
            build(sort = ResourceTableSort(ResourceTableColumn.SOURCE, ascending = false)),
        )
    }

    @Test
    fun `unknown counts sort after known ones in both directions`() {
        assertEquals(listOf(3, 1, 5, 2, 4), build(sort = ResourceTableSort(ResourceTableColumn.FILES)))
        assertEquals(
            listOf(1, 5, 3, 2, 4),
            build(sort = ResourceTableSort(ResourceTableColumn.FILES, ascending = false)),
        )
    }

    @Test
    fun `search and sort combine`() {
        assertEquals(
            listOf(5, 3, 1),
            build(query = "storage", sort = ResourceTableSort(ResourceTableColumn.PATH, ascending = false)),
        )
    }

    @Test
    fun `header press flips the same column and restarts another ascending`() {
        val first = ResourceTableSort.pressed(null, ResourceTableColumn.NAME)
        assertEquals(ResourceTableSort(ResourceTableColumn.NAME, ascending = true), first)
        val second = ResourceTableSort.pressed(first, ResourceTableColumn.NAME)
        assertEquals(ResourceTableSort(ResourceTableColumn.NAME, ascending = false), second)
        val third = ResourceTableSort.pressed(second, ResourceTableColumn.PATH)
        assertEquals(ResourceTableSort(ResourceTableColumn.PATH, ascending = true), third)
    }

    @Test
    fun `width threshold is 840 dp at default text and grows with the text scale`() {
        assertEquals(840, ResourceTableWidthPolicy.minimumWindowWidthDp(1f))
        assertEquals(840, ResourceTableWidthPolicy.minimumWindowWidthDp(0.85f))
        assertEquals(1008, ResourceTableWidthPolicy.minimumWindowWidthDp(1.3f))
        assertTrue(ResourceTableWidthPolicy.isTableEligible(1280, 1f))
        assertFalse(ResourceTableWidthPolicy.isTableEligible(800, 1f))
        assertFalse(ResourceTableWidthPolicy.isTableEligible(960, 1.3f))
    }

    @Test
    fun `table preference shows only in an eligible window`() {
        val tablePreferred = ResourceViewPreference(grid = false, table = true)
        assertEquals(ResourceViewMode.TABLE, ResourceViewMode.effective(tablePreferred, tableEligible = true))
        assertEquals(ResourceViewMode.LIST, ResourceViewMode.effective(tablePreferred, tableEligible = false))
        val both = ResourceViewPreference(grid = true, table = true)
        assertEquals(ResourceViewMode.GRID, ResourceViewMode.effective(both, tableEligible = false))
    }

    @Test
    fun `wide toggle cycles list grid table list`() {
        var preference = ResourceViewPreference(grid = false, table = false)
        val seen = mutableListOf<ResourceViewMode>()
        repeat(4) {
            preference = ResourceViewMode.next(preference, tableEligible = true)
            seen += ResourceViewMode.effective(preference, tableEligible = true)
        }
        assertEquals(
            listOf(ResourceViewMode.GRID, ResourceViewMode.TABLE, ResourceViewMode.LIST, ResourceViewMode.GRID),
            seen,
        )
    }

    @Test
    fun `narrow toggle flips grid and keeps the table preference`() {
        val tablePreferred = ResourceViewPreference(grid = false, table = true)
        val next = ResourceViewMode.next(tablePreferred, tableEligible = false)
        assertEquals(ResourceViewPreference(grid = true, table = true), next)
        assertEquals(ResourceViewMode.TABLE, ResourceViewMode.effective(next, tableEligible = true))
    }
}
