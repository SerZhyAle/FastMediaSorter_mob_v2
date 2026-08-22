package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1710: the watch and the phone must agree on the board for one config and one seed (ADR-1), so
 * determinism is the property under test here, not the shape of any single level.
 */
class GameBoardGeneratorTest {

    private val generator = GameBoardGenerator()

    @Test
    fun `one seed produces the same board twice`() {
        val config = GameLevelConfig(seed = 42L)

        val first = generator.createInitialState(config)
        val second = generator.createInitialState(config)

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun `different seeds are allowed to differ but stay valid`() {
        val states = (1L..8L).map { seed -> generator.createInitialState(GameLevelConfig(seed = seed)) }

        states.forEach { state ->
            assertNotNull(state)
            requireNotNull(state)
            assertTrue(state.board.isFloor(state.player.position))
            val kryvavitsa = state.enemies.filter { it.type == GameEnemyType.KRYVAVITSA }
            assertEquals(1, kryvavitsa.size)
            assertTrue(state.board.isFloor(kryvavitsa.first().position))
            assertTrue(state.board.exitPositions().isNotEmpty())
            assertTrue(state.enemies.none { it.position == state.player.position })
        }
    }

    @Test
    fun `an exhausted attempt budget returns the failure instead of throwing`() {
        val exhausted = GameBoardGenerator(maxGenerationAttempts = 0)

        assertNull(exhausted.createInitialState(GameLevelConfig(seed = 1L)))
    }
}
