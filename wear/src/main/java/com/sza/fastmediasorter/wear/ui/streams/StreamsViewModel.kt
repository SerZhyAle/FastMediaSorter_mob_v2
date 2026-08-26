package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
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
    private val preparePlayback: PrepareWearStreamPlaybackUseCase,
    private val favoritesRepository: WearFavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private val faviconSlicer = WearFaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    init {
        viewModelScope.launch {
            preferencesRepository.viewMode.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }

        viewModelScope.launch {
            repository.observeChannels().collect { channels ->
                // S1954: re-read the marks with the catalogue rather than per row. This is the only
                // point where the set can have changed without this screen being rebuilt.
                val pinned = loadPinnedStreamIds()
                _uiState.update { state ->
                    val topics = channels.mapNotNull { it.topic }.filter { it.isNotBlank() }.distinct().sorted()
                    val languages = channels.mapNotNull { it.language }
                        .flatMap { it.split(",") }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    val display = computeDisplayChannels(
                        channels = channels,
                        query = state.searchQuery,
                        filterKind = state.filterKind,
                        sortOrder = state.sortOrder,
                        selectedTopic = state.selectedTopic,
                        selectedLanguage = state.selectedLanguage,
                        pinnedIdentities = pinned
                    )
                    state.copy(
                        channels = channels,
                        displayChannels = display,
                        availableTopics = topics,
                        availableLanguages = languages,
                        pinnedStreamIds = pinned
                    )
                }
                if (channels.isEmpty() && !_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    refreshCatalog(isInitial = true)
                }
            }
        }
    }

    /**
     * S1954: re-reads the marks after the player may have changed them.
     *
     * The catalogue collector below is no longer the only writer: the video player marks a channel
     * without the catalogue emitting anything, so a screen returning from the player would keep the
     * order it was built with and the mark the user just made would not move its row.
     */
    fun refreshPinnedStreams() {
        Timber.d("S1954: streams screen re-reading pinned marks")
        viewModelScope.launch {
            val pinned = loadPinnedStreamIds()
            _uiState.update { state ->
                val display = computeDisplayChannels(
                    state.channels,
                    state.searchQuery,
                    state.filterKind,
                    state.sortOrder,
                    state.selectedTopic,
                    state.selectedLanguage,
                    pinned
                )
                state.copy(displayChannels = display, pinnedStreamIds = pinned)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val display = computeDisplayChannels(
                state.channels,
                query,
                state.filterKind,
                state.sortOrder,
                state.selectedTopic,
                state.selectedLanguage,
                state.pinnedStreamIds
            )
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
            val display = computeDisplayChannels(
                state.channels,
                state.searchQuery,
                kind,
                state.sortOrder,
                state.selectedTopic,
                state.selectedLanguage,
                state.pinnedStreamIds
            )
            state.copy(filterKind = kind, displayChannels = display, showFilterDialog = false)
        }
    }

    fun setSelectedTopic(topic: String?) {
        Timber.d("S1947: setSelectedTopic topic=$topic")
        _uiState.update { state ->
            val display = computeDisplayChannels(
                state.channels,
                state.searchQuery,
                state.filterKind,
                state.sortOrder,
                topic,
                state.selectedLanguage,
                state.pinnedStreamIds
            )
            state.copy(selectedTopic = topic, displayChannels = display, showFilterDialog = false)
        }
    }

    fun setSelectedLanguage(language: String?) {
        Timber.d("S1947: setSelectedLanguage language=$language")
        _uiState.update { state ->
            val display = computeDisplayChannels(
                state.channels,
                state.searchQuery,
                state.filterKind,
                state.sortOrder,
                state.selectedTopic,
                language,
                state.pinnedStreamIds
            )
            state.copy(selectedLanguage = language, displayChannels = display, showFilterDialog = false)
        }
    }

    fun setSortOrder(order: StreamSortOrder) {
        _uiState.update { state ->
            val display = computeDisplayChannels(
                state.channels,
                state.searchQuery,
                state.filterKind,
                order,
                state.selectedTopic,
                state.selectedLanguage,
                state.pinnedStreamIds
            )
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

    /**
     * S1954: the marked channels, as the normalized addresses the projection compares against.
     *
     * Only stream favourites are taken: the same store holds file marks, whose `filePath` is a path
     * and would never match an address anyway, but filtering by source id says so on purpose.
     */
    private suspend fun loadPinnedStreamIds(): Set<String> =
        favoritesRepository.getFavorites()
            .filter { it.sourceId == SOURCE_ID_STREAM }
            .mapTo(mutableSetOf()) { it.filePath }
}

private fun computeDisplayChannels(
    channels: List<WearStreamChannel>,
    query: String,
    filterKind: StreamFilterKind,
    sortOrder: StreamSortOrder,
    selectedTopic: String? = null,
    selectedLanguage: String? = null,
    pinnedIdentities: Set<String> = emptySet()
): List<WearStreamChannel> {
    var result = channels

    if (query.isNotBlank()) {
        val trimmed = query.trim()
        result = result.filter { ch ->
            ch.name.contains(trimmed, ignoreCase = true) ||
                (ch.category?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.topic?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.language?.contains(trimmed, ignoreCase = true) == true)
        }
    }

    result = when (filterKind) {
        StreamFilterKind.ALL -> result
        StreamFilterKind.AUDIO_ONLY -> result.filter { !it.isVideoKind() }
        StreamFilterKind.VIDEO_ONLY -> result.filter { it.isVideoKind() }
    }

    if (!selectedTopic.isNullOrBlank()) {
        result = result.filter { ch ->
            ch.topic?.equals(selectedTopic, ignoreCase = true) == true
        }
    }

    if (!selectedLanguage.isNullOrBlank()) {
        result = result.filter { ch ->
            ch.language?.split(",")?.any { it.trim().equals(selectedLanguage, ignoreCase = true) } == true
        }
    }

    result = when (sortOrder) {
        StreamSortOrder.DEFAULT -> result
        StreamSortOrder.NAME_ASC -> result.sortedBy { it.name.lowercase() }
        StreamSortOrder.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
        StreamSortOrder.KIND -> result.sortedWith(
            compareBy<WearStreamChannel> { !it.isVideoKind() }.thenBy { it.name.lowercase() }
        )
        StreamSortOrder.TOPIC -> result.sortedWith(
            compareBy<WearStreamChannel> { it.topic?.lowercase() ?: "" }.thenBy { it.name.lowercase() }
        )
        StreamSortOrder.LANGUAGE -> result.sortedWith(
            compareBy<WearStreamChannel> { it.language?.lowercase() ?: "" }.thenBy { it.name.lowercase() }
        )
    }

    if (pinnedIdentities.isEmpty()) {
        return result
    }
    // S1954: partition last and by address, not row id. Pinning is a second ordering key applied over
    // whatever the filter and sort already decided, so both groups keep the order chosen above, and a
    // catalogue re-import that renumbers every row leaves the marks where they were.
    val (pinned, unpinned) = result.partition { normalizeWearStreamUrl(it.url) in pinnedIdentities }
    return pinned + unpinned
}
