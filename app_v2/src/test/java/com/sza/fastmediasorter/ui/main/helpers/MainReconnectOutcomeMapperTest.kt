package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.ReconnectResourceUseCase
import com.sza.fastmediasorter.ui.main.MainEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2374: the reconnect outcome message. The partial case is the one that mattered - it used to be
 * indistinguishable from a complete move.
 */
class MainReconnectOutcomeMapperTest {

    @Test
    fun `complete move reports success without counts`() {
        val event = MainReconnectOutcomeMapper.toEvent(
            Result.success(ReconnectResourceUseCase.Outcome(remappedFavorites = 40, keptFavorites = 0)),
        ) as MainEvent.ShowResourceMessage

        assertEquals(R.string.reconnect_success, event.resId)
        assertEquals(0, event.args.size)
    }

    @Test
    fun `partial move names moved and in-play counts`() {
        val event = MainReconnectOutcomeMapper.toEvent(
            Result.success(ReconnectResourceUseCase.Outcome(remappedFavorites = 12, keptFavorites = 28)),
        ) as MainEvent.ShowResourceMessage

        assertEquals(R.string.reconnect_success_partial_favorites, event.resId)
        assertArrayEquals(arrayOf<Any>(12, 40), event.args)
    }

    /** The case the old single message hid completely: nothing moved, and it read like a full success. */
    @Test
    fun `nothing moved is not reported as a complete move`() {
        val event = MainReconnectOutcomeMapper.toEvent(
            Result.success(ReconnectResourceUseCase.Outcome(remappedFavorites = 0, keptFavorites = 40)),
        ) as MainEvent.ShowResourceMessage

        assertEquals(R.string.reconnect_success_partial_favorites, event.resId)
        assertArrayEquals(arrayOf<Any>(0, 40), event.args)
    }

    @Test
    fun `failure reports the failure message`() {
        val event = MainReconnectOutcomeMapper.toEvent(
            Result.failure(IllegalStateException("boom")),
        ) as MainEvent.ShowResourceMessage

        assertEquals(R.string.reconnect_failed, event.resId)
    }
}
