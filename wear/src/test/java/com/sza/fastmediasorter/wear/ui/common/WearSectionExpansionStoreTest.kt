package com.sza.fastmediasorter.wear.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSectionExpansionStoreTest {

    private val store = WearSectionExpansionStore()

    @Test
    fun `group is collapsed when it was never touched`() {
        assertFalse(store.isExpanded(SCREEN, FIRST_GROUP))
    }

    @Test
    fun `toggle opens a group and toggling again closes it`() {
        store.toggle(SCREEN, FIRST_GROUP)
        assertTrue(store.isExpanded(SCREEN, FIRST_GROUP))

        store.toggle(SCREEN, FIRST_GROUP)
        assertFalse(store.isExpanded(SCREEN, FIRST_GROUP))
    }

    @Test
    fun `groups of one screen are independent of each other`() {
        store.toggle(SCREEN, FIRST_GROUP)

        assertTrue(store.isExpanded(SCREEN, FIRST_GROUP))
        assertFalse(store.isExpanded(SCREEN, SECOND_GROUP))
    }

    @Test
    fun `two screens sharing a group id do not share its state`() {
        store.toggle(SCREEN, FIRST_GROUP)

        assertFalse(store.isExpanded(OTHER_SCREEN, FIRST_GROUP))
    }

    @Test
    fun `clear forgets one screen and leaves the other alone`() {
        store.toggle(SCREEN, FIRST_GROUP)
        store.toggle(OTHER_SCREEN, FIRST_GROUP)

        store.clear(SCREEN)

        assertFalse(store.isExpanded(SCREEN, FIRST_GROUP))
        assertTrue(store.isExpanded(OTHER_SCREEN, FIRST_GROUP))
    }

    private companion object {
        const val SCREEN = "apps/systeminfo"
        const val OTHER_SCREEN = "apps/other"
        const val FIRST_GROUP = 101
        const val SECOND_GROUP = 202
    }
}
