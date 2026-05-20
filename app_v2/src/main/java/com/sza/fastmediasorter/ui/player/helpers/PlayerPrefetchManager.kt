package com.sza.fastmediasorter.ui.player.helpers

import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.domain.model.OffloadOffer
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import com.sza.fastmediasorter.domain.usecase.StreamOffloadUseCase
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.views.PrefetchOverlayView
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns all pre-cache / offload UI concerns inside [PlayerActivity]:
 * - mounts [PrefetchOverlayView] onto the root window decor
 * - observes [PlayerViewModel.prefetchPlan] → pushes plan to [VideoPlayerManager]
 * - observes [PlayerViewModel.prefetchProgress] → drives the overlay
 * - observes [PlayerViewModel.offloadOffer] → shows [StreamOffloadOfferDialog]
 * - observes [PlayerViewModel.offloadProgress] → drives [FileCopyProgressDialog]
 * - observes [PlayerViewModel.cleanupPrompt] → shows [StreamingCacheCleanupHelper]
 *
 * Extracted from [PlayerActivity] to keep it under the 1000-line limit.
 */
internal class PlayerPrefetchManager(private val activity: PlayerActivity) {

    private lateinit var overlayView: PrefetchOverlayView
    private var fileCopyProgressDialog: FileCopyProgressDialog? = null
    private var suppressCleanupPromptForSession = false
    // Mirrors PrefetchCacheMultiplier.LESS setting: suppresses overlay + offload offer.
    private var overlayEnabled = true

    // ── Setup ──────────────────────────────────────────────────────────────────

    fun setup() {
        mountOverlay()
        startObserving()
    }

    private fun mountOverlay() {
        overlayView = PrefetchOverlayView(activity)
        overlayView.visibility = View.GONE

        // mediaContentArea is the FrameLayout that hosts the player surface.
        // Adding there keeps the overlay correctly layered above video / inside the media area.
        val container = activity.activityBinding.mediaContentArea
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(8)
        }
        container.addView(overlayView, params)
        Timber.d("PlayerPrefetchManager: overlay mounted to mediaContentArea")
    }

    // ── Observers ──────────────────────────────────────────────────────────────

    private fun startObserving() {
        val vm = activity.viewModel

        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // prefetchCacheMultiplier → gate overlay + offload offer
                launch {
                    vm.prefetchCacheMultiplier.collect { multiplier ->
                        overlayEnabled = multiplier != PrefetchCacheMultiplier.LESS
                        overlayView.isOverlayEnabled = overlayEnabled
                        if (!overlayEnabled) overlayView.visibility = View.GONE
                    }
                }

                // prefetchPlan → push to VideoPlayerManager (affects next player build)
                launch {
                    vm.prefetchPlan.collect { plan ->
                        activity._videoPlayerManager?.setPrefetchPlan(plan)
                        if (plan != null) {
                            Timber.d(
                                "PlayerPrefetchManager: plan updated " +
                                    "viability=${plan.viability} target=${plan.targetPrefetchSec}s"
                            )
                        }
                    }
                }

                // prefetchProgress → drive overlay
                launch {
                    vm.prefetchProgress.collect { progress ->
                        overlayView.update(progress)
                        if (progress.viability == StreamViabilityState.NOT_VIABLE) {
                            overlayView.visibility = View.GONE
                        }
                    }
                }

                // offloadOffer → show dialog
                launch {
                    vm.offloadOffer.collect { offer ->
                        showOffloadOffer(offer)
                    }
                }

                // offloadProgress → drive FileCopyProgressDialog
                launch {
                    vm.offloadProgress.collect { progress ->
                        handleOffloadProgress(progress)
                    }
                }

                // cleanupPrompt → show cleanup dialog
                launch {
                    vm.cleanupPrompt.collect { request ->
                        if (!suppressCleanupPromptForSession) {
                            showCleanupPrompt(request)
                        }
                    }
                }
            }
        }
    }

    // ── Offload offer dialog ───────────────────────────────────────────────────

    private fun showOffloadOffer(offer: OffloadOffer) {
        // LESS mode: user opted for minimal UI - skip the offer entirely
        if (!overlayEnabled) return

        // Dismiss any currently-shown overlay while the dialog is visible
        overlayView.visibility = View.GONE

        val fm = activity.supportFragmentManager
        if (fm.findFragmentByTag(StreamOffloadOfferDialog.TAG) != null) return

        StreamOffloadOfferDialog.newInstance(offer)
            .show(fm, StreamOffloadOfferDialog.TAG)
        Timber.d("PlayerPrefetchManager: showing offload offer dialog")
    }

    // ── Offload download progress ──────────────────────────────────────────────

    private fun handleOffloadProgress(progress: StreamOffloadUseCase.OffloadProgress?) {
        when (progress) {
            is StreamOffloadUseCase.OffloadProgress.Starting -> {
                val filename = progress.destinationPath.substringAfterLast("/")
                fileCopyProgressDialog?.dismiss()
                fileCopyProgressDialog = FileCopyProgressDialog(
                    context = activity,
                    fileName = filename,
                    onCancelRequested = { activity.viewModel.cancelOffload() }
                ).also { it.show() }
                Timber.d("PlayerPrefetchManager: offload starting - %s", filename)
            }
            is StreamOffloadUseCase.OffloadProgress.Downloading -> {
                fileCopyProgressDialog?.updateProgress(
                    bytesCopied = progress.bytesTransferred,
                    totalBytes = progress.totalBytes,
                    speedBytesPerSec = progress.speedBytesPerSecond
                )
            }
            is StreamOffloadUseCase.OffloadProgress.Completed -> {
                fileCopyProgressDialog?.dismiss()
                fileCopyProgressDialog = null
                val sizeLabel = Formatter.formatShortFileSize(activity, progress.entry.sizeBytes)
                overlayView.showLocalCopyMode(sizeLabel)
                Timber.i("PlayerPrefetchManager: offload completed - playing from local copy")
            }
            is StreamOffloadUseCase.OffloadProgress.Failed -> {
                fileCopyProgressDialog?.dismiss()
                fileCopyProgressDialog = null
                Timber.w("PlayerPrefetchManager: offload failed - reason=%s", progress.reason)
            }
            is StreamOffloadUseCase.OffloadProgress.Cancelled -> {
                fileCopyProgressDialog?.dismiss()
                fileCopyProgressDialog = null
                Timber.d("PlayerPrefetchManager: offload cancelled")
            }
            null -> { /* initial / idle state */ }
        }
    }

    // ── Cleanup prompt ─────────────────────────────────────────────────────────

    private fun showCleanupPrompt(
        request: com.sza.fastmediasorter.domain.model.CleanupPromptRequest
    ) {
        StreamingCacheCleanupHelper.showPrompt(
            context = activity,
            request = request,
            onDelete = { entry ->
                activity.viewModel.deleteLocalCopy(entry)
            },
            onKeep = { /* no-op */ },
            onSuppressForSession = { suppressCleanupPromptForSession = true }
        )
    }

    /** Called when a new media session starts (e.g., user navigates to next file). */
    fun onNewMediaSession() {
        overlayView.reset()
    }

    /** Called when ExoPlayer reaches STATE_READY + isPlaying. */
    fun onPlaybackReady() {
        overlayView.markPlaybackReady()
    }

    // ── Utils ──────────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * activity.resources.displayMetrics.density + 0.5f).toInt()
}
