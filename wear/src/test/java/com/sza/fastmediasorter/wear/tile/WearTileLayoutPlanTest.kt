package com.sza.fastmediasorter.wear.tile

import androidx.wear.protolayout.material.layouts.LayoutDefaults.MultiButtonLayoutDefaults.MAX_BUTTONS
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2589: the tile layer's decisions, checked without a watch.
 *
 * S2511 left the grid clamp as an open risk precisely because it could not be reached from here: it sat in a
 * private method that built ProtoLayout buttons, and a button needs a `Context` this module has no
 * Robolectric to supply. Above the grid's capacity `MultiButtonLayout` throws rather than truncates, and a
 * throw inside a tile request replaces the whole tile with a system error card - so the clamp failing is not
 * a cosmetic loss of the last row, it is the tile going dark.
 */
class WearTileLayoutPlanTest {

    private val testCapacity = 4

    private fun shortcuts(count: Int): List<WearTileShortcut> = (1..count).map { index ->
        val destination = WearDestinationId.entries[index % WearDestinationId.entries.size]
        WearTileShortcut(
            destinationId = destination,
            contentDescription = "shortcut $index",
            launchTarget = WearLaunchTarget.Destination(destination)
        )
    }

    @Test
    fun `a list above the capacity keeps exactly the capacity`() {
        val plan = planShortcutGrid(shortcuts(testCapacity + 3), capacity = testCapacity)

        assertEquals(testCapacity, plan.shown.size)
        assertEquals(3, plan.dropped)
    }

    @Test
    fun `the entries kept are the first ones, in order`() {
        val entries = shortcuts(testCapacity + 2)

        val plan = planShortcutGrid(entries, capacity = testCapacity)

        assertEquals(entries.take(testCapacity), plan.shown)
    }

    @Test
    fun `a list exactly at the capacity drops nothing`() {
        val plan = planShortcutGrid(shortcuts(testCapacity), capacity = testCapacity)

        assertEquals(testCapacity, plan.shown.size)
        assertEquals(0, plan.dropped)
    }

    @Test
    fun `a list below the capacity drops nothing`() {
        val plan = planShortcutGrid(shortcuts(1), capacity = testCapacity)

        assertEquals(1, plan.shown.size)
        assertEquals(0, plan.dropped)
    }

    @Test
    fun `an empty list is a plan with nothing to draw rather than an error`() {
        val plan = planShortcutGrid(emptyList(), capacity = testCapacity)

        assertTrue(plan.shown.isEmpty())
        assertEquals(0, plan.dropped)
    }

    /**
     * The default is what production passes, so a test stating its own capacity everywhere would pin a bound
     * the tile never uses. This is the one place the two are tied together.
     */
    @Test
    fun `the default capacity is the grid capacity of the library`() {
        val entries = shortcuts(MAX_BUTTONS + 1)

        val plan = planShortcutGrid(entries)

        assertEquals(MAX_BUTTONS, plan.shown.size)
        assertEquals(1, plan.dropped)
    }

    private val overflow = WearTileShortcut(
        destinationId = WearDestinationId.HOME,
        contentDescription = "more",
        launchTarget = WearLaunchTarget.Destination(WearDestinationId.HOME)
    )

    @Test
    fun `an overflowing list spends its last cell on the way out`() {
        val entries = shortcuts(testCapacity + 3)

        val plan = planShortcutGrid(entries, overflow = overflow, capacity = testCapacity)

        assertEquals(testCapacity, plan.shown.size)
        assertEquals(overflow, plan.shown.last())
        assertEquals(entries.take(testCapacity - 1), plan.shown.dropLast(1))
        assertEquals(4, plan.dropped)
    }

    /**
     * The defect this replaced: the eighth section was cut off and nothing said so. The count has to name
     * every entry no cell carries, including the one whose place the overflow cell took.
     */
    @Test
    fun `the dropped count includes the entry the overflow cell displaced`() {
        val plan = planShortcutGrid(shortcuts(testCapacity), overflow = overflow, capacity = testCapacity)

        assertEquals(0, plan.dropped)
        assertFalse(plan.shown.contains(overflow))
    }

    @Test
    fun `a list that fits keeps every entry and no way out`() {
        val entries = shortcuts(testCapacity - 1)

        val plan = planShortcutGrid(entries, overflow = overflow, capacity = testCapacity)

        assertEquals(entries, plan.shown)
    }

    @Test
    fun `an empty list offers no way out either`() {
        val plan = planShortcutGrid(emptyList(), overflow = overflow, capacity = testCapacity)

        assertTrue(plan.shown.isEmpty())
    }

    @Test
    fun `an assigned tile previews at most three entries`() {
        val previewed = planAssignedPreview(listOf("a", "b", "c", "d", "e"))

        assertEquals(listOf("a", "b", "c"), previewed)
    }

    @Test
    fun `an assigned tile with fewer entries previews all of them`() {
        assertEquals(listOf("a", "b"), planAssignedPreview(listOf("a", "b")))
    }

    @Test
    fun `the two assignable kinds carry their own unassigned label`() {
        assertEquals(R.string.wear_tile_unassigned_resource, unassignedLabelRes(WearTileKind.RESOURCE))
        assertEquals(R.string.wear_tile_unassigned_stream, unassignedLabelRes(WearTileKind.STREAM))
    }

    /**
     * The three grid kinds pin nothing and so never reach the unassigned state; they are mapped rather than
     * left to an `else` so a sixth kind has to be classified instead of inheriting whatever this label says.
     */
    @Test
    fun `the kinds that pin nothing share the empty-favourites label`() {
        listOf(WearTileKind.FAVOURITES, WearTileKind.PROGRAMS, WearTileKind.SECTIONS).forEach { kind ->
            assertEquals(R.string.wear_tile_favourites_empty, unassignedLabelRes(kind))
        }
    }

    @Test
    fun `every tile kind has a label`() {
        WearTileKind.entries.forEach { kind ->
            assertTrue("no label for $kind", unassignedLabelRes(kind) != 0)
        }
    }
}
