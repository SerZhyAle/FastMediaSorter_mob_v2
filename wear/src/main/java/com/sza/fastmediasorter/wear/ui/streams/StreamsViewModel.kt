package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.isVideoKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1708/S1871: ViewModel for the Wear OS streams list screen.
 */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val importCatalogUseCase: ImportWearStreamCatalogUseCase,
    private val faviconAtlasStore: WearFaviconAtlasStore,
    private val preferencesRepository: WearPreferencesRepository,
    private val preparePlayback: PrepareWearStreamPlaybackUseCase
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
                _uiState.update { state ->
                    val display = computeDisplayChannels(
                        channels = channels,
                        query = state.searchQuery,
                        filterKind = state.filterKind,
                        sortOrder = state.sortOrder
                    )
                    state.copy(channels = channels, displayChannels = display)
                }
                if (channels.isEmpty() && !_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    refreshCatalog(isInitial = true)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        Timber.d("S1946: streams search query reached the state, length=${query.length}")
        _uiState.update { state ->
            val display = computeDisplayChannels(state.channels, query, state.filterKind, state.sortOrder)
            state.copy(
                searchQuery = query,
                displayChannels = display,
                showSearchDialog = false,
                // A query arriving is proof the input path answered, so any earlier refusal is stale.
                searchInputUnavailable = false,
            )
        }
    }

    /** S1946: no text or speech input activity answered, and the user is owed that in words. */
    fun setSearchInputUnavailable() {
        _uiState.update { it.copy(searchInputUnavailable = true) }
    }

    fun setFilterKind(kind: StreamFilterKind) {
        _uiState.update { state ->
            val display = computeDisplayChannels(state.channels, state.searchQuery, kind, state.sortOrder)
            state.copy(filterKind = kind, displayChannels = display, showFilterDialog = false)
        }
    }

    fun setSortOrder(order: StreamSortOrder) {
        _uiState.update { state ->
            val display = computeDisplayChannels(state.channels, state.searchQuery, state.filterKind, order)
            state.copy(sortOrder = order, displayChannels = display, showSortDialog = false)
        }
    }

    fun setShowSearchDialog(show: Boolean) {
        _uiState.update { it.copy(showSearchDialog = show) }
    }

    fun setShowFilterDialog(show: Boolean) {
        _uiState.update { it.copy(showFilterDialog = show) }
    }

    fun setShowSortDialog(show: Boolean) {
        _uiState.update { it.copy(showSortDialog = show) }
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

    /**
     * S1944: the preparation moved to [PrepareWearStreamPlaybackUseCase] - the phone can now ask the
     * watch to open a channel, and that request lands in the Data Layer listener rather than here, so
     * both entrances must share one answer. The list still supplies what it was showing, which is
     * what keeps paging inside the user's current view.
     */
    fun prepareStreamPlayback(channel: WearStreamChannel): StreamPlaybackTarget {
        val target = preparePlayback(channel, _uiState.value.displayChannels)
        return StreamPlaybackTarget(fileId = target.fileId, isVideo = target.isVideo)
    }

    data class StreamPlaybackTarget(
        val fileId: Long,
        val isVideo: Boolean
    )
}

private fun computeDisplayChannels(
    channels: List<WearStreamChannel>,
    query: String,
    filterKind: StreamFilterKind,
    sortOrder: StreamSortOrder
): List<WearStreamChannel> {
    var result = channels

    if (query.isNotBlank()) {
        val trimmed = query.trim()
        result = result.filter { ch ->
            ch.name.contains(trimmed, ignoreCase = true) ||
                (ch.category?.contains(trimmed, ignoreCase = true) == true)
        }
    }

    result = when (filterKind) {
        StreamFilterKind.ALL -> result
        StreamFilterKind.AUDIO_ONLY -> result.filter { !it.isVideoKind() }
        StreamFilterKind.VIDEO_ONLY -> result.filter { it.isVideoKind() }
    }

    result = when (sortOrder) {
        StreamSortOrder.DEFAULT -> result
        StreamSortOrder.NAME_ASC -> result.sortedBy { it.name.lowercase() }
        StreamSortOrder.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
        StreamSortOrder.KIND -> result.sortedWith(
            compareBy<WearStreamChannel> { !it.isVideoKind() }.thenBy { it.name.lowercase() }
        )
    }

    return result
}
