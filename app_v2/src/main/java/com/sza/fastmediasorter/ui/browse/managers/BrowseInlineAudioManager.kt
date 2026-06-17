package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.media.MediaPlayer
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.SaveResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.InlinePlayerState
import com.sza.fastmediasorter.ui.browse.PlaybackStatus
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages inline audio playback in the Browse screen.
 *
 * Responsibilities:
 * - Start / pause / resume / stop Android MediaPlayer for local & SMB audio files.
 * - Download SMB audio to app cache on first play; prefetch the next track.
 * - Track per-file download progress in [inlinePlayerState].
 * - Save / restore inline-playback resume state via [SaveResumeStateUseCase].
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseInlineAudioManager(
    private val context: Context,
    private val smbClient: Lazy<SmbClient>,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val saveResumeStateUseCase: SaveResumeStateUseCase,
    private val windowIdProvider: () -> String,
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<BrowseState>,
    /** Called when resume logic needs to navigate to a subfolder before starting playback. */
    private val onNavigateToFolder: (String) -> Unit
) {
    private var player: MediaPlayer? = null
    private val prefetchInProgress = ConcurrentHashMap.newKeySet<String>()

    private val _inlinePlayerState = MutableStateFlow(InlinePlayerState())
    val inlinePlayerState: StateFlow<InlinePlayerState> = _inlinePlayerState.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Toggle playback for [file]:
     * - IDLE / different file → start new
     * - PLAYING same file    → pause
     * - PAUSED  same file    → resume
     */
    fun inlinePlayToggle(file: MediaFile) {
        val current = _inlinePlayerState.value
        when {
            current.playingPath == file.path && current.status == PlaybackStatus.PLAYING -> {
                player?.pause()
                _inlinePlayerState.value = current.copy(status = PlaybackStatus.PAUSED)
                Timber.d("InlinePlayer: paused '${file.name}'")
                saveResumeState()
            }
            current.playingPath == file.path && current.status == PlaybackStatus.PAUSED -> {
                player?.start()
                _inlinePlayerState.value = current.copy(status = PlaybackStatus.PLAYING)
                Timber.d("InlinePlayer: resumed '${file.name}'")
                saveResumeState()
            }
            else -> {
                inlineStop()
                inlineStart(file)
            }
        }
    }

    /** Stop playback and release MediaPlayer resources. */
    fun inlineStop() {
        player?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        player = null
        _inlinePlayerState.value = InlinePlayerState()
        Timber.d("InlinePlayer: stopped")
    }

    /**
     * One-shot: wait for initial file list, navigate to resume folder if needed,
     * then start inline playback of the previously playing file.
     */
    fun attemptResumeInlinePlayback(
        initialFolderPath: String?,
        initialFilePath: String?,
        isPlaying: Boolean?
    ) {
        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(15_000L) {
                    stateFlow.first { it.mediaFiles.isNotEmpty() }
                }
                kotlinx.coroutines.delay(300)

                if (initialFolderPath != null) {
                    Timber.d("InlinePlayer: Resume - navigating to '$initialFolderPath'")
                    onNavigateToFolder(initialFolderPath)
                    kotlinx.coroutines.withTimeout(15_000L) {
                        stateFlow.first {
                            it.currentPath == initialFolderPath && it.mediaFiles.isNotEmpty()
                        }
                    }
                    kotlinx.coroutines.delay(200)
                }

                val targetPath = initialFilePath ?: return@launch
                val targetFile = stateFlow.value.mediaFiles.firstOrNull { it.path == targetPath }
                if (targetFile != null && isPlaying == true) {
                    Timber.d("InlinePlayer: Resume - starting '${targetFile.name}'")
                    inlinePlayToggle(targetFile)
                } else if (targetFile != null) {
                    Timber.d("InlinePlayer: Resume - found file but isPlaying=false, not auto-starting")
                } else {
                    Timber.w("InlinePlayer: Resume - target file not found: $targetPath")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.w("InlinePlayer: Resume timed out waiting for file list")
            } catch (e: Exception) {
                Timber.e(e, "InlinePlayer: Resume failed")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun inlineStart(file: MediaFile) {
        scope.launch(Dispatchers.IO) {
            try {
                val localPath = resolveLocalPath(file)
                if (localPath == null) {
                    Timber.e("InlinePlayer: cannot resolve playback path for '${file.name}'")
                    _inlinePlayerState.value = InlinePlayerState()
                    return@launch
                }

                _inlinePlayerState.value = _inlinePlayerState.value.copy(
                    playingPath = file.path,
                    status = PlaybackStatus.IDLE,
                    downloadingPath = null,
                    downloadProgressPercent = 0
                )

                val newPlayer = MediaPlayer().apply {
                    try {
                        setDataSource(localPath)
                    } catch (e: java.io.IOException) {
                        val contentUri = file.contentUri
                        if (contentUri != null) {
                            Timber.i("InlinePlayer: direct path failed, using content URI for '${file.name}'")
                            reset()
                            setDataSource(context, android.net.Uri.parse(contentUri))
                        } else {
                            throw e
                        }
                    }
                    prepare()
                    setOnCompletionListener { inlinePlayNext() }
                }
                player = newPlayer
                _inlinePlayerState.value = InlinePlayerState(
                    playingPath = file.path,
                    status = PlaybackStatus.PLAYING
                )
                newPlayer.start()
                Timber.d("InlinePlayer: started '${file.name}'")
                saveResumeState()
                prefetchNextInlineAudio(file.path)
            } catch (e: Exception) {
                Timber.e(e, "InlinePlayer: failed to start '${file.name}'")
                _inlinePlayerState.value = InlinePlayerState()
            }
        }
    }

    private fun inlinePlayNext() {
        val currentPath = _inlinePlayerState.value.playingPath ?: return
        val audioFiles = stateFlow.value.mediaFiles.filter { !it.isDirectory && it.type == MediaType.AUDIO }
        if (audioFiles.isEmpty()) return
        val idx = audioFiles.indexOfFirst { it.path == currentPath }
        val next = audioFiles[(idx + 1) % audioFiles.size]
        inlineStop()
        inlineStart(next)
    }

    private fun prefetchNextInlineAudio(currentPath: String) {
        scope.launch(Dispatchers.IO) {
            val audioFiles = stateFlow.value.mediaFiles.filter { !it.isDirectory && it.type == MediaType.AUDIO }
            if (audioFiles.size < 2) return@launch
            val index = audioFiles.indexOfFirst { it.path == currentPath }
            if (index < 0) return@launch
            val next = audioFiles[(index + 1) % audioFiles.size]
            if (!next.path.startsWith("smb://")) return@launch
            val added = prefetchInProgress.add(next.path)
            if (!added) return@launch
            try {
                val cacheFile = getInlineAudioCacheFile(next)
                if (cacheFile.exists() && cacheFile.length() > 0L) return@launch
                Timber.d("InlinePlayer: prefetching '${next.name}'")
                downloadSmbAudioToCache(next, showProgress = false)
            } finally {
                prefetchInProgress.remove(next.path)
            }
        }
    }

    private suspend fun resolveLocalPath(file: MediaFile): String? {
        val path = file.path
        // Non-SMB network protocols (sftp://, ftp://, etc.) are not cacheable here; only local and content:// paths pass through.
        if (!path.startsWith("smb://")) return path.takeUnless { "://" in it && !it.startsWith("content://") }
        val cacheFile = getInlineAudioCacheFile(file)
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            Timber.d("InlinePlayer: cache hit for '${file.name}'")
            return cacheFile.absolutePath
        }
        _inlinePlayerState.value = _inlinePlayerState.value.copy(
            playingPath = file.path,
            status = PlaybackStatus.IDLE,
            downloadingPath = file.path,
            downloadProgressPercent = 0
        )
        return downloadSmbAudioToCache(file, showProgress = true)
    }

    private fun getInlineAudioCacheFile(file: MediaFile): java.io.File {
        val cacheDir = java.io.File(context.cacheDir, "inline_audio")
        cacheDir.mkdirs()
        return java.io.File(cacheDir, "${file.path.hashCode()}_${file.name}")
    }

    private suspend fun downloadSmbAudioToCache(file: MediaFile, showProgress: Boolean): String? {
        val path = file.path
        val credentialsId = stateFlow.value.resource?.credentialsId
        if (credentialsId == null) {
            Timber.e("InlinePlayer: no credentialsId for resource")
            return null
        }
        val connectionInfoResult = smbOperationsUseCase.getConnectionInfo(credentialsId)
        if (connectionInfoResult.isFailure) {
            Timber.e(connectionInfoResult.exceptionOrNull(), "InlinePlayer: failed to get connection info")
            return null
        }
        val connectionInfo = connectionInfoResult.getOrThrow()
        val uri = android.net.Uri.parse(path)
        val segments = uri.pathSegments
        if (segments.size < 2) {
            Timber.e("InlinePlayer: invalid SMB path '$path'")
            return null
        }
        val shareFromPath = segments.first()
        val filePathInShare = segments.drop(1).joinToString("/")
        val effectiveConnectionInfo = if (connectionInfo.shareName != shareFromPath) {
            connectionInfo.copy(shareName = shareFromPath)
        } else {
            connectionInfo
        }
        val cacheFile = getInlineAudioCacheFile(file)
        var lastReportedPercent = -1
        val progressCallback: ByteProgressCallback? = if (showProgress) {
            object : ByteProgressCallback {
                override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                    if (totalBytes <= 0L) return
                    val percent = ((bytesTransferred * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    if (percent == lastReportedPercent || percent - lastReportedPercent < 2) return
                    lastReportedPercent = percent
                    _inlinePlayerState.value = _inlinePlayerState.value.copy(
                        playingPath = file.path,
                        downloadingPath = file.path,
                        downloadProgressPercent = percent
                    )
                }
            }
        } else null

        Timber.d("InlinePlayer: downloading '${file.name}' from SMB")
        val result = java.io.FileOutputStream(cacheFile).use { output ->
            smbClient.get().downloadFile(
                connectionInfo = effectiveConnectionInfo,
                remotePath = filePathInShare,
                localOutputStream = output,
                fileSize = file.size,
                progressCallback = progressCallback
            )
        }
        return when (result) {
            is com.sza.fastmediasorter.data.network.model.SmbResult.Success -> {
                Timber.d("InlinePlayer: download complete, size=${cacheFile.length()}")
                if (showProgress) resetDownloadProgress()
                cacheFile.absolutePath
            }
            is com.sza.fastmediasorter.data.network.model.SmbResult.Error -> {
                Timber.e("InlinePlayer: SMB download failed: ${result.message}")
                cacheFile.delete()
                if (showProgress) resetDownloadProgress()
                null
            }
        }
    }

    private fun resetDownloadProgress() {
        _inlinePlayerState.value = _inlinePlayerState.value.copy(
            downloadingPath = null,
            downloadProgressPercent = 0
        )
    }

    private fun saveResumeState() {
        val inlineState = _inlinePlayerState.value
        val playingPath = inlineState.playingPath ?: return
        val currentState = stateFlow.value
        val resource = currentState.resource ?: return
        scope.launch {
            try {
                val resumeState = ResumeState(
                    filePath = playingPath,
                    resourceId = resource.id,
                    currentFolderPath = currentState.currentPath,
                    screenType = ScreenType.BROWSER,
                    sortMode = currentState.sortMode,
                    isPlaying = inlineState.status == PlaybackStatus.PLAYING,
                    isSlideshowEnabled = false,
                    mediaType = MediaType.AUDIO,
                    savedAt = System.currentTimeMillis()
                )
                saveResumeStateUseCase(windowIdProvider(), resumeState)
            } catch (e: Exception) {
                Timber.e(e, "InlinePlayer: Failed to save resume state")
            }
        }
    }
}
