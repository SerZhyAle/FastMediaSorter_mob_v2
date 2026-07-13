package com.sza.fastmediasorter.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// S0993: the CONTRAST skin must be a first-class mode and survive round-tripping through storage.
class GameModeTest {

    @Test
    fun `contrast is a registered mode`() {
        assertTrue(GameMode.entries.contains(GameMode.CONTRAST))
    }

    @Test
    fun `contrast round-trips through storage name`() {
        assertEquals(GameMode.CONTRAST, GameMode.fromStorageName("CONTRAST"))
    }

    @Test
    fun `unknown storage name falls back to classic`() {
        assertEquals(GameMode.CLASSIC, GameMode.fromStorageName("nope"))
    }
}
