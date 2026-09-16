package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import com.sza.fastmediasorter.broadcast.BroadcastState

/**
 * What the pre-stream half of the broadcast screen shows.
 *
 * This was `BroadcastEntryManager`, the driver of the separate entry screen. S3060 merged that screen
 * into [BroadcastControlManager] and the driver lost its last caller; the state mapping and the lens
 * view model it carried are still the pre-stream rendering contract, so they stay and the class body
 * does not.
 */
object BroadcastEntryUi {

    enum class UiState {
        Unavailable,
        Confirm,
        Live,
    }

    data class LensUi(
        val options: List<BroadcastLensOption>,
        val selectedLensId: String?,
        val visible: Boolean,
    )

    /**
     * Idle and Failed both render the confirm layout: a failed session already reported itself
     * through the snackbars and returns to idle on the next start attempt.
     */
    fun mapState(state: BroadcastState, isAvailable: Boolean): UiState = when {
        !isAvailable -> UiState.Unavailable
        state is BroadcastState.Live -> UiState.Live
        else -> UiState.Confirm
    }
}
