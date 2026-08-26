package com.sza.fastmediasorter.wear.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The module has no instrumented tests, so the action rule is pinned here rather than in the
 * composable: it is the one part of the state block that must hold identically on ten screens.
 */
class WearStateBlockActionsTest {

    @Test
    fun `error with a retry handler offers retry then back`() {
        val actions = stateActionsFor(WearStateKind.ERROR, hasRetry = true)

        assertEquals(listOf(WearStateAction.RETRY, WearStateAction.BACK), actions)
    }

    @Test
    fun `unavailable with a retry handler offers retry then back`() {
        val actions = stateActionsFor(WearStateKind.UNAVAILABLE, hasRetry = true)

        assertEquals(listOf(WearStateAction.RETRY, WearStateAction.BACK), actions)
    }

    @Test
    fun `empty offers back alone`() {
        val actions = stateActionsFor(WearStateKind.EMPTY, hasRetry = false)

        assertEquals(listOf(WearStateAction.BACK), actions)
    }

    @Test
    fun `empty refuses a retry even when a handler is supplied`() {
        val actions = stateActionsFor(WearStateKind.EMPTY, hasRetry = true)

        assertEquals(listOf(WearStateAction.BACK), actions)
    }

    @Test
    fun `error without a retry handler offers back alone`() {
        val actions = stateActionsFor(WearStateKind.ERROR, hasRetry = false)

        assertEquals(listOf(WearStateAction.BACK), actions)
    }

    @Test
    fun `back is present in every combination`() {
        val combinations = WearStateKind.entries.flatMap { kind ->
            listOf(kind to true, kind to false)
        }

        combinations.forEach { (kind, hasRetry) ->
            assertTrue(
                "$kind with hasRetry=$hasRetry dropped Back",
                stateActionsFor(kind, hasRetry).contains(WearStateAction.BACK)
            )
        }
    }
}
