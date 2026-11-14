package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.wear.util.GridColumnFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4628: the calculator keypad is the one watch surface drawn below the ordinary touch floor, by owner
 * ruling S2007. UI-SIZING section 2 rule 4 asks that such a floor is declared and tested, so the numbers
 * written in `docs/ui/WEAR_SIZE_PROFILE.md` are pinned here: a later edit to either constant has to move
 * the record with it instead of drifting silently.
 */
class CalculatorKeyFloorTest {

    @Test
    fun `key height is the declared compact floor`() {
        assertEquals(DECLARED_KEY_HEIGHT, KEY_HEIGHT)
    }

    @Test
    fun `neighbouring keys stay separated by the declared gap`() {
        assertEquals(DECLARED_KEY_GAP, KEY_GAP)
        assertTrue(KEY_GAP > 0.dp)
    }

    @Test
    fun `the floor is below the ordinary target so the record must name it`() {
        assertTrue(KEY_HEIGHT < GridColumnFit.DEFAULT_MIN_TARGET_DP.dp)
    }

    private companion object {
        val DECLARED_KEY_HEIGHT = 25.dp
        val DECLARED_KEY_GAP = 2.dp
    }
}
