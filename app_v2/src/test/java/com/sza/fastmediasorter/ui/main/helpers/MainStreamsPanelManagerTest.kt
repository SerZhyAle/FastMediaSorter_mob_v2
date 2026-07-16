package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0808: unit tests for [MainStreamsPanelManager.resolveVisibility] - the pure decision behind the
 * expanded(content row) <-> collapsed(strip) toggle of the streams panel. Mirrors the programs-panel
 * collapse resolver (MainProgramsPanelManagerTest) so the "root owns availability" precedence and the
 * "never both shown" invariant are testable without Android view dependencies.
 *
 * S1061: also covers [MainStreamsPanelManager.resolveContentSlot] - the channel-list <-> empty-hint swap
 * inside a shown row - and the composition of the two, which is where "the hint never outlives a
 * collapse" actually holds.
 *
 * [MainStreamsPanelManager] carries Media3's @UnstableApi, but that is a lint annotation (not
 * @RequiresOptIn), so referencing the pure companion resolver needs no opt-in here.
 */
class MainStreamsPanelManagerTest {

    @Test
    fun `available and expanded - content row shown, strip hidden`() {
        val (contentVisible, stripVisible) =
            MainStreamsPanelManager.resolveVisibility(available = true, collapsed = false)
        assertTrue("expanded: content row visible", contentVisible)
        assertFalse("expanded: strip hidden", stripVisible)
    }

    @Test
    fun `available and collapsed - strip shown, content row hidden`() {
        val (contentVisible, stripVisible) =
            MainStreamsPanelManager.resolveVisibility(available = true, collapsed = true)
        assertFalse("collapsed: content row hidden", contentVisible)
        assertTrue("collapsed: strip visible", stripVisible)
    }

    @Test
    fun `unavailable - both hidden regardless of collapsed`() {
        val (expandedContent, expandedStrip) =
            MainStreamsPanelManager.resolveVisibility(available = false, collapsed = false)
        assertFalse("hidden + expanded: content row hidden", expandedContent)
        assertFalse("hidden + expanded: strip hidden", expandedStrip)

        val (collapsedContent, collapsedStrip) =
            MainStreamsPanelManager.resolveVisibility(available = false, collapsed = true)
        assertFalse("hidden + collapsed: content row hidden", collapsedContent)
        assertFalse("hidden + collapsed: strip hidden", collapsedStrip)
    }

    @Test
    fun `content row and strip are never both shown`() {
        for (available in listOf(true, false)) {
            for (collapsed in listOf(true, false)) {
                val (contentVisible, stripVisible) =
                    MainStreamsPanelManager.resolveVisibility(available, collapsed)
                assertFalse(
                    "available=$available collapsed=$collapsed must not show both",
                    contentVisible && stripVisible
                )
            }
        }
    }

    @Test
    fun `S1061 - shown row with nothing pinned shows the hint, not the channel list`() {
        val (channelsVisible, hintVisible) =
            MainStreamsPanelManager.resolveContentSlot(contentVisible = true, pinnedEmpty = true)
        assertFalse("unpinned: channel list hidden", channelsVisible)
        assertTrue("unpinned: hint visible", hintVisible)
    }

    @Test
    fun `S1061 - shown row with pinned channels shows the list, not the hint`() {
        val (channelsVisible, hintVisible) =
            MainStreamsPanelManager.resolveContentSlot(contentVisible = true, pinnedEmpty = false)
        assertTrue("pinned: channel list visible", channelsVisible)
        assertFalse("pinned: hint hidden", hintVisible)
    }

    @Test
    fun `S1061 - hidden content row paints neither, regardless of the pinned count`() {
        for (pinnedEmpty in listOf(true, false)) {
            val (channelsVisible, hintVisible) =
                MainStreamsPanelManager.resolveContentSlot(contentVisible = false, pinnedEmpty = pinnedEmpty)
            assertFalse("hidden row (pinnedEmpty=$pinnedEmpty): channel list hidden", channelsVisible)
            assertFalse("hidden row (pinnedEmpty=$pinnedEmpty): hint hidden", hintVisible)
        }
    }

    /**
     * S1061: the hint must not survive a collapse or an unavailable panel. That guarantee comes from
     * composing the two resolvers the way [MainStreamsPanelManager.applyVisibility] does - feeding the
     * row's own verdict into the slot resolver - so the composition is what gets asserted here, over
     * every combination of the three axes.
     */
    @Test
    fun `S1061 - composed resolvers - hint only on an available, expanded, unpinned panel`() {
        for (available in listOf(true, false)) {
            for (collapsed in listOf(true, false)) {
                for (pinnedEmpty in listOf(true, false)) {
                    val (contentVisible, _) = MainStreamsPanelManager.resolveVisibility(available, collapsed)
                    val (channelsVisible, hintVisible) =
                        MainStreamsPanelManager.resolveContentSlot(contentVisible, pinnedEmpty)
                    val case = "available=$available collapsed=$collapsed pinnedEmpty=$pinnedEmpty"
                    assertFalse("$case must not show list and hint together", channelsVisible && hintVisible)
                    assertEquals("$case hint", available && !collapsed && pinnedEmpty, hintVisible)
                    assertEquals("$case list", available && !collapsed && !pinnedEmpty, channelsVisible)
                }
            }
        }
    }
}
