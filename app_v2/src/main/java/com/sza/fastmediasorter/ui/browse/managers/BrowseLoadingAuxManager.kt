package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Auxiliary loading helpers for the Browse screen:
 * - Formatted loading-error dispatch ([handleLoadingError]).
 * - Background audio-metadata enrichment ([enrichAudioMetadataInBackground]).
 * - ExoPlayer warm-up heuristic ([schedulePlayerWarmupIfEligible]).
 *
 * Holds the mutable [playerWarmupJob] and [audioMetadataEnrichmentJob] so that
 * [BrowseViewModel.cancelScan] / [BrowseViewModel.onCleared] can cancel them via [cancelAll].
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseLoadingAuxManager(
    private val context: Context,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val cachedMediaMetadataExtractor: CachedMediaMetadataExtractor,
    private val cachedFileListRepository: CachedFileListRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val exceptionHandler: CoroutineContext,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val setLoading: (Boolean) -> Unit,
    private val getSettings: suspend () -> AppSettings,
    private val resourceId: Long,
    private val onHandleError: (Throwable) -> Unit
) {
    // ── Mutable jobs (owned here, cancel via cancelAll / cancelPlayerWarmup) ──

    private var playerWarmupJob: Job? = null
    private var lastWarmupSignature: String? = null
    private var audioMetadataEnrichmentJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Dispatch a structured, user-friendly error event for loading failures.
     * Updates resource availability to `false` for connectivity errors.
     */
    fun handleLoadingError(resource: MediaResource, e: Throwable) {
        // ConnectionThrottle cancellation: video player is active
        if (e is java.util.concurrent.CancellationException &&
            e.message?.contains("Video player priority", ignoreCase = true) == true
        ) {
            Timber.d("BrowseLoadingAuxManager: folder loading blocked by video player throttle")
            sendEvent(BrowseEvent.ShowError(
                message = "Cannot load folder while video is playing\n\n" +
                    "The video player has priority access to network resources.\n\n" +
                    "Please close the video player and try again.",
                details = "Resource: ${resource.name}\nPath: ${resource.path}\n\n" +
                    "This is a safety feature to prevent network congestion and ensure smooth video playback.",
                exception = e
            ))
            setLoading(false)
            return
        }

        // Cloud authentication error
        if (resource.type == ResourceType.CLOUD &&
            (e.message?.contains("Google Drive authentication required", ignoreCase = true) == true ||
                e.message?.contains("Not authenticated", ignoreCase = true) == true)
        ) {
            Timber.d("BrowseLoadingAuxManager: Cloud auth error — sending ShowCloudAuthenticationRequired")
            // Clear lastBrowseDate so this resource is not auto-reopened on next startup.
            // Prevents orphaned/unauthenticated cloud resources from showing the error dialog
            // every time the app starts (even if the user never added this resource themselves).
            scope.launch(ioDispatcher) {
                try {
                    updateResourceUseCase(resource.copy(lastBrowseDate = null))
                    Timber.d("BrowseLoadingAuxManager: cleared lastBrowseDate for cloud resource '${resource.name}' (auth error)")
                } catch (ex: Exception) {
                    Timber.e(ex, "BrowseLoadingAuxManager: failed to clear lastBrowseDate")
                }
            }
            sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(resource.cloudProvider ?: CloudProvider.GOOGLE_DRIVE))
            setLoading(false)
            return
        }

        // Mark resource unavailable on connection errors
        scope.launch(ioDispatcher) {
            try {
                val isConnErr = e.message?.contains("Connection", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    e.message?.contains("Authentication", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
                if (isConnErr && resource.isAvailable) {
                    updateResourceUseCase(resource.copy(isAvailable = false))
                    Timber.d("BrowseLoadingAuxManager: marked resource unavailable due to connection error")
                }
            } catch (ex: Exception) {
                Timber.e(ex, "BrowseLoadingAuxManager: failed to update resource availability")
            }
        }

        val title = when {
            e.message?.contains("Authentication failed", ignoreCase = true) == true ||
                e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true -> "Authentication Failed"
            e.message?.contains("Connection error", ignoreCase = true) == true ||
                e.message?.contains("Network", ignoreCase = true) == true -> "Network Connection Error"
            e.message?.contains("Permission denied", ignoreCase = true) == true -> "Permission Denied"
            else -> "Error Loading Files"
        }

        val body = when {
            e.message?.contains("Authentication failed", ignoreCase = true) == true ||
                e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true ->
                "Invalid username or password.\n\nRecommendations:\n" +
                    "• Edit this resource and update credentials\n" +
                    "• Verify username and password are correct\n" +
                    "• Check if account is locked or expired"
            e.message?.contains("Connection error", ignoreCase = true) == true ||
                e.message?.contains("Network", ignoreCase = true) == true ->
                "Cannot connect to server.\n\nRecommendations:\n" +
                    "• Check your network connection\n" +
                    "• Verify server address and port\n" +
                    "• Check if server is online and accessible\n" +
                    "• Try pinging the server from another device"
            e.message?.contains("timed out", ignoreCase = true) == true ||
                e.message?.contains("SocketTimeoutException", ignoreCase = true) == true ->
                "Connection timeout.\n\nPossible causes:\n" +
                    "• Server is slow or overloaded\n" +
                    "• Network latency is too high\n" +
                    "• Firewall blocking data connection (FTP passive mode)\n\n" +
                    "Recommendations:\n" +
                    "• Check server status and load\n" +
                    "• Configure firewall to allow FTP passive mode\n" +
                    "• Contact server administrator if problem persists"
            e.message?.contains("Permission denied", ignoreCase = true) == true ->
                "No access to this folder.\n\nRecommendations:\n" +
                    "• Check folder permissions on server\n" +
                    "• Verify your account has read access\n" +
                    "• Try accessing from root directory first"
            else -> "Failed to load media files.\n\nSee error details below for more information."
        }

        val details = buildString {
            append("Resource: ${resource.name}\n")
            append("Path: ${resource.path}\n")
            append("Type: ${resource.type}\n\n")
            append("Error: ${e.message ?: "Unknown error"}\n\n")
            append("Stack trace:\n${e.stackTraceToString()}")
        }

        sendEvent(BrowseEvent.ShowError(message = "$title\n\n$body", details = details, exception = e))
        onHandleError(e)
    }

    /**
     * Lazily enriches audio files with metadata (artist, album, title, duration) in the background.
     * Results are written to UI state, RAM cache, and DB cache.
     */
    fun enrichAudioMetadataInBackground(resource: MediaResource) {
        audioMetadataEnrichmentJob?.cancel()
        audioMetadataEnrichmentJob = scope.launch(ioDispatcher + exceptionHandler) {
            delay(300L) // let UI settle first

            val audioWithoutMeta = stateFlow.value.mediaFiles.filter { file ->
                !file.isDirectory &&
                    file.type == MediaType.AUDIO &&
                    file.artist == null && file.title == null
            }
            if (audioWithoutMeta.isEmpty()) {
                Timber.d("BrowseLoadingAuxManager.enrichAudio: nothing to enrich for resource ${resource.id}")
                return@launch
            }

            Timber.d("BrowseLoadingAuxManager.enrichAudio: enriching ${audioWithoutMeta.size} files")

            val enriched = cachedMediaMetadataExtractor.enrichBatch(
                resourceId = resource.id,
                resourceType = resource.type,
                credentialsId = resource.credentialsId,
                files = audioWithoutMeta
            )
            val enrichedMap = enriched.associateBy { it.path }

            val latestFiles = stateFlow.value.mediaFiles
            var enrichedCount = 0
            val updatedFiles = latestFiles.map { file ->
                val e = enrichedMap[file.path]
                if (e != null && e !== file &&
                    (e.artist != file.artist || e.title != file.title ||
                        e.album != file.album || e.duration != file.duration)
                ) {
                    enrichedCount++
                    file.copy(
                        artist = e.artist ?: file.artist,
                        album = e.album ?: file.album,
                        title = e.title ?: file.title,
                        duration = e.duration ?: file.duration
                    )
                } else file
            }

            if (enrichedCount == 0) {
                Timber.d("BrowseLoadingAuxManager.enrichAudio: no new metadata found")
                return@launch
            }
            Timber.i("BrowseLoadingAuxManager.enrichAudio: enriched $enrichedCount files")

            updateState { it.copy(mediaFiles = updatedFiles) }
            MediaFilesCacheManager.setCachedList(resource.id, updatedFiles)

            if (resource.rememberFileList) {
                try {
                    cachedFileListRepository.saveCachedFiles(resource.id, updatedFiles)
                } catch (ex: Exception) {
                    Timber.e(ex, "BrowseLoadingAuxManager.enrichAudio: failed to save DB cache")
                }
            }
            cachedMediaMetadataExtractor.logSessionDiagnostics("Browse-enrich")
        }
    }

    /**
     * Schedule an ExoPlayer pre-warm if the file list is predominantly video.
     * Skipped when `settings.enablePlayerWarmup` is false or the list hasn't changed.
     */
    suspend fun schedulePlayerWarmupIfEligible(mediaFiles: List<MediaFile>) {
        val settings = getSettings()
        if (!settings.enablePlayerWarmup) return

        val regularFiles = mediaFiles.filter { !it.isDirectory }
        if (regularFiles.isEmpty()) return

        val videoCount = regularFiles.count { it.type == MediaType.VIDEO }
        val videoRatio = videoCount.toDouble() / regularFiles.size.toDouble()
        if (videoRatio < 0.8) return

        val sig = "${regularFiles.size}:$videoCount:${regularFiles.first().path}:${regularFiles.last().path}"
        if (sig == lastWarmupSignature) return
        lastWarmupSignature = sig

        playerWarmupJob?.cancel()
        playerWarmupJob = scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Timber.d("BrowseLoadingAuxManager: ExoPlayer warmup start (videoRatio=${"%.2f".format(videoRatio)})")
                    ExoPlayer.Builder(context).build().release()
                }
                Timber.d("BrowseLoadingAuxManager: ExoPlayer warmup done")
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("BrowseLoadingAuxManager: ExoPlayer warmup cancelled")
                throw e
            } catch (e: Exception) {
                Timber.w(e, "BrowseLoadingAuxManager: ExoPlayer warmup failed (non-critical)")
            }
        }
    }

    /** Cancel the player warmup job (called from cancelScan). */
    fun cancelPlayerWarmup() {
        playerWarmupJob?.cancel()
    }

    /** Cancel all owned jobs (called from onCleared). */
    fun cancelAll() {
        playerWarmupJob?.cancel()
        audioMetadataEnrichmentJob?.cancel()
    }
}
