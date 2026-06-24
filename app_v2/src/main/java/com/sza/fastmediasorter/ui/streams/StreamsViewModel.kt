package com.sza.fastmediasorter.ui.streams

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.settings.StreamsSessionStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.model.StreamMediaTypeFilter
import com.sza.fastmediasorter.domain.model.StreamsCatalogRefreshPolicy
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamPlaylistUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UpdateStreamSourceUseCase
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
    private val updateStreamSource: UpdateStreamSourceUseCase,
    private val importStreamPlaylist: ImportStreamPlaylistUseCase,
    private val importStreamCatalog: ImportStreamCatalogUseCase,
    private val pinStreamSource: PinStreamSourceUseCase,
    private val removeStreamSource: RemoveStreamSourceUseCase,
    private val recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase,
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: StreamsSessionStore,
    // S0659: same synchronous Wi-Fi/unmetered check that backs searchAudioCoversOnlyOnWifi -
    // injected so the PERIODIC_WIFI policy never touches ConnectivityManager from the ViewModel.
    private val networkContextAnalyzer: NetworkContextAnalyzer,
) : ViewModel() {

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    // S0577: the streams screen reads the background-playback gate and exit preference to mirror the
    // player's behavior. Eager so `.value` is current when the Activity decides the playback path.
    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _filter = MutableStateFlow(StreamsFilter())

    // S0659: guards the one-shot session/defaults seed in init against clobbering an early user change.
    @Volatile
    private var initialFilterApplied = false

    // S0659: the catalog-refresh policy must run once per logical screen open, not once per Activity
    // instance - the ViewModel survives config-change recreation, so guarding here keeps a rotation from
    // re-suggesting / re-fetching.
    @Volatile
    private var catalogPolicyApplied = false

    private val _events = Channel<StreamsEvent>(Channel.BUFFERED)
    val events: Flow<StreamsEvent> = _events.receiveAsFlow()

    init {
        // S0659: restore the last session before the combine renders, falling back to the user defaults.
        // Applied once so a fast user interaction during the async DataStore read is never clobbered.
        viewModelScope.launch { seedInitialFilter() }

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

    /**
     * S0659: seed [_filter] from the persisted last session, else from the user defaults. Only sort,
     * media-kind and query carry over; the catalog-derived facets (category/topic/language) stay at
     * defaults. Skips if the user already changed the filter while the read was in flight.
     */
    private suspend fun seedInitialFilter() {
        if (initialFilterApplied) return
        val session = sessionStore.read()
        // A user intent during the async read flips the flag and persists its own state; do not clobber it.
        if (initialFilterApplied) return
        val defaults = settings.value
        _filter.value = _filter.value.copy(
            sort = session.lastSort?.toSortMode() ?: defaults.streamsDefaultSort.toSortMode(),
            mediaKind = session.lastMediaFilter?.toMediaKind() ?: defaults.streamsDefaultMediaFilter.toMediaKind(),
            query = session.lastQuery ?: "",
        )
        initialFilterApplied = true
    }

    /**
     * S0659: apply the catalog-refresh policy once per screen open. MANUAL does nothing; ON_OPEN offers a
     * throttled, dismissible suggestion; PERIODIC_WIFI auto-refreshes when on Wi-Fi/unmetered and the daily
     * throttle has elapsed. Throttling reads the last-refresh timestamp from the session store - opportunistic
     * on-open only, no WorkManager job (strategic §3.2 "no heavy background work by default").
     */
    fun onScreenOpened() = viewModelScope.launch {
        if (catalogPolicyApplied) return@launch
        catalogPolicyApplied = true
        val now = System.currentTimeMillis()
        val lastRefreshAt = sessionStore.read().lastCatalogRefreshAt
        when (settings.value.streamsCatalogRefreshPolicy) {
            StreamsCatalogRefreshPolicy.MANUAL -> Unit
            StreamsCatalogRefreshPolicy.ON_OPEN ->
                if (now - lastRefreshAt > ON_OPEN_THROTTLE_MS) _events.send(StreamsEvent.SuggestCatalogRefresh)
            StreamsCatalogRefreshPolicy.PERIODIC_WIFI ->
                if (networkContextAnalyzer.hasWifi() && now - lastRefreshAt > PERIODIC_THROTTLE_MS) onImportCatalog()
        }
    }

    fun onAdd(url: String, title: String?) = viewModelScope.launch {
        when (addStreamSource(url, title)) {
            AddStreamSourceUseCase.AddResult.InvalidUrl ->
                _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
            else -> Unit
        }
    }

    /** S0660: persist an in-place edit of a manual channel; only the invalid-url case surfaces a message. */
    fun onEdit(source: StreamSourceEntity, url: String, title: String?) = viewModelScope.launch {
        if (updateStreamSource(source, url, title) == UpdateStreamSourceUseCase.UpdateResult.InvalidUrl) {
            _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
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
        _state.update { it.copy(isImporting = true) }
        try {
            when (val result = importStreamCatalog()) {
                is ImportStreamCatalogUseCase.CatalogImportResult.Success -> {
                    // S0659: advance the refresh throttle on a completed sync so the policy back-offs apply.
                    sessionStore.writeCatalogRefreshAt(System.currentTimeMillis())
                    _events.send(StreamsEvent.CatalogUpdated(result.added, result.updated, result.removed))
                }
                ImportStreamCatalogUseCase.CatalogImportResult.Empty -> {
                    // A reachable-but-empty catalog still counts as a refresh; advance the throttle too.
                    sessionStore.writeCatalogRefreshAt(System.currentTimeMillis())
                    _events.send(StreamsEvent.Message(R.string.streams_catalog_empty))
                }
                is ImportStreamCatalogUseCase.CatalogImportResult.Failure ->
                    _events.send(StreamsEvent.Message(R.string.streams_error_network))
            }
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun onQueryChanged(query: String) {
        _filter.update { it.copy(query = query) }
        persistSession()
    }

    fun onFilter(
        category: String? = null,
        language: String? = null,
        mediaKind: MediaKindFilter = MediaKindFilter.ALL,
    ) {
        _filter.update { it.copy(category = category, language = language, mediaKind = mediaKind) }
        persistSession()
    }

    fun onSort(mode: SortMode) {
        _filter.update { it.copy(sort = mode) }
        persistSession()
    }

    /**
     * S0659: persist the user-chosen sort/media-filter/query so the next open restores them. Marks the
     * seed as applied so the async init seed can never overwrite a change the user just made. Only the
     * three remembered fields are written; facets are session-scoped and intentionally not persisted.
     */
    private fun persistSession() {
        initialFilterApplied = true
        val filter = _filter.value
        viewModelScope.launch {
            sessionStore.writeFilterState(
                sort = filter.sort.name,
                mediaFilter = filter.mediaKind.name,
                query = filter.query,
            )
        }
    }

    // S0659: bridge the persisted domain enums (StreamDefaultSort / StreamMediaTypeFilter) to the UI
    // enums the filter uses. Constant names are kept identical across the two enum families, but the
    // mapping is explicit (not name-based) so a future divergence is a compile error, not silent drift.

    private fun StreamDefaultSort.toSortMode(): SortMode = when (this) {
        StreamDefaultSort.NAME -> SortMode.NAME
        StreamDefaultSort.TOPIC -> SortMode.TOPIC
        StreamDefaultSort.LANGUAGE -> SortMode.LANGUAGE
        StreamDefaultSort.RECENT -> SortMode.RECENT
    }

    private fun StreamMediaTypeFilter.toMediaKind(): MediaKindFilter = when (this) {
        StreamMediaTypeFilter.ALL -> MediaKindFilter.ALL
        StreamMediaTypeFilter.AUDIO -> MediaKindFilter.AUDIO
        StreamMediaTypeFilter.VIDEO -> MediaKindFilter.VIDEO
    }

    // Decode a persisted last-session enum name back to the UI enum; unknown/legacy names fall through
    // to the caller's default via the null result.
    private fun String.toSortMode(): SortMode? = SortMode.values().firstOrNull { it.name == this }

    private fun String.toMediaKind(): MediaKindFilter? =
        MediaKindFilter.values().firstOrNull { it.name == this }

    /** S0637: resolve a home-screen shortcut URL to its source and ask the Activity to play it. */
    fun playByUrl(url: String) = viewModelScope.launch {
        val source = getStreamSourceByUrl(url)
        if (source != null) {
            _events.send(StreamsEvent.PlayRequested(source))
        } else {
            _events.send(StreamsEvent.Message(R.string.streams_shortcut_channel_missing))
        }
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
        // S0659: catalog-refresh throttles. ON_OPEN re-suggests at most every 6h; PERIODIC_WIFI
        // opportunistically auto-refreshes at most once a day (no WorkManager periodic job).
        private const val ON_OPEN_THROTTLE_MS = 6 * 60 * 60 * 1000L
        private const val PERIODIC_THROTTLE_MS = 24 * 60 * 60 * 1000L

        /**
         * Filters by case-insensitive query (title/topic/language substring) and the active facets, then
         * orders pinned-first followed by the chosen [SortMode]. Category, language and media-kind facets
         * are ANDed: each unset facet passes everything, so a separate ALL/ANY match-mode toggle is
         * redundant (selecting "All" on a facet already disables it). An active language facet now keeps
         * only rows whose language tokens explicitly contain that language, while rows without a language
         * stay visible only under "All". The media-kind facet folds VIDEO and RTSP transports into a
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
                val languageHit = filter.language == null ||
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
        data class PlayRequested(val source: StreamSourceEntity) : StreamsEvent

        /** S0659: ON_OPEN policy asks the Activity to surface a dismissible catalog-refresh suggestion. */
        data object SuggestCatalogRefresh : StreamsEvent
    }
}

/** Splits a catalog language cell (e.g. "russian,ukrainian") into trimmed, non-blank language tokens. */
private fun String?.tokens(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
