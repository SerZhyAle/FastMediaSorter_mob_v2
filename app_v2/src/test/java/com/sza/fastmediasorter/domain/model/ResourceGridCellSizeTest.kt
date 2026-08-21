package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceGridCellSizeTest {

    @Test
    fun `default is medium`() {
        assertEquals(ResourceGridCellSize.MEDIUM, ResourceGridCellSize.DEFAULT)
    }

    @Test
    fun `fromName falls back to default on null`() {
        assertEquals(ResourceGridCellSize.MEDIUM, ResourceGridCellSize.fromName(null))
    }

    @Test
    fun `fromName falls back to default on unknown value`() {
        assertEquals(ResourceGridCellSize.MEDIUM, ResourceGridCellSize.fromName("garbage"))
    }

    @Test
    fun `fromName round-trips every entry`() {
        ResourceGridCellSize.values().forEach { entry ->
            assertEquals(entry, ResourceGridCellSize.fromName(entry.name))
        }
    }

    // The owner named five and two resources per row against today's three-column portrait grid.
    // These are the numbers the whole feature was specified by, so they are pinned rather than derived.
    @Test
    fun `span for the default portrait bucket matches the owner's numbers`() {
        val base = 3
        assertEquals(5, ResourceGridCellSize.SMALL.spanFor(base))
        assertEquals(3, ResourceGridCellSize.MEDIUM.spanFor(base))
        assertEquals(2, ResourceGridCellSize.LARGE.spanFor(base))
    }

    @Test
    fun `medium is the identity on every bucket`() {
        listOf(1, 2, 3, 4, 5, 6).forEach { base ->
            assertEquals(base, ResourceGridCellSize.MEDIUM.spanFor(base))
        }
    }

    @Test
    fun `span never drops below one column`() {
        ResourceGridCellSize.values().forEach { entry ->
            assertEquals(true, entry.spanFor(1) >= 1)
        }
    }
}
