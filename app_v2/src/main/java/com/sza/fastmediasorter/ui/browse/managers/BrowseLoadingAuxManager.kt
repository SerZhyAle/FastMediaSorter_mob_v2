package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
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
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
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

    // @Volatile: written on IO (warm-up scheduled after a scan), cancelled/read on main (cancelAll /
    // cancelPlayerWarmup) - without it the cancel edge can read a stale value and miss the live job.
    @Volatile private var playerWarmupJob: Job? = null

    @Volatile private var lastWarmupSignature: String? = null

    @Volatile private var audioMetadataEnrichmentJob: Job? = null

    private fun getFriendlyBrowseErrorMessage(throwable: Throwable): String =
        context.getString(resolveFriendlyBrowseErrorRes(throwable))

    private fun resolveFriendlyBrowseErrorRes(throwable: Throwable): Int {
        // WifiRequiredException fires before any socket attempt - clearly a Wi-Fi gate rejection,
        // not a generic outage. Must be checked by type before the message-based heuristics below.
        if (throwable is WifiRequiredException) return R.string.error_wifi_required_smb

        // A watchdog-aborted scan is a distinct case: a folder that never finished listing, not a
        // per-request socket timeout. Map by type so the user sees the scan-specific message.
        if (throwable is com.sza.fastmediasorter.data.network.exceptions.ScanTimeoutException) {
            return R.string.error_scan_timeout
        }

        // SFTP protocol status is locale-independent; the server's message text is not
        // (Windows OpenSSH sends "cannot find the file specified", matched by no rule below). S1000.
        when (com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure.fromThrowable(throwable).category) {
            com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory.NOT_FOUND ->
                return R.string.friendly_copy_error_not_found
            com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory.PERMISSION_DENIED ->
                return R.string.friendly_copy_error_access_denied
            else -> Unit
        }

        val message = throwable.message.orEmpty()
        return when {
            message.contains("Authentication", ignoreCase = true) ||
                message.contains("LOGON_FAILURE", ignoreCase = true) ||
                message.contains("Not authenticated", ignoreCase = true) ->
                R.string.friendly_copy_error_auth_failed

            (message.contains("permission", ignoreCase = true) &&
                message.contains("denied", ignoreCase = true)) ||
                message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                message.contains("access denied", ignoreCase = true) ->
                R.string.friendly_copy_error_access_denied

            message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
                message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true) ||
                message.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ->
                R.string.friendly_copy_error_not_found

            message.contains("unreachable", ignoreCase = true) ||
                message.contains("Cannot resolve host", ignoreCase = true) ||
                message.contains("Unknown host", ignoreCase = true) ->
                R.string.friendly_copy_error_no_connection

            message.contains("Connection reset", ignoreCase = true) ||
                message.contains("connection closed", ignoreCase = true) ||
                message.contains("broken pipe", ignoreCase = true) ||
                message.contains("connection lost", ignoreCase = true) ->
                R.string.error_network_connection_lost

            message.contains("timed out", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("SocketTimeoutException", ignoreCase = true) ->
                R.string.error_network_timeout

            message.contains("Connection", ignoreCase = true) ||
                message.contains("Network", ignoreCase = true) ->
                R.string.error_network_connection

            else -> R.string.friendly_copy_error_generic
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Dispatch a structured, user-friendly error event for loading failures.
     * Updates resource availability to `false` for connectivity errors.
     */
    fun handleLoadingError(resource: MediaResource, e: Throwable) {
        if (e is LocalNetworkPermissionDeniedException) {
            sendEvent(BrowseEvent.ShowLocalNetworkPermissionRequired)
            setLoading(false)
            return
        }
        // ConnectionThrottle cancellation: video player is active
        if (e is java.util.concurrent.CancellationException &&
            e.message?.contains("Video player priority", ignoreCase = true) == true
        ) {
            Timber.d("BrowseLoadingAuxManager: folder loading blocked by video player throttle")
            sendEvent(BrowseEvent.ShowError(
                message = context.getString(R.string.error_folder_loading_blocked_by_video_player),
                details = buildString {
                    append("${context.getString(R.string.error_details_resource)}: ${resource.name}\n")
                    append("${context.getString(R.string.error_details_path)}: ${resource.path}\n\n")
                    append(context.getString(R.string.error_folder_loading_blocked_by_video_player_details))
                },
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
            Timber.d("BrowseLoadingAuxManager: Cloud auth error - sending ShowCloudAuthenticationRequired")
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
                    e.message?.contains("timed out", ignoreCase = true) == true ||
                    e.message?.contains("unreachable", ignoreCase = true) == true
                if (isConnErr && resource.isAvailable) {
                    updateResourceUseCase(resource.copy(isAvailable = false))
                    Timber.d("BrowseLoadingAuxManager: marked resource unavailable due to connection error")
                }
            } catch (ex: Exception) {
                Timber.e(ex, "BrowseLoadingAuxManager: failed to update resource availability")
            }
        }

        val details = buildString {
            append("${context.getString(R.string.error_details_resource)}: ${resource.name}\n")
            append("${context.getString(R.string.error_details_path)}: ${resource.path}\n")
            append("${context.getString(R.string.error_details_resource_type)}: ${resource.type}")
        }

        // S1014: on a browse-scan connectivity failure of a companion/SFTP resource, show the companion's
        // access note (or the default access guidance) instead of a bare timeout - this is the surface the
        // user sees when opening a shared folder that cannot be reached on any address.
        sendEvent(BrowseEvent.ShowError(
            message = companionConnectGuidance(resource, e) ?: getFriendlyBrowseErrorMessage(e),
            details = details,
            exception = e
        ))
        onHandleError(e)
    }

    /**
     * S1014: actionable access guidance for a browse-scan connectivity failure of a companion-style
     * (SFTP/FTP) resource - the companion's own access note when present, otherwise the default guidance.
     * Returns null (fall back to the generic friendly error) for other resource types or non-connectivity
     * failures.
     */
    private fun companionConnectGuidance(resource: MediaResource, e: Throwable): String? {
        val msg = e.message.orEmpty()
        val isConnectivity = msg.contains("timeout", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ||
            msg.contains("unreachable", ignoreCase = true) ||
            msg.contains("Connection", ignoreCase = true)
        val companion = resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP
        if (!isConnectivity || (!companion && resource.accessNote.isNullOrBlank())) {
            return null
        }
        Timber.d("S1014: browse connect guidance for ${resource.type}, note=${!resource.accessNote.isNullOrBlank()}")
        return resource.accessNote?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.error_companion_connect_guidance)
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
