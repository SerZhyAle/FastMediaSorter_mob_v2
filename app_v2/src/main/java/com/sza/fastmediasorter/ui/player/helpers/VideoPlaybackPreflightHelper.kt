package com.sza.fastmediasorter.ui.player.helpers

import android.os.Debug
import android.widget.Toast
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.VideoPlaybackFailureSessionCache
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryScenario
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Extracted from VideoPlayerManager.kt as part of S0274 Wave 01 decomposition.
 *
 * Owns the per-file pre-flight pipeline that runs synchronously at the top of
 * [VideoPlayerManager.playVideo] before the coroutine dispatch to a protocol handler:
 *  - reset of per-file UI state (panel stereo single-eye notifier, TextureView crop matrix),
 *  - scenario tag derivation (`audio` vs `video`),
 *  - memory profile entry + audio-scenario Glide memory cache eviction,
 *  - PRE_PLAY MemoryProbe checkpoint,
 *  - per-file mutable state reset (`videoSizeKnown`, `pendingEffectsApply`, `currentFilePath`,
 *    `posterExtractor`, `lastCompletedPath`, `audioUnsupportedShownForPath`),
 *  - one-shot warning toast for previously-failed sources,
 *  - position-saving loop teardown,
 *  - periodic ExoPlayer recreation (per-N-tracks or native-heap-low),
 *  - low-native-heap Glide eviction + GC pre-flight (S0168 §5.3).
 *
 * Returns the `scenarioTag` (currently `"audio"` or `"video"`) so the calling coroutine
 * can pass it to subsequent [com.sza.fastmediasorter.core.memory.MemoryProbe.record] calls
 * without re-running the classifier.
 */
internal class VideoPlaybackPreflightHelper(
    private val manager: VideoPlayerManager,
) {

    companion object {
        // ExoPlayer reuse across track changes accumulates native codec/decoder allocations.
        // Force a full recreation every N tracks or when native heap free falls below the threshold.
        private const val PLAYER_RECREATE_INTERVAL = 4
        private const val NATIVE_HEAP_RECREATE_THRESHOLD_BYTES = 15L * 1024 * 1024

        // S0168 §5.3: VP9/other codec decoders allocate 20-30 MB native at init.
        // 30 MB free is the minimum safe margin before we attempt Glide eviction + GC.
        private const val NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES = 30L * 1024 * 1024
    }

    /**
     * @return scenario tag for downstream `MemoryProbe.record` calls (`"audio"` or `"video"`).
     */
    fun runPreflight(path: String, resourceType: ResourceType): String {
        // Reset per-file stereo detection before every new video.
        manager.playerCallback.onBeforeVideoLoad(path)
        // Reset the panel single-eye toast one-shot for the new media session.
        manager.panelStereoSingleEyeNotifier.resetForNewSession()
        // S0264: reset TextureView crop matrix so a leftover transform from the previous
        // SBS/OU file doesn't corrupt the first frames of a new MONO file.
        PanelStereoCropApplier.reset(manager.currentPlayerView)

        val scenarioTag = manager.scenarioTagFor(path)
        val playbackScenario = if (scenarioTag == "audio") {
            MemoryScenario.AUDIO_PLAYBACK
        } else {
            MemoryScenario.VIDEO_PLAYBACK
        }
        manager.memoryProfileCoordinator.enter(playbackScenario)
        if (playbackScenario == MemoryScenario.AUDIO_PLAYBACK) {
            // Audio playback should not inherit browse thumbnails as warm image memory.
            Glide.get(manager.context).clearMemory()
        }
        // S0207 Phase 01: PRE_PLAY checkpoint - captures native heap right before ExoPlayer starts.
        manager.memoryProbe.record(MemoryCheckpoint.PRE_PLAY, scenarioTag = scenarioTag)

        // Reset effects-deferral gate for the new file - size will be reported
        // again via onVideoSizeChanged once the decoder decodes the first frame.
        manager.videoSizeKnown = false
        manager.pendingEffectsApply = false

        manager.currentFilePath = path
        if (VideoPlaybackFailureSessionCache.hasFailure(path)) {
            // WHY: a previous timeout would otherwise look like a random skip again on retry.
            Toast.makeText(
                manager.context,
                manager.context.getString(R.string.video_playback_failed_session_warning),
                Toast.LENGTH_SHORT,
            ).show()
        }
        manager.posterExtractor.reset()
        manager.lastCompletedPath = null
        manager.audioUnsupportedShownForPath = null
        manager.stopPositionSaving()

        // Recreate ExoPlayer periodically to release accumulated native codec/decoder allocations.
        if (manager.exoPlayer != null) {
            manager.trackChangesSinceRecreate++
            val nativeFree = Debug.getNativeHeapFreeSize()
            if (manager.trackChangesSinceRecreate >= PLAYER_RECREATE_INTERVAL || nativeFree < NATIVE_HEAP_RECREATE_THRESHOLD_BYTES) {
                Timber.i("VideoPlayerManager: recreating ExoPlayer - trackChanges=${manager.trackChangesSinceRecreate}, nativeFree=${nativeFree / 1024 / 1024}MB")
                manager.releasePlayer()
                manager.trackChangesSinceRecreate = 0
            }
        }

        // WHY S0168 §5.3: VP9/other codec decoders allocate 20-30 MB native at init.
        // When native heap is critically low, buffer allocation stalls immediately -> errorCode=1004.
        // Run Glide eviction + GC before ExoPlayer starts to maximise available native memory.
        // S0207 Goal#2: no user-facing toast - playback proceeds; chronic toast noise removed.
        val nativeFreePrePlay = Debug.getNativeHeapFreeSize()
        if (nativeFreePrePlay < NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES) {
            Timber.w(
                "VideoPlayerManager: native heap low before playback - free=%dMB, running Glide eviction + GC (S0168 §5.3)",
                nativeFreePrePlay / 1024 / 1024,
            )
            Glide.get(manager.context).clearMemory()
            Runtime.getRuntime().gc()
            val nativeFreeAfterGc = Debug.getNativeHeapFreeSize()
            Timber.i(
                "VideoPlayerManager: native heap after GC - free=%dMB",
                nativeFreeAfterGc / 1024 / 1024,
            )
        }

        return scenarioTag
    }
}
