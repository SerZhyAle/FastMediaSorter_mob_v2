package com.sza.fastmediasorter.core.menu

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1424: the channel counterpart of [ResourceActionCatalogTest]. Two rows change caption with the
 * channel's state, so the label resolution is asserted alongside the composition.
 */
class StreamActionCatalogTest {

    private val catalogChannel = StreamActionCatalog.Facts()

    @Test
    fun `a catalog channel on the streams screen offers pin, shortcut, link and removal`() {
        val actions = StreamActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, catalogChannel)

        assertEquals(
            listOf(
                StreamMenuAction.TOGGLE_PIN,
                StreamMenuAction.ADD_SHORTCUT,
                // S1474: a reading action, so it sits above sharing and clear of removal.
                StreamMenuAction.ABOUT_CHANNEL,
                StreamMenuAction.SHARE_LINK,
                StreamMenuAction.REMOVE,
            ),
            actions,
        )
    }

    @Test
    fun `only a user-added channel can be edited`() {
        val manual = StreamActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            catalogChannel.copy(isManualOrigin = true),
        )

        assertTrue(manual.contains(StreamMenuAction.EDIT))
        assertFalse(
            StreamActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, catalogChannel)
                .contains(StreamMenuAction.EDIT),
        )
    }

    @Test
    fun `reorder rows appear only while the channel sits in a reorderable pinned block`() {
        val reorderable = StreamActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            catalogChannel.copy(isPinned = true, isReorderable = true),
        )

        assertTrue(reorderable.contains(StreamMenuAction.MOVE_UP))
        assertTrue(reorderable.contains(StreamMenuAction.MOVE_DOWN))
        assertTrue(reorderable.contains(StreamMenuAction.MOVE_TO_TOP))
    }

    @Test
    fun `the favourite row follows the feature toggle, not the channel`() {
        val enabled = StreamActionCatalog.actionsFor(
            MenuActionSurface.MAIN_WINDOW,
            catalogChannel.copy(favoritesEnabled = true),
        )

        assertTrue(enabled.contains(StreamMenuAction.TOGGLE_FAVORITE))
        assertFalse(
            StreamActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, catalogChannel)
                .contains(StreamMenuAction.TOGGLE_FAVORITE),
        )
    }

    @Test
    fun `desktop never offers a reorder row or the favourite toggle`() {
        val everythingOn = StreamActionCatalog.Facts(
            isPinned = true,
            isFavorite = true,
            favoritesEnabled = true,
            isReorderable = true,
            isManualOrigin = true,
        )

        val actions = StreamActionCatalog.actionsFor(MenuActionSurface.LAUNCHER_DESKTOP, everythingOn)

        assertFalse(actions.contains(StreamMenuAction.TOGGLE_FAVORITE))
        assertFalse(actions.contains(StreamMenuAction.MOVE_UP))
        assertFalse(actions.contains(StreamMenuAction.MOVE_DOWN))
        assertFalse(actions.contains(StreamMenuAction.MOVE_TO_TOP))
        // S1474: the window needs a decoder and the streams screen's probe manager; a desktop cell has
        // neither, so the action is offered on the card and the player menus only.
        assertFalse(actions.contains(StreamMenuAction.ABOUT_CHANNEL))
        assertTrue(actions.contains(StreamMenuAction.EDIT))
    }

    @Test
    fun `the two state-dependent captions flip with the channel`() {
        val pinnedFavorite = StreamActionCatalog.Facts(isPinned = true, isFavorite = true)

        assertEquals(R.string.streams_unpin, StreamActionCatalog.labelRes(StreamMenuAction.TOGGLE_PIN, pinnedFavorite))
        assertEquals(
            R.string.streams_remove_from_favorites,
            StreamActionCatalog.labelRes(StreamMenuAction.TOGGLE_FAVORITE, pinnedFavorite),
        )
        assertEquals(R.string.streams_pin, StreamActionCatalog.labelRes(StreamMenuAction.TOGGLE_PIN, catalogChannel))
        assertEquals(
            R.string.streams_add_to_favorites,
            StreamActionCatalog.labelRes(StreamMenuAction.TOGGLE_FAVORITE, catalogChannel),
        )
    }

    @Test
    fun `a host that cannot run an action leaves it out rather than showing it dead`() {
        val actions = StreamActionCatalog.actionsFor(
            MenuActionSurface.LAUNCHER_DESKTOP,
            catalogChannel,
        ) { it != StreamMenuAction.SHARE_LINK }

        assertFalse(actions.contains(StreamMenuAction.SHARE_LINK))
        assertTrue(actions.contains(StreamMenuAction.REMOVE))
    }
}
