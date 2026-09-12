package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2533: guards the direction-to-field mapping. A compile cannot catch a direction that reads one
 * field and writes another, which is the failure the accessors exist to prevent.
 */
class BrowseSwipeDirectionTest {

    @Test
    fun `actionOf reads what withAction wrote for every direction and action`() {
        for (direction in BrowseSwipeDirection.entries) {
            for (action in listOf(BrowseSwipeAction.NONE, BrowseSwipeAction.RENAME, BrowseSwipeAction.INFO)) {
                val updated = direction.withAction(AppSettings(), action)
                assertEquals(action, direction.actionOf(updated))
            }
        }
    }

    @Test
    fun `withAction leaves the other direction untouched`() {
        val base = AppSettings()
        val leftChanged = BrowseSwipeDirection.LEFT.withAction(base, BrowseSwipeAction.COPY)
        assertEquals(
            BrowseSwipeDirection.RIGHT.actionOf(base),
            BrowseSwipeDirection.RIGHT.actionOf(leftChanged),
        )

        val rightChanged = BrowseSwipeDirection.RIGHT.withAction(base, BrowseSwipeAction.MOVE)
        assertEquals(
            BrowseSwipeDirection.LEFT.actionOf(base),
            BrowseSwipeDirection.LEFT.actionOf(rightChanged),
        )
    }

    @Test
    fun `clean install defaults are delete on the left and send-to on the right`() {
        val settings = AppSettings()
        assertEquals(BrowseSwipeAction.DELETE, BrowseSwipeDirection.LEFT.actionOf(settings))
        assertEquals(BrowseSwipeAction.SEND_TO, BrowseSwipeDirection.RIGHT.actionOf(settings))
        assertEquals(BrowseSwipeAction.DELETE, BrowseSwipeDirection.LEFT.default)
        assertEquals(BrowseSwipeAction.SEND_TO, BrowseSwipeDirection.RIGHT.default)
    }

    @Test
    fun `fromName round-trips every entry through its name`() {
        for (action in BrowseSwipeAction.entries) {
            assertEquals(action, BrowseSwipeAction.fromName(action.name, BrowseSwipeAction.NONE))
        }
    }

    @Test
    fun `fromName falls back to the default for null and for an unknown name`() {
        assertEquals(
            BrowseSwipeAction.SEND_TO,
            BrowseSwipeAction.fromName(null, BrowseSwipeAction.SEND_TO),
        )
        assertEquals(
            BrowseSwipeAction.DELETE,
            BrowseSwipeAction.fromName("REORDER", BrowseSwipeAction.DELETE),
        )
    }

    @Test
    fun `only the mutating actions require write access`() {
        val requiring = BrowseSwipeAction.entries.filter { it.requiresWriteAccess }.toSet()
        assertEquals(
            setOf(
                BrowseSwipeAction.MOVE,
                BrowseSwipeAction.RENAME,
                BrowseSwipeAction.EXTRACT_ARCHIVE,
                BrowseSwipeAction.DELETE,
            ),
            requiring,
        )
    }
}
