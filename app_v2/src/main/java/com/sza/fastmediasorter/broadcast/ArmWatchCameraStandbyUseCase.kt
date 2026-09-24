package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/** What came of the owner turning the standby switch on. */
sealed interface WatchCameraStandby {

    /** A camera session is on air, so a request from the watch will be granted without a prompt. */
    data object Armed : WatchCameraStandby

    /** Nothing was armed, for a reason the settings row can put in words. */
    data class Refused(val refusal: WearCameraRefusal) : WatchCameraStandby
}

/**
 * S2551: the phone side of the owner's §6.8 decision - in `noLegal` the watch is answered from a
 * standing arrangement, and this is what puts that arrangement in place.
 *
 * Arming is a plain camera broadcast started while the app is visible, because the platform refuses to
 * create a camera foreground service for an invisible app and grants no exemption a Data Layer message
 * could reach (`research/05`). What the watch's request meets later is therefore a session that already
 * exists, which is the whole point of the standby shape.
 */
class ArmWatchCameraStandbyUseCase @Inject constructor(
    private val controller: BroadcastSourceController,
    private val startWatchCameraBroadcast: StartWatchCameraBroadcastUseCase,
) {

    /** True while a camera session is on air - the state `StandbyCameraSessionConsentPolicy` grants on. */
    val isArmed: Boolean get() = controller.state.value.isCameraLive()

    /**
     * The same answer as a stream, so a switch can show what is true rather than what was asked for.
     *
     * The session outlives the screen that armed it and can end without anyone touching the switch - a
     * failed capture, a stop from the watch, a reboot - so a row rendered from the stored intent alone
     * would go on claiming the watch may look long after there was nothing to look at.
     */
    val armed: Flow<Boolean> = controller.state.map { it.isCameraLive() }

    suspend fun arm(): WatchCameraStandby = whenArmed()

    private suspend fun whenArmed(): WatchCameraStandby = when {
        // A build whose manifest declares no camera foreground-service type loses the camera the moment
        // the app leaves the screen, so arming it would promise a session that dies in the owner's
        // pocket. Only `noLegal` carries that type (src/broadcastVideoBackground).
        !controller.cameraSurvivesBackground ->
            WatchCameraStandby.Refused(WearCameraRefusal.NOT_SUPPORTED)

        else -> when (val outcome = startWatchCameraBroadcast()) {
            is WatchCameraBroadcast.Serving -> WatchCameraStandby.Armed
            is WatchCameraBroadcast.Refused -> WatchCameraStandby.Refused(outcome.refusal)
        }
    }.also {
    }

    /**
     * Ends the standing session, and only it.
     *
     * An audio broadcast the owner started for its own listeners is left alone: withdrawing the watch's
     * permission is not an instruction to stop broadcasting to the room.
     */
    fun disarm() {
        if (isArmed) controller.stop()
    }
}

/**
 * Whether the live session carries a camera, rather than merely being live.
 *
 * The state is shared with the audio broadcast, so "something is on air" is not the question the
 * standby switch asks - an audio session offers the watch no picture at all.
 */
internal fun BroadcastState.isCameraLive(): Boolean =
    this is BroadcastState.Live && descriptor.mode != BroadcastMode.AUDIO_ONLY.name
