package com.sza.fastmediasorter.ui.browse.helpers

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
 * S0962 (VR Cinema, Столп 1) - cold launch of the immersive VR player straight from a Browse
 * video-file overflow entry, with no open [com.sza.fastmediasorter.ui.player.PlayerActivity].
 *
 * Reuses the shared launch transport ([StartVrPlaybackUseCase]) via the previously-unused
 * [VrLaunchPoint.BROWSE_TILE] entry point. Visibility is driven by [XrDetectionFacade]: on non-VR
 * flavors the No-Op facade emits [XrDetectionState.NONE], so [isAvailable] stays false and the
 * caller never surfaces the menu item - no `src/main` flavor guards needed.
 *
 * Activity-scoped: the injected [ActivityContext] is the host Activity (a [LifecycleOwner]), so the
 * availability mirror and the launch coroutine both ride the Activity lifecycle without the caller
 * threading an owner through.
 */
@ActivityScoped
class BrowseVrCinemaLaunchManager @Inject constructor(
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
     * availability and URI locality; a non-local (network/cloud) URI resolves to Unavailable and
     * surfaces a short toast rather than opening the flat player.
     */
    fun launch(file: MediaFile) {
        val owner = context as? LifecycleOwner ?: return
        Timber.d("S0962: VR cinema cold-launch requested type=%s local=%s", file.type, file.isLocalPath())

        owner.lifecycleScope.launch {
            val request = StartVrPlaybackRequest(
                launchMode = VrLaunchMode.FILE_URI,
                fileUriString = file.toLaunchUriString(),
                mediaType = VrMediaType.VIDEO,
                source = VrLaunchPoint.BROWSE_TILE,
                snapshot = null,
            )
            when (val result = startVrPlaybackUseCase(request, returnTarget = null)) {
                StartVrPlaybackUseCase.DispatchResult.Started -> Unit
                is StartVrPlaybackUseCase.DispatchResult.Unavailable -> {
                    Timber.i("VR cinema launch unavailable reason=%s", result.reason)
                    toastUnavailable()
                }
                is StartVrPlaybackUseCase.DispatchResult.Failed -> {
                    Timber.w("VR cinema launch failed msg=%s", result.message)
                    toastUnavailable()
                }
            }
        }
    }

    private fun toastUnavailable() {
        Toast.makeText(context, R.string.vr_cinema_launch_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun MediaFile.isLocalPath(): Boolean = path.startsWith("/") || path.startsWith("file://")
}
