package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1708: ViewModel for the Wear OS streams list screen.
 */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val importCatalogUseCase: ImportWearStreamCatalogUseCase,
    private val faviconAtlasStore: WearFaviconAtlasStore,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private val faviconSlicer = WearFaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    init {
        Timber.d("S1708: streams view model initialized")
        viewModelScope.launch {
            preferencesRepository.viewMode.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }

        viewModelScope.launch {
            repository.observeChannels().collect { channels ->
                _uiState.update { it.copy(channels = channels) }
                if (channels.isEmpty() && !_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    refreshCatalog(isInitial = true)
                }
            }
        }
    }

    fun refreshCatalog(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(isLoading = true, error = null)
                } else {
                    it.copy(isRefreshing = true, error = null)
                }
            }
            when (val result = importCatalogUseCase()) {
                is CatalogImportResult.Success -> {
                    Timber.d("StreamsViewModel: Catalog imported ${result.count} channels")
                    faviconSlicer.invalidate()
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
                }
                is CatalogImportResult.Empty -> {
                    Timber.d("StreamsViewModel: Catalog import was empty")
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                }
                is CatalogImportResult.Failure -> {
                    Timber.w("StreamsViewModel: Catalog import failed: ${result.reason}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.channels.isEmpty()) result.reason else null
                        )
                    }
                }
            }
        }
    }

    suspend fun getFaviconTile(faviconIndex: Int?): Bitmap? {
        if (faviconIndex == null) return null
        return faviconSlicer.tileFor(faviconIndex)
    }

    fun prepareStreamPlayback(channel: WearStreamChannel): StreamPlaybackTarget {
        val isVideo = channel.mediaKind.equals("VIDEO", ignoreCase = true) ||
            channel.mediaKind.equals("RTSP", ignoreCase = true)

        val mediaFile = WearMediaFile(
            id = channel.url.hashCode().toLong(),
            name = channel.name,
            uri = Uri.parse(channel.url),
            mimeType = if (isVideo) "video/*" else "audio/*",
            size = 0L,
            dateModified = 0L
        )

        selectedMediaManager.selectFile(
            file = mediaFile,
            isNetworkSource = true,
            streamUri = channel.url,
            sourceId = "stream",
            isDirectStream = true
        )

        val channels = _uiState.value.channels
        val matchingChannels = if (isVideo) {
            channels.filter { it.isVideoKind() }
        } else {
            channels.filter { !it.isVideoKind() }
        }

        val files = matchingChannels.map { ch ->
            WearMediaFile(
                id = ch.url.hashCode().toLong(),
                name = ch.name,
                uri = Uri.parse(ch.url),
                mimeType = if (isVideo) "video/*" else "audio/*",
                size = 0L,
                dateModified = 0L
            )
        }

        val startIndex = matchingChannels.indexOfFirst { it.url == channel.url }.coerceAtLeast(0)
        playbackSetManager.publish(files, startIndex)

        return StreamPlaybackTarget(
            fileId = mediaFile.id,
            isVideo = isVideo
        )
    }

    data class StreamPlaybackTarget(
        val fileId: Long,
        val isVideo: Boolean
    )
}

// A catalog row carries its kind as free text, so VIDEO and RTSP are both "play it in the video
// player" and the two spellings must never drift apart between the two call sites above.
private fun WearStreamChannel.isVideoKind(): Boolean =
    mediaKind.equals("VIDEO", ignoreCase = true) || mediaKind.equals("RTSP", ignoreCase = true)
