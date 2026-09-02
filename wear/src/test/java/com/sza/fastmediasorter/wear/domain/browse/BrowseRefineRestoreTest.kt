package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2199: strategic ADR-7 exists to stop persistence from producing an empty screen, and the branch
 * that does it - a stored filter naming nothing this route holds - is the one a walk on a watch is
 * least likely to hit by accident, because it needs two different routes visited in order.
 */
class BrowseRefineRestoreTest {

    @Test
    fun `a stored set fully present in the route applies unchanged`() {
        val restore = BrowseRefineRestore()
        restore.remember(setOf(WearContentType.MUSIC, WearContentType.VIDEO))

        val applicable = restore.consume(
            listOf(WearContentType.MUSIC, WearContentType.VIDEO, WearContentType.IMAGE)
        )

        assertEquals(setOf(WearContentType.MUSIC, WearContentType.VIDEO), applicable)
    }

    @Test
    fun `a stored set only partly present narrows to the present part`() {
        val restore = BrowseRefineRestore()
        restore.remember(setOf(WearContentType.MUSIC, WearContentType.DOCUMENT))

        val applicable = restore.consume(listOf(WearContentType.MUSIC, WearContentType.IMAGE))

        assertEquals(setOf(WearContentType.MUSIC), applicable)
    }

    @Test
    fun `a stored set naming nothing present applies no filter at all`() {
        val restore = BrowseRefineRestore()
        restore.remember(setOf(WearContentType.MUSIC, WearContentType.VIDEO))

        val applicable = restore.consume(listOf(WearContentType.IMAGE))

        // Null, not an empty set: an empty contentTypes means "every type", so returning one would
        // read as a filter the wearer cleared rather than one that never applied.
        assertNull(applicable)
    }

    @Test
    fun `an empty stored set applies no filter at all`() {
        val restore = BrowseRefineRestore()
        restore.remember(emptySet())

        val applicable = restore.consume(listOf(WearContentType.MUSIC, WearContentType.IMAGE))

        assertNull(applicable)
    }

    @Test
    fun `a route holding nothing applies no filter at all`() {
        val restore = BrowseRefineRestore()
        restore.remember(setOf(WearContentType.MUSIC))

        val applicable = restore.consume(emptyList())

        assertNull(applicable)
    }

    @Test
    fun `a second reload does not re-apply a filter the wearer has since cleared`() {
        val restore = BrowseRefineRestore()
        restore.remember(setOf(WearContentType.MUSIC))
        val present = listOf(WearContentType.MUSIC, WearContentType.IMAGE)

        assertEquals(setOf(WearContentType.MUSIC), restore.consume(present))
        assertNull(restore.consume(present))
    }

    @Test
    fun `consuming without anything remembered applies no filter at all`() {
        val restore = BrowseRefineRestore()

        assertNull(restore.consume(listOf(WearContentType.MUSIC)))
    }
}
