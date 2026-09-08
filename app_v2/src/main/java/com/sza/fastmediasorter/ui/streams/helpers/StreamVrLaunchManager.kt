package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.StartVrPlaybackRequest
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchPoint
import com.sza.fastmediasorter.core.xr.VrLaunchSourceKind
import com.sza.fastmediasorter.core.xr.VrLaunchUnavailableReason
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionState
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1218: cold launch of the immersive player on a live video channel, straight from the channel's
 * own menu - the affordance the owner looked for on the streams screen and did not find.
 *
 * Modelled on [com.sza.fastmediasorter.ui.browse.helpers.BrowseVrCinemaLaunchManager], including its
 * gate: availability is driven by [XrDetectionFacade], whose No-Op implementation reports
 * [XrDetectionState.NONE] on every flavor without the immersive host, so no `BuildConfig` flavor
 * guard is needed in `src/main` (strategic §3.2).
 *
 * No playlist travels with the request. A live channel has no neighbouring item, so PREV/NEXT have
 * nowhere to go - strategic §6 Q2, and the same reason the immersive HUD paints its live form.
 */
@ActivityScoped
class StreamVrLaunchManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val detectionFacade: XrDetectionFacade,
    private val startVrPlaybackUseCase: StartVrPlaybackUseCase,
) {

    @Volatile
    private var latestState: XrDetectionState = XrDetectionState.NONE

    /** True only when the device is XR-capable and the user enabled the VR-3D master toggle. */
    val isAvailable: Boolean
        get() = latestState == XrDetectionState.AVAILABLE_ENABLED

    init {
        (context as? LifecycleOwner)?.let { owner ->
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    detectionFacade.state().collect { latestState = it }
                }
            }
        }
    }

    /**
     * Takes the channel's address and name rather than its row: this is a UI helper, and a Room
     * entity reaching it would put the data layer inside the UI one (gate `ui-imports-room`).
     */
    fun launch(url: String, title: String) {
        val owner = context as? LifecycleOwner ?: return

        Timber.d("S1218: streams VR entry tapped scheme=${url.substringBefore(':')}")
        owner.lifecycleScope.launch {
            val request = StartVrPlaybackRequest(
                launchMode = VrLaunchMode.FILE_URI,
                fileUriString = url,
                mediaType = VrMediaType.VIDEO,
                source = VrLaunchPoint.BROWSE_TILE,
                snapshot = null,
                sourceKind = VrLaunchSourceKind.NETWORK_STREAM,
                displayTitle = title,
            )
            when (val result = startVrPlaybackUseCase(request, returnTarget = null)) {
                StartVrPlaybackUseCase.DispatchResult.Started -> Unit
                is StartVrPlaybackUseCase.DispatchResult.Unavailable -> {
                    Timber.i("Stream VR launch unavailable reason=%s", result.reason)
                    toastUnavailable(result.reason)
                }
                is StartVrPlaybackUseCase.DispatchResult.Failed -> {
                    Timber.w("Stream VR launch failed msg=%s", result.message)
                    toastUnavailable(reason = null)
                }
            }
        }
    }

    /**
     * A transport the immersive host does not open yet says so, instead of falling back on the
     * generic "unavailable" - strategic §2 goal 3 asks a refusal to explain itself rather than read
     * as a missing feature, and RTSP is a continuation rather than a defect (§6 Q3).
     */
    private fun toastUnavailable(reason: VrLaunchUnavailableReason?) {
        val messageRes = if (reason == VrLaunchUnavailableReason.NotYetSupported) {
            R.string.stream_vr_unsupported_transport
        } else {
            R.string.vr_cinema_launch_unavailable
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
    }
}
