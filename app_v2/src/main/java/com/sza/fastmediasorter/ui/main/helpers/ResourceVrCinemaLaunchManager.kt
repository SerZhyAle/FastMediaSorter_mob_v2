package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.StartVrPlaybackRequest
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.VrLaunchPoint
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionState
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.ui.dialog.ResourcePickerDialog
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S0963 (VR Cinema, Pillar 2) - opens a resource in the immersive browser from the main resource
 * (⋮) menu, reusing the shared launch transport ([StartVrPlaybackUseCase]) via
 * [StartVrPlaybackRequest.resourceBrowse]. Mirrors
 * [com.sza.fastmediasorter.ui.browse.helpers.BrowseVrCinemaLaunchManager] but scoped to the main
 * list Activity. Visibility is driven by [XrDetectionFacade]: on non-VR flavors the No-Op facade
 * emits [XrDetectionState.NONE], so [isAvailable] stays false - no `src/main` flavor guards.
 */
@ActivityScoped
class ResourceVrCinemaLaunchManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val detectionFacade: XrDetectionFacade,
    private val startVrPlaybackUseCase: StartVrPlaybackUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
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

    /** Opens [resource] in the immersive browser; the use-case preflights XR availability. */
    fun launch(resource: MediaResource) {
        val owner = context as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            val request = StartVrPlaybackRequest.resourceBrowse(
                resourceId = resource.id,
                source = VrLaunchPoint.BROWSE_TILE,
            )
            when (val result = startVrPlaybackUseCase(request, returnTarget = null)) {
                StartVrPlaybackUseCase.DispatchResult.Started -> Unit
                is StartVrPlaybackUseCase.DispatchResult.Unavailable -> {
                    Timber.i("VR cinema resource launch unavailable reason=%s", result.reason)
                    toastUnavailable()
                }
                is StartVrPlaybackUseCase.DispatchResult.Failed -> {
                    Timber.w("VR cinema resource launch failed msg=%s", result.message)
                    toastUnavailable()
                }
            }
        }
    }

    /**
     * S0962 (Pillar 1): the "VR Cinema" program carries no preselected target, so prompt for a
     * resource, then open it in the immersive browser. Reuses the canonical [ResourcePickerDialog].
     */
    fun promptResourceAndLaunch() {
        val owner = context as? LifecycleOwner ?: return
        ResourcePickerDialog(
            context = context,
            lifecycleOwner = owner,
            getResourcesUseCase = getResourcesUseCase,
            currentSelection = null,
            title = context.getString(R.string.vr_cinema_program_title),
            allowClear = false,
            onResourceSelected = { resource -> resource?.let { launch(it) } },
        ).show()
    }

    private fun toastUnavailable() {
        Toast.makeText(context, R.string.vr_cinema_launch_unavailable, Toast.LENGTH_SHORT).show()
    }
}
