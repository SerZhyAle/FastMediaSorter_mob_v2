package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastLensChoice
import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import com.sza.fastmediasorter.broadcast.BroadcastMode
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
     * S4286: while this screen's own session holds the camera, CameraX's camera list can read empty for a
     * few seconds, and the one read made when the activity is recreated would then hide the lens choice for
     * the rest of the session. A phone's lens set does not change, so the last non-empty answer stands in.
     * Outside a session an empty answer is believed: nothing of ours explains it.
     */
    fun lensChoiceFor(
        fresh: BroadcastLensChoice,
        remembered: BroadcastLensChoice,
        holdsCamera: Boolean,
    ): BroadcastLensChoice = if (fresh.options.isEmpty() && holdsCamera) remembered else fresh

    /**
     * Idle and Failed both render the confirm layout: a failed session already reported itself
     * through the snackbars and returns to idle on the next start attempt.
     */
    fun mapState(state: BroadcastState, isAvailable: Boolean): UiState = when {
        !isAvailable -> UiState.Unavailable
        state is BroadcastState.Live -> UiState.Live
        else -> UiState.Confirm
    }

    /**
     * S3235: the settings card configures a session that has not started yet. While one runs its
     * parameters are fixed, and the card sat in the middle of the screen pushing the live controls
     * and the sharing block below the fold.
     */
    fun showsSettingsCard(state: BroadcastState): Boolean = state !is BroadcastState.Live

    /**
     * S3235: a listener count outside a live session can only say "0 listeners", which costs a row
     * of vertical space and tells the user nothing.
     */
    fun showsListenerCount(state: BroadcastState): Boolean = state is BroadcastState.Live

    /**
     * S4281: the low-light row (bright frame, and the flashlight where the lens has a flash) belongs to a
     * session with a camera of its own. A file or screen session serves a picture the phone's camera does
     * not make, and audio only has no picture to light.
     */
    fun showsLightControls(state: BroadcastState): Boolean = state is BroadcastState.Live &&
        state.source == null &&
        state.descriptor.mode != BroadcastMode.AUDIO_ONLY.name

    /**
     * S4281: a front lens usually has no flash, and a muted camera sends black frames the lamp cannot
     * improve - a flashlight button in either case would be one that lights nothing.
     */
    fun showsTorchButton(state: BroadcastState): Boolean =
        state is BroadcastState.Live && showsLightControls(state) && state.torchAvailable && state.cameraEnabled

    /**
     * S4418: the player's rule (S0667) is that landscape means fullscreen for a visual medium. On this
     * screen it applies only to a live camera picture: while idle the two-pane layout is where the mode,
     * the lens and the start button are, and a session without a picture has nothing to enlarge.
     * [controlsShown] is the user's tap on the picture asking for the panel back.
     */
    fun showsFullscreenPreview(
        isLandscape: Boolean,
        pictureOnScreen: Boolean,
        controlsShown: Boolean,
    ): Boolean = isLandscape && pictureOnScreen && !controlsShown
}
