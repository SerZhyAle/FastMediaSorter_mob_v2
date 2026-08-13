package com.sza.fastmediasorter.ui.player.helpers

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
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionState
import com.sza.fastmediasorter.core.xr.toLaunchUriString
import com.sza.fastmediasorter.domain.model.MediaFile
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1114 - cold-launch of the immersive VR player from the transport controls row of the standalone
 * photo/video host (which, unlike the main player, has no VR badge/launch pipeline of its own).
 *
 * Mirrors [com.sza.fastmediasorter.ui.browse.helpers.BrowseVrCinemaLaunchManager] and
 * [com.sza.fastmediasorter.ui.main.helpers.ResourceVrCinemaLaunchManager]: reuses the shared launch
 * transport ([StartVrPlaybackUseCase]) with no return target, and gates availability on the runtime
 * [XrDetectionFacade] seam - so on non-VR flavors the No-Op facade emits [XrDetectionState.NONE],
 * [isAvailable] stays false, and the button never surfaces (no `src/main` flavor guard).
 */
@ActivityScoped
class StandaloneVrCinemaLaunchManager @Inject constructor(
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
     * Cold-launch the immersive player on [file] (video only). The use-case preflights XR
     * availability and URI locality; a non-local URI resolves to Unavailable and shows a short toast.
     */
    fun launch(file: MediaFile) {
        val owner = context as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            val request = StartVrPlaybackRequest(
                launchMode = VrLaunchMode.FILE_URI,
                fileUriString = file.toLaunchUriString(),
                mediaType = VrMediaType.VIDEO,
                source = VrLaunchPoint.CONTROLS_ROW,
                snapshot = null,
            )
            when (val result = startVrPlaybackUseCase(request, returnTarget = null)) {
                StartVrPlaybackUseCase.DispatchResult.Started -> Unit
                is StartVrPlaybackUseCase.DispatchResult.Unavailable -> {
                    Timber.i("Standalone VR launch unavailable reason=%s", result.reason)
                    toastUnavailable()
                }
                is StartVrPlaybackUseCase.DispatchResult.Failed -> {
                    Timber.w("Standalone VR launch failed msg=%s", result.message)
                    toastUnavailable()
                }
            }
        }
    }

    private fun toastUnavailable() {
        Toast.makeText(context, R.string.vr_cinema_launch_unavailable, Toast.LENGTH_SHORT).show()
    }
}
