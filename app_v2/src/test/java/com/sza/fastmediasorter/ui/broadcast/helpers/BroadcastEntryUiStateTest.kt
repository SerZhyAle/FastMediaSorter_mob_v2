package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastFailure
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2818: proves the two machine-checkable entry-screen behaviors - a controller without broadcast
 * support ends in Unavailable (no dead end in disabled flavors, strategic criterion 4) and a live
 * session renders the stop state, never a second start (ADR-2).
 */
class BroadcastEntryUiStateTest {

    @Test
    fun `unavailable controller maps every state to Unavailable`() {
        val states = listOf(
            BroadcastState.Idle,
            liveState(),
            failedState(),
        )
        states.forEach { state ->
            assertEquals(
                BroadcastEntryUi.UiState.Unavailable,
                BroadcastEntryUi.mapState(state, isAvailable = false),
            )
        }
    }

    @Test
    fun `idle with available controller renders Confirm`() {
        assertEquals(
            BroadcastEntryUi.UiState.Confirm,
            BroadcastEntryUi.mapState(BroadcastState.Idle, isAvailable = true),
        )
    }

    @Test
    fun `live session renders Live`() {
        assertEquals(
            BroadcastEntryUi.UiState.Live,
            BroadcastEntryUi.mapState(liveState(), isAvailable = true),
        )
    }

    @Test
    fun `failed session falls back to Confirm layout`() {
        assertEquals(
            BroadcastEntryUi.UiState.Confirm,
            BroadcastEntryUi.mapState(failedState(), isAvailable = true),
        )
    }

    private fun liveState() = BroadcastState.Live(
        descriptor = BroadcastDescriptorDto(url = "http://192.168.1.5:8080/stream", mode = "AUDIO_ONLY"),
        startedAtElapsedRealtimeMs = 1_000L,
    )

    private fun failedState() = BroadcastState.Failed(
        failure = BroadcastFailure.NETWORK_UNAVAILABLE,
        detail = "port busy",
    )
}
