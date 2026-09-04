package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2494: the seed is the only source of randomness in the game, so "two boards in a row differ" is
 * an assertion about this class alone - and the hard case is the one where the clock cannot help,
 * a restart landing inside the same tick as the board it replaces.
 */
class GameSeedSourceTest {

    private val source = GameSeedSource().apply { clock = { PINNED_CLOCK } }

    @Test
    fun `three seeds for one level differ under a stopped clock`() {
        val seeds = listOf(source.nextSeed(1), source.nextSeed(1), source.nextSeed(1))

        assertEquals(seeds.size, seeds.toSet().size)
    }

    @Test
    fun `seeds for different levels differ`() {
        assertNotEquals(source.nextSeed(1), source.nextSeed(2))
    }

    private companion object {
        const val PINNED_CLOCK = 1_000L
    }
}
