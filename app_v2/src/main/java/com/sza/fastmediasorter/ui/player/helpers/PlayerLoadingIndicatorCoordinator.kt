package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.view.View
import androidx.core.view.isVisible
import timber.log.Timber

/**
 * Single visibility owner of the unified player's `R.id.progressBar` spinner (S0704).
 *
 * Before this class, the unified player's progress bar was written by ~11 independent sites - the
 * reactive `viewModel.loading` driver, the image `Handler` cycle, and Glide / ExoPlayer / OCR /
 * translation / PDF callbacks - with no contract about precedence. The final visibility therefore
 * depended on event-delivery order, which produced a stuck or flickering spinner on fast navigation
 * and on media transitions.
 *
 * Model: source-counted. The bar is visible iff at least one [LoadingSource] is active. Every former
 * direct writer now only adds or removes its own source; nobody overwrites another's view state.
 * All scheduling (the former 1s show-delay runnable and 30s safety runnable) lives here, keyed per
 * source, so a transition can cancel exactly its own pending work via [reset] without disturbing
 * legitimate sources owned by other flows.
 *
 * Main-thread only: every entry point runs on the main looper, like the [View] it owns, so the
 * source set and the pending-runnable maps need no synchronization.
 */
class PlayerLoadingIndicatorCoordinator(
    private val progressBar: View,
    private val handler: Handler,
    private val isDestroyed: () -> Boolean,
) {
    private val activeSources = mutableSetOf<LoadingSource>()
    private val pendingShow = mutableMapOf<LoadingSource, Runnable>()
    private val pendingSafety = mutableMapOf<LoadingSource, Runnable>()

    /** Mark [source] active immediately (cancelling any pending delayed show for it) and sync. */
    fun show(source: LoadingSource) {
        cancelPendingShow(source)
        activeSources.add(source)
        sync()
    }

    /** Cancel any pending delayed show for [source], then schedule a fresh one after [delayMs]. */
    fun showDelayed(source: LoadingSource, delayMs: Long = DEFAULT_SHOW_DELAY_MS) {
        cancelPendingShow(source)
        val runnable = Runnable {
            pendingShow.remove(source)
            activeSources.add(source)
            sync()
        }
        pendingShow[source] = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /**
     * Arm a safety timeout that hides [source] after [timeoutMs] if nothing cleared it first.
     * Replaces the legacy 30s `hideLoadingSafetyRunnable`. Only sources with no deterministic
     * completion signal (e.g. [LoadingSource.IMAGE_GLIDE]) arm this; operations with their own
     * `finally` (OCR / translation / PDF export) do not. [onTimeout] preserves any user-facing
     * feedback the legacy runnable emitted (e.g. the image load-timeout toast); it runs after the
     * hide and only if the timeout actually fires.
     */
    fun armSafetyTimeout(
        source: LoadingSource,
        timeoutMs: Long = DEFAULT_SAFETY_TIMEOUT_MS,
        onTimeout: (() -> Unit)? = null,
    ) {
        cancelPendingSafety(source)
        val runnable = Runnable {
            pendingSafety.remove(source)
            Timber.w("PlayerLoadingIndicatorCoordinator: safety timeout fired for $source - hiding spinner")
            hide(source)
            onTimeout?.invoke()
        }
        pendingSafety[source] = runnable
        handler.postDelayed(runnable, timeoutMs)
    }

    /** Mark [source] inactive (cancelling its pending safety timeout) and sync. */
    fun hide(source: LoadingSource) {
        cancelPendingSafety(source)
        activeSources.remove(source)
        sync()
    }

    /**
     * Drop [source] and cancel its pending show + safety, leaving other sources untouched. Use on
     * transition entry (e.g. starting to display a new image, or switching image -> video) so a
     * stale delayed show scheduled for the previous item cannot resurrect the spinner over the new
     * content. Distinct from [clearAll], which is the destroy-time sledgehammer.
     */
    fun reset(source: LoadingSource) {
        cancelPendingShow(source)
        cancelPendingSafety(source)
        activeSources.remove(source)
        sync()
    }

    /** Drop every source and cancel all pending work. Use on destroy. */
    fun clearAll() {
        pendingShow.values.forEach { handler.removeCallbacks(it) }
        pendingSafety.values.forEach { handler.removeCallbacks(it) }
        pendingShow.clear()
        pendingSafety.clear()
        activeSources.clear()
        sync()
    }

    private fun cancelPendingShow(source: LoadingSource) {
        pendingShow.remove(source)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelPendingSafety(source: LoadingSource) {
        pendingSafety.remove(source)?.let { handler.removeCallbacks(it) }
    }

    private fun sync() {
        // Attach + destroy guard: never touch a view whose window is gone (post-destroy) or not yet
        // present. isDestroyed() mirrors the activity's isDestroyed || isFinishing.
        if (!isDestroyed() && progressBar.isAttachedToWindow) {
            progressBar.isVisible = activeSources.isNotEmpty()
        }
    }

    companion object {
        const val DEFAULT_SHOW_DELAY_MS = 1000L
        const val DEFAULT_SAFETY_TIMEOUT_MS = 30_000L
    }
}

/**
 * One value per family of progress-bar writer sites in the unified player (S0704). A source is the
 * unit of counting in [PlayerLoadingIndicatorCoordinator]: the spinner is shown while any source is
 * active. Grouping by family (rather than per call site) keeps the contract small while still
 * letting independent flows show/hide without stepping on each other.
 */
enum class LoadingSource {
    /** Reactive `viewModel.loading` file-list fetch (all media types except PDF/EPUB). */
    FILE_LIST,

    /** Glide image / GIF load cycle (delayed show + safety timeout). */
    IMAGE_GLIDE,

    /** In-app ExoPlayer video buffering / readiness. Also covers in-app audio buffering. */
    VIDEO_EXOPLAYER,

    /** Reserved for in-app audio buffering should it ever be tracked separately from video. */
    AUDIO_EXOPLAYER,

    /** Persistent audio playback service bind. */
    AUDIO_SERVICE,

    /** PDF page export to JPG. */
    PDF_EXPORT,

    /** EPUB parse + first-chapter render. */
    EPUB_LOAD,

    /** Text file load. */
    TEXT_LOAD,

    /** Text editor save / upload. */
    TEXT_SAVE,

    /** OCR text extraction (image or PDF). */
    OCR,

    /** Image translation pipeline. */
    TRANSLATION,
}
