package com.sza.fastmediasorter.broadcast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * What the phone can offer a watch that asked to see its camera.
 *
 * A closed pair rather than a nullable address: the watch draws a different screen for each, and the
 * refusal must carry a reason it can put in words - an empty address is how the wire says "refused
 * for a reason this build has no name for", which is the one answer nothing here should ever send.
 */
sealed interface WatchCameraBroadcast {

    /**
     * [startedNow] is false when the broadcast was already running - a `noLegal` standby session, or
     * one the owner armed himself. The stop command reads it so a session this watch never started is
     * not torn down under its owner.
     */
    data class Serving(
        val url: String,
        val lenses: BroadcastCameraLenses,
        val startedNow: Boolean
    ) : WatchCameraBroadcast

    data class Refused(val refusal: WearCameraRefusal) : WatchCameraBroadcast
}

/**
 * S3117: brings this phone's video broadcast up for a watch that asked, and names the address.
 *
 * Lives in `src/main` beside the lens enumeration rather than in `wearGms` with the listener that
 * calls it, for two reasons: `wearGms` is mounted by directory into the shipping variants only and is
 * in no unit-test source set at all, so a decision written there could not be judged without a phone
 * in hand; and the broadcast layer is what this asks questions of.
 *
 * Consent is decided before this runs - `CameraSessionConsentPolicy` owns that, per flavor. By the
 * time this is reached the owner has either agreed or the session is already live, so the only
 * question left is whether the capture half can actually serve.
 */
class StartWatchCameraBroadcastUseCase @Inject constructor(
    private val controller: BroadcastSourceController,
    private val listCameraLenses: ListBroadcastCameraLensesUseCase,
    @param:ApplicationContext private val context: Context
) {

    suspend operator fun invoke(): WatchCameraBroadcast = when {
        // A build with the broadcast layer disabled answers honestly instead of starting a wait that
        // nothing would ever end.
        !controller.isAvailable -> WatchCameraBroadcast.Refused(WearCameraRefusal.NOT_SUPPORTED)
        // S2551: a live AUDIO_ONLY session is not an answer to a request for a picture - taking it for
        // one served the watch the audio stream's address and left it on a player with nothing to show.
        else -> (controller.state.value as? BroadcastState.Live)
            ?.takeIf { it.isCameraLive() }
            ?.let { serving(it, startedNow = false) }
            ?: startAndAwait()
    }

    private suspend fun startAndAwait(): WatchCameraBroadcast {
        Timber.d("S2551: no camera session live, starting one for the watch")
        // A failure left behind by an earlier session is a terminal state the wait below would read as
        // this session's own outcome.
        controller.acknowledgeFailure()
        controller.start(modeForGrantedPermissions())
        // The wait ends on a camera session or on a failure, never on "not idle": an audio broadcast
        // already running makes the state non-idle from the first emission, and a session started here
        // would be reported before its camera ever opened.
        val settled = withTimeoutOrNull(START_TIMEOUT_MILLIS) {
            controller.state.first { it.isCameraLive() || it is BroadcastState.Failed }
        }
        return when {
            settled is BroadcastState.Live -> serving(settled, startedNow = true)
            settled is BroadcastState.Failed -> WatchCameraBroadcast.Refused(settled.failure.asRefusal())
            // Nothing settled inside the budget: the watch is told the capture failed rather than
            // being left on a spinner until its own timeout.
            else -> WatchCameraBroadcast.Refused(WearCameraRefusal.CAPTURE_FAILED)
        }
    }

    /**
     * The live lens comes from the session, the list from the enumeration.
     *
     * Only the capture service knows which camera it actually opened, while only the enumeration
     * knows the whole set the phone offers - taking both from one source would either shorten the
     * list to what is live or name an active lens the session is not on.
     */
    private suspend fun serving(live: BroadcastState.Live, startedNow: Boolean): WatchCameraBroadcast {
        val enumerated = listCameraLenses()
        return WatchCameraBroadcast.Serving(
            url = live.descriptor.url,
            lenses = BroadcastCameraLenses(
                lenses = enumerated.lenses,
                activeLensId = live.activeLensId ?: enumerated.activeLensId
            ),
            startedNow = startedNow
        )
    }

    /**
     * A phone with no microphone grant still serves, silently, instead of failing the whole request:
     * the capture service refuses `VIDEO_AUDIO` outright when `RECORD_AUDIO` is missing, and nothing
     * here can ask for a permission from a background listener.
     */
    private fun modeForGrantedPermissions(): BroadcastMode {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return if (micGranted) BroadcastMode.VIDEO_AUDIO else BroadcastMode.VIDEO_ONLY
    }

    private companion object {

        /** Long enough for the camera and the encoder to open on a cold start, short enough to answer. */
        const val START_TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * The broadcast layer's failure in the wire's own vocabulary.
 *
 * A missing microphone or camera grant lands on `CAPTURE_FAILED` rather than on a reason of its own:
 * the wire names no permission refusal, and inventing one here would be a contract change the watch
 * of an older build could not read.
 */
private fun BroadcastFailure.asRefusal(): WearCameraRefusal = when (this) {
    BroadcastFailure.NETWORK_UNAVAILABLE -> WearCameraRefusal.NO_NETWORK
    BroadcastFailure.MICROPHONE_PERMISSION,
    BroadcastFailure.ENCODER_UNAVAILABLE,
    BroadcastFailure.CAPTURE_ERROR -> WearCameraRefusal.CAPTURE_FAILED
}
