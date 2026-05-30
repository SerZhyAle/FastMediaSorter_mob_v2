package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileFilter.isEmpty] and [FileFilter.activeFilterCount].
 */
class FileFilterTest {

    @Test
    fun `default filter is empty`() {
        assertTrue(FileFilter().isEmpty())
        assertEquals(0, FileFilter().activeFilterCount())
    }

    @Test
    fun `blank name does not count as an active filter`() {
        assertTrue(FileFilter(nameContains = "   ").isEmpty())
        assertEquals(0, FileFilter(nameContains = "").activeFilterCount())
    }

    @Test
    fun `non-blank name is one active filter`() {
        val filter = FileFilter(nameContains = "holiday")
        assertFalse(filter.isEmpty())
        assertEquals(1, filter.activeFilterCount())
    }

    @Test
    fun `either date bound counts the date filter exactly once`() {
        assertEquals(1, FileFilter(minDate = 10L).activeFilterCount())
        assertEquals(1, FileFilter(maxDate = 20L).activeFilterCount())
        assertEquals(1, FileFilter(minDate = 10L, maxDate = 20L).activeFilterCount())
    }

    @Test
    fun `either size bound counts the size filter exactly once`() {
        assertEquals(1, FileFilter(minSizeMb = 1f).activeFilterCount())
        assertEquals(1, FileFilter(maxSizeMb = 5f).activeFilterCount())
        assertEquals(1, FileFilter(minSizeMb = 1f, maxSizeMb = 5f).activeFilterCount())
    }

    @Test
    fun `empty media-types set does not count as an active filter`() {
        // isEmpty() only checks `mediaTypes == null`, so a non-null empty set keeps it non-empty,
        // while activeFilterCount() uses isNullOrEmpty() and reports zero. Both behaviours are asserted.
        val filter = FileFilter(mediaTypes = emptySet())
        assertFalse(filter.isEmpty())
        assertEquals(0, filter.activeFilterCount())
    }

    @Test
    fun `null media-types keeps the filter empty`() {
        assertTrue(FileFilter(mediaTypes = null).isEmpty())
    }

    @Test
    fun `non-empty media-types set is one active filter`() {
        val filter = FileFilter(mediaTypes = setOf(MediaType.IMAGE))
        assertFalse(filter.isEmpty())
        assertEquals(1, filter.activeFilterCount())
    }

    @Test
    fun `all four criteria active yields a count of four`() {
        val filter = FileFilter(
            nameContains = "x",
            minDate = 1L,
            maxSizeMb = 9f,
            mediaTypes = setOf(MediaType.VIDEO),
        )
        assertFalse(filter.isEmpty())
        assertEquals(4, filter.activeFilterCount())
    }
}
