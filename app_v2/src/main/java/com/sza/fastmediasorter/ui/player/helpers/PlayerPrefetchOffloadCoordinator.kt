package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import com.sza.fastmediasorter.domain.model.CleanupPromptRequest
import com.sza.fastmediasorter.domain.model.OffloadOffer
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.domain.usecase.StreamOffloadUseCase
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Adaptive pre-cache + stream-offload orchestration for PlayerViewModel.
 * See `PLAN/spec_adaptive-playback-strategy.md` §5.5–§5.6.
 *
 * All physics live in [StreamOffloadUseCase] / [PrefetchProgressTracker]. This coordinator
 * exposes the flows the UI observes, wires progress pipelines from the active tracker, and
 * runs the offload job lifecycle. The `currentLocalCopyEntry` remembers the swap so
 * [requestCleanupIfNeeded] can prompt/delete/retain on exit or when switching files.
 */
class PlayerPrefetchOffloadCoordinator(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val streamOffloadUseCase: StreamOffloadUseCase,
    private val streamingCacheRepository: StreamingCacheRepository,
    private val stateFlow: StateFlow<PlayerViewModel.PlayerState>,
    private val updateState: ((PlayerViewModel.PlayerState) -> PlayerViewModel.PlayerState) -> Unit
) {

    /** Exposed so PlayerPrefetchManager can gate overlay + offload offer without a separate DI injection. */
    val prefetchCacheMultiplier: StateFlow<PrefetchCacheMultiplier> =
        settingsRepository.getSettings()
            .map { it.prefetchCacheMultiplier }
            .stateIn(scope, SharingStarted.Eagerly, PrefetchCacheMultiplier.DEFAULT)

    private val _prefetchPlan = MutableStateFlow<PrefetchPlan?>(null)
    val prefetchPlan: StateFlow<PrefetchPlan?> = _prefetchPlan.asStateFlow()

    private val _prefetchProgress = MutableStateFlow(PrefetchProgress.EMPTY)
    val prefetchProgress: StateFlow<PrefetchProgress> = _prefetchProgress.asStateFlow()

    // Pipes the active tracker's StateFlow into ours; cancelled on rebind.
    private var prefetchProgressBridge: Job? = null

    private val _offloadOffer = MutableSharedFlow<OffloadOffer>(extraBufferCapacity = 1)
    val offloadOffer: SharedFlow<OffloadOffer> = _offloadOffer.asSharedFlow()

    private val _offloadProgress = MutableStateFlow<StreamOffloadUseCase.OffloadProgress?>(null)
    val offloadProgress: StateFlow<StreamOffloadUseCase.OffloadProgress?> = _offloadProgress.asStateFlow()

    private val _cleanupPrompt = MutableSharedFlow<CleanupPromptRequest>(extraBufferCapacity = 1)
    val cleanupPrompt: SharedFlow<CleanupPromptRequest> = _cleanupPrompt.asSharedFlow()

    private var offloadJob: Job? = null

    @Volatile
    private var currentLocalCopyEntry: StreamingCacheEntry? = null

    fun updatePrefetchPlan(plan: PrefetchPlan) {
        _prefetchPlan.value = plan
        Timber.d("PlayerPrefetchOffloadCoordinator: prefetchPlan updated viability=%s target=%ds", plan.viability, plan.targetPrefetchSec)
    }

    fun bindPrefetchTracker(tracker: PrefetchProgressTracker) {
        prefetchProgressBridge?.cancel()
        _prefetchProgress.value = tracker.prefetchProgress.value
        prefetchProgressBridge = scope.launch {
            tracker.prefetchProgress.collect { _prefetchProgress.value = it }
        }
    }

    fun unbindPrefetchTracker() {
        prefetchProgressBridge?.cancel()
        prefetchProgressBridge = null
        _prefetchProgress.value = PrefetchProgress.EMPTY
    }

    fun emitOffloadOffer(offer: OffloadOffer) {
        Timber.d(
            "PlayerPrefetchOffloadCoordinator: emitOffloadOffer size=%d speed=%s enoughSpace=%s",
            offer.fileSizeBytes,
            offer.speedMbps?.toString() ?: "n/a",
            offer.hasEnoughSpace
        )
        _offloadOffer.tryEmit(offer)
    }

    fun acceptOffload(offer: OffloadOffer) {
        offloadJob?.cancel()
        offloadJob = scope.launch {
            try {
                streamOffloadUseCase.run(offer.request).collect { progress ->
                    _offloadProgress.value = progress
                    if (progress is StreamOffloadUseCase.OffloadProgress.Completed) {
                        Timber.d("PlayerPrefetchOffloadCoordinator: offload completed -> %s", progress.entry.localPath)
                        switchToLocalFile(progress.entry)
                    }
                }
            } catch (ce: CancellationException) {
                Timber.d("PlayerPrefetchOffloadCoordinator: offload job cancelled")
                throw ce
            } catch (t: Throwable) {
                Timber.e(t, "PlayerPrefetchOffloadCoordinator: offload failed unexpectedly")
                _offloadProgress.value = StreamOffloadUseCase.OffloadProgress.Failed(
                    StreamOffloadUseCase.FailureReason.DOWNLOAD_FAILED,
                    t.message
                )
            }
        }
    }

    fun declineOffload(@Suppress("UNUSED_PARAMETER") offer: OffloadOffer) {
        Timber.d("PlayerPrefetchOffloadCoordinator: offload declined — caller proceeds with MARGINAL plan")
    }

    fun cancelOffload() {
        offloadJob?.cancel()
        offloadJob = null
        _offloadProgress.value = StreamOffloadUseCase.OffloadProgress.Cancelled
        Timber.d("PlayerPrefetchOffloadCoordinator: offload cancelled by user")
    }

    /**
     * Swap current playback source to the freshly downloaded local copy. Replaces
     * the current file's path in [PlayerViewModel.PlayerState] so downstream helpers
     * re-open it as a plain local file (minimal pre-cache, no network stack).
     */
    fun switchToLocalFile(entry: StreamingCacheEntry) {
        currentLocalCopyEntry = entry
        val files = stateFlow.value.files.toMutableList()
        val idx = stateFlow.value.currentIndex
        if (idx !in files.indices) return
        val current = files[idx]
        files[idx] = current.copy(path = entry.localPath)
        updateState { it.copy(files = files) }
        scope.launch {
            runCatching { streamingCacheRepository.touchPlayed(entry.resourceHash) }
                .onFailure { Timber.w(it, "PlayerPrefetchOffloadCoordinator: touchPlayed failed") }
        }
    }

    /**
     * Called on player exit / file switch when a local copy was used. Emits
     * [CleanupPromptRequest] unless the user's mode is AUTO_DELETE (delete right
     * away) or AUTO_KEEP (silently retain).
     */
    fun requestCleanupIfNeeded() {
        val entry = currentLocalCopyEntry ?: return
        currentLocalCopyEntry = null
        scope.launch {
            val mode = runCatching { settingsRepository.getSettings().first().streamingCacheCleanupMode }
                .getOrDefault(StreamingCacheCleanupMode.DEFAULT)
            when (mode) {
                StreamingCacheCleanupMode.AUTO_DELETE -> {
                    Timber.d("PlayerPrefetchOffloadCoordinator: cleanup AUTO_DELETE -> %s", entry.resourceHash)
                    runCatching { streamingCacheRepository.delete(entry.resourceHash) }
                        .onFailure { Timber.w(it, "PlayerPrefetchOffloadCoordinator: auto-delete failed") }
                }
                StreamingCacheCleanupMode.AUTO_KEEP -> {
                    Timber.d("PlayerPrefetchOffloadCoordinator: cleanup AUTO_KEEP -> retained %s", entry.resourceHash)
                }
                StreamingCacheCleanupMode.ASK -> {
                    _cleanupPrompt.tryEmit(CleanupPromptRequest(entry))
                }
            }
        }
    }

    fun deleteLocalCopy(entry: StreamingCacheEntry) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                streamingCacheRepository.delete(entry.resourceHash)
                File(entry.localPath).delete()
            }.onFailure {
                Timber.w(it, "PlayerPrefetchOffloadCoordinator: deleteLocalCopy failed for %s", entry.resourceHash)
            }
            Timber.d("PlayerPrefetchOffloadCoordinator: deleteLocalCopy complete — %s", entry.resourceHash)
        }
    }
}
