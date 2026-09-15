package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.broadcast.BroadcastLensChoice
import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.ui.main.helpers.MainBroadcastManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.launch

/**
 * Drives the broadcast entry confirmation screen - the one screen every S2818 entry surface opens.
 * Session concerns (permission loop, failure snackbars, live indicator, share auto-open) stay in
 * [MainBroadcastManager], handed in pre-built from [com.sza.fastmediasorter.ui.main.helpers.MainHelperFactory]
 * so no domain dependency reaches the host activity.
 *
 * The lens is chosen here, before the start, because the camera modes open a camera the moment the
 * session goes live (S3050 owner requirement: every lens, before the start and on air).
 */
class BroadcastEntryManager(
    private val broadcastManager: MainBroadcastManager,
    private val controller: BroadcastSourceController,
    private val listLenses: suspend () -> BroadcastLensChoice,
    private val finish: () -> Unit,
    private val render: (UiState) -> Unit,
    private val renderLenses: (LensUi) -> Unit,
) {

    private var mode = BroadcastMode.AUDIO_ONLY
    private var lensChoice = BroadcastLensChoice.EMPTY
    private var selectedLensId: String? = null

    fun bind(owner: LifecycleOwner) {
        if (!controller.isAvailable) {
            render(UiState.Unavailable)
            finish()
            return
        }
        broadcastManager.bind(owner)
        owner.collectOnLifecycle(controller.state) { state ->
            render(mapState(state, isAvailable = true))
        }
        owner.lifecycleScope.launch {
            lensChoice = listLenses()
            selectedLensId = selectedLensId ?: lensChoice.initialLensId
            publishLenses()
        }
    }

    fun onPermissionResult(permission: String, granted: Boolean) {
        broadcastManager.onPermissionResult(permission, granted)
    }

    fun onModeSelected(newMode: BroadcastMode) {
        mode = newMode
        publishLenses()
    }

    fun onLensSelected(lensId: String) {
        selectedLensId = lensId
    }

    fun start(selectedMode: BroadcastMode = mode) {
        mode = selectedMode
        val lensId = selectedLensId.takeIf { selectedMode != BroadcastMode.AUDIO_ONLY }
        broadcastManager.startBroadcast(selectedMode, lensId)
    }

    fun stop() {
        broadcastManager.stopBroadcast()
    }

    /** Leaving via cancel must not touch the session - nothing has started yet (strategic criterion 6). */
    fun cancel() {
        finish()
    }

    private fun publishLenses() {
        renderLenses(
            LensUi(
                options = lensChoice.options,
                selectedLensId = selectedLensId,
                visible = mode != BroadcastMode.AUDIO_ONLY && lensChoice.options.isNotEmpty(),
            )
        )
    }

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

    companion object {
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
}
