package com.sza.fastmediasorter.ui.streams

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamPlaylistUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RemoveStreamSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * State holder for the "Трансляции" list screen. Observes the catalog and forwards user intents to
 * the use cases; maps typed use-case failures to one-shot localized [StreamsEvent]s. No Android View
 * types - the Activity resolves [StreamsEvent.Message.messageResId] to text (Clean layering, Rule 3).
 *
 * S0570: the raw source Flow is combined with a [StreamsFilter] (query + facets + [SortMode]) to
 * derive the list the UI renders; filtering/sorting lives here, not in the Activity. Pinned-first is
 * always the primary order key (matches the DAO ordering) before the chosen [SortMode].
 */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    observeStreamSources: ObserveStreamSourcesUseCase,
    private val addStreamSource: AddStreamSourceUseCase,
    private val importStreamPlaylist: ImportStreamPlaylistUseCase,
    private val importStreamCatalog: ImportStreamCatalogUseCase,
    private val pinStreamSource: PinStreamSourceUseCase,
    private val removeStreamSource: RemoveStreamSourceUseCase,
    private val recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    // S0577: the streams screen reads the background-playback gate and exit preference to mirror the
    // player's behavior. Eager so `.value` is current when the Activity decides the playback path.
    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _filter = MutableStateFlow(StreamsFilter())

    private val _events = Channel<StreamsEvent>(Channel.BUFFERED)
    val events: Flow<StreamsEvent> = _events.receiveAsFlow()

    init {
        combine(observeStreamSources(), _filter) { sources, filter ->
            StreamsUiState(
                sources = applyFilter(sources, filter),
                filter = filter,
                facets = facetsOf(sources),
                isLoading = false,
            )
        }
            .onEach { newState -> _state.update { newState.copy(isImporting = it.isImporting) } }
            .launchIn(viewModelScope)
    }

    fun onAdd(url: String, title: String?) = viewModelScope.launch {
        when (addStreamSource(url, title)) {
            AddStreamSourceUseCase.AddResult.InvalidUrl ->
                _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
            else -> Unit
        }
    }

    fun onImport(listUrl: String) = viewModelScope.launch {
        if (listUrl.isBlank()) {
            _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
            return@launch
        }
        when (val result = importStreamPlaylist(listUrl)) {
            is ImportStreamPlaylistUseCase.ImportResult.Success ->
                _events.send(StreamsEvent.ImportFinished(result.inserted))
            ImportStreamPlaylistUseCase.ImportResult.Empty ->
                _events.send(StreamsEvent.ImportFinished(0))
            is ImportStreamPlaylistUseCase.ImportResult.Failure ->
                _events.send(StreamsEvent.Message(R.string.streams_error_network))
        }
    }

    /** Downloads/refreshes the curated FastMediaSorter catalog; reports the added/updated/removed delta. */
    fun onImportCatalog() = viewModelScope.launch {
        Timber.d("S0570: catalog import requested")
        _state.update { it.copy(isImporting = true) }
        try {
            when (val result = importStreamCatalog()) {
                is ImportStreamCatalogUseCase.CatalogImportResult.Success -> {
                    Timber.d("S0570: catalog merged added=%d updated=%d removed=%d", result.added, result.updated, result.removed)
                    _events.send(StreamsEvent.CatalogUpdated(result.added, result.updated, result.removed))
                }
                ImportStreamCatalogUseCase.CatalogImportResult.Empty ->
                    _events.send(StreamsEvent.Message(R.string.streams_catalog_empty))
                is ImportStreamCatalogUseCase.CatalogImportResult.Failure ->
                    _events.send(StreamsEvent.Message(R.string.streams_error_network))
            }
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun onQueryChanged(query: String) = _filter.update { it.copy(query = query) }

    fun onFilter(
        category: String? = null,
        language: String? = null,
        mediaKind: MediaKindFilter = MediaKindFilter.ALL,
    ) = _filter.update {
        it.copy(category = category, language = language, mediaKind = mediaKind)
    }

    fun onSort(mode: SortMode) {
        Timber.d("S0570: list sort changed to %s", mode)
        _filter.update { it.copy(sort = mode) }
    }

    fun onPin(id: String) = viewModelScope.launch { pinStreamSource(id) }

    fun onRemove(source: StreamSourceEntity) = viewModelScope.launch { removeStreamSource(source) }

    /** S0593: record the inline-audio play outcome (OK on first playing, FAIL on error) for the row bullet. */
    fun recordStreamOutcome(id: String, ok: Boolean) =
        viewModelScope.launch { recordStreamPlayOutcome(id, ok) }

    /** S0577: persist the background-audio exit preference chosen from the streams exit dialog. */
    fun updateExitBehavior(behavior: BackgroundAudioExitBehavior) = viewModelScope.launch {
        val settings = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(settings.copy(backgroundAudioExitBehavior = behavior))
    }

    companion object {
        /**
         * Filters by case-insensitive query (title/topic/language substring) and the active facets, then
         * orders pinned-first followed by the chosen [SortMode]. Category, language and media-kind facets
         * are ANDed: each unset facet passes everything, so a separate ALL/ANY match-mode toggle is
         * redundant (selecting "All" on a facet already disables it). A language-less row always passes the
         * language predicate (strategic §6.4). The media-kind facet folds VIDEO and RTSP transports into a
         * single "video" bucket. The incoming list is already pinned-first from the DAO; re-sorting keeps
         * that invariant explicit and stable. `internal` so the pure filter logic is unit-testable without
         * the ViewModel's injected graph.
         */
        internal fun applyFilter(sources: List<StreamSourceEntity>, filter: StreamsFilter): List<StreamSourceEntity> {
            val query = filter.query.trim().lowercase()
            val matched = sources.filter { source ->
                val queryHit = query.isEmpty() ||
                    source.title.lowercase().contains(query) ||
                    source.topic?.lowercase()?.contains(query) == true ||
                    source.language?.lowercase()?.contains(query) == true
                val categoryHit = filter.category == null || source.category == filter.category
                // Per strategic §6.4 a language-less row is never hidden by an active language filter,
                // so the language predicate passes when the cell is null/blank.
                val languageHit = filter.language == null ||
                    source.language.isNullOrBlank() ||
                    source.language.tokens().any { it.equals(filter.language, ignoreCase = true) }
                // mediaKind values are the StreamSourceEntity contract ("AUDIO" / "VIDEO" / "RTSP").
                val mediaHit = when (filter.mediaKind) {
                    MediaKindFilter.ALL -> true
                    MediaKindFilter.AUDIO -> source.mediaKind == "AUDIO"
                    // RTSP is a video transport, so it shares the "video" bucket.
                    MediaKindFilter.VIDEO -> source.mediaKind == "VIDEO" || source.mediaKind == "RTSP"
                }
                // topic stays ANDed: not exposed in the filter UI (the query box covers topic).
                val topicHit = filter.topic == null || source.topic == filter.topic
                queryHit && categoryHit && languageHit && mediaHit && topicHit
            }
            val secondary: Comparator<StreamSourceEntity> = when (filter.sort) {
                SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                SortMode.TOPIC -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.topic }
                SortMode.LANGUAGE -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.language }
                SortMode.RECENT -> compareByDescending { it.addedAt }
            }
            // Pinned-first is the primary key regardless of the chosen secondary order.
            return matched.sortedWith(compareByDescending<StreamSourceEntity> { it.pinned }.then(secondary))
        }

        internal fun facetsOf(sources: List<StreamSourceEntity>): StreamsFacets = StreamsFacets(
            categories = sources.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().sorted(),
            topics = sources.mapNotNull { it.topic?.takeIf(String::isNotBlank) }.distinct().sorted(),
            // Catalog language cells can be comma-separated (e.g. "russian,ukrainian"); split into
            // individual language names so each is a separate, single-language facet option.
            languages = sources.asSequence()
                .mapNotNull { it.language }
                .flatMap { it.splitToSequence(',') }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .toList(),
        )
    }

    data class StreamsUiState(
        val sources: List<StreamSourceEntity> = emptyList(),
        val filter: StreamsFilter = StreamsFilter(),
        val facets: StreamsFacets = StreamsFacets(),
        val isLoading: Boolean = true,
        val isImporting: Boolean = false,
    ) {
        val isEmpty: Boolean get() = !isLoading && sources.isEmpty()
    }

    /** Distinct facet values present in the catalog, surfaced for the filter UI. */
    data class StreamsFacets(
        val categories: List<String> = emptyList(),
        val topics: List<String> = emptyList(),
        val languages: List<String> = emptyList(),
    )

    data class StreamsFilter(
        val query: String = "",
        val category: String? = null,
        val topic: String? = null,
        val language: String? = null,
        val mediaKind: MediaKindFilter = MediaKindFilter.ALL,
        val sort: SortMode = SortMode.NAME,
    )

    enum class SortMode { NAME, TOPIC, LANGUAGE, RECENT }

    /** Media-kind facet: ALL passes everything, AUDIO matches audio rows, VIDEO matches VIDEO + RTSP rows. */
    enum class MediaKindFilter { ALL, AUDIO, VIDEO }

    sealed interface StreamsEvent {
        data class Message(@StringRes val messageResId: Int) : StreamsEvent
        data class ImportFinished(val inserted: Int) : StreamsEvent
        data class CatalogUpdated(val added: Int, val updated: Int, val removed: Int) : StreamsEvent
    }
}

/** Splits a catalog language cell (e.g. "russian,ukrainian") into trimmed, non-blank language tokens. */
private fun String?.tokens(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
