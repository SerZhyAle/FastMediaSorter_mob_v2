package com.sza.fastmediasorter.ui.streams

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.DefaultDispatcher
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.settings.StreamsSessionStore
import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.model.StreamMediaTypeFilter
import com.sza.fastmediasorter.domain.model.StreamResumeState
import com.sza.fastmediasorter.domain.model.StreamsCatalogRefreshPolicy
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StreamResumeStateRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.SendStreamToWatchUseCase
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ClearDownloadedStreamsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.GetStreamSourceByUrlUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamPlaylistUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamPlayOutcomesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinnedStreamMove
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ReorderPinnedStreamUseCase
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UnpinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UpdateStreamSourceUseCase
import com.sza.fastmediasorter.ui.streams.helpers.StreamTopicLabelProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
// Pre-existing large state holder (already over the param threshold); S0712 adds the persistent
// frame store as one more injected dependency. Kept whole - each dep is a distinct stream use case.
@Suppress("LongParameterList")
@HiltViewModel
class StreamsViewModel @Inject constructor(
    observeStreamSources: ObserveStreamSourcesUseCase,
    private val addStreamSource: AddStreamSourceUseCase,
    private val updateStreamSource: UpdateStreamSourceUseCase,
    private val importStreamPlaylist: ImportStreamPlaylistUseCase,
    private val importStreamCatalog: ImportStreamCatalogUseCase,
    private val pinStreamSource: PinStreamSourceUseCase,
    private val unpinStreamSource: UnpinStreamSourceUseCase,
    // S0938: relative reorder of a pinned channel (up / down / to top) within the pinned set.
    private val reorderPinnedStream: ReorderPinnedStreamUseCase,
    private val removeStreamSource: RemoveStreamSourceUseCase,
    private val clearDownloadedStreams: ClearDownloadedStreamsUseCase,
    private val recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase,
    // S1502: outcomes arrive on their own table-scoped Flow, beside the catalog rather than inside it.
    observeStreamPlayOutcomes: ObserveStreamPlayOutcomesUseCase,
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase,
    // S0783: shared Favorites - add/remove a channel and observe which channels are favorited.
    private val favoritesUseCase: FavoritesUseCase,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: StreamsSessionStore,
    // S0659: same synchronous Wi-Fi/unmetered check that backs searchAudioCoversOnlyOnWifi -
    // injected so the PERIODIC_WIFI policy never touches ConnectivityManager from the ViewModel.
    private val networkContextAnalyzer: NetworkContextAnalyzer,
    // S0712: invalidate a channel's persisted last-frame thumbnail when it is removed.
    private val streamFramePersistentStore: StreamFramePersistentStore,
    // S1144: per-channel audio/subtitle preference, edited from the add/edit channel dialog.
    private val streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase,
    // S1152: persists the last active stream so the next cold start can resume it (mirrors media resume).
    private val streamResumeStateRepository: StreamResumeStateRepository,
    // S1152: the exit clear must outlive this ViewModel - viewModelScope is already cancelled by the time
    // the host tears down, so a clear launched there would never reach the prefs.
    @ApplicationScope private val applicationScope: CoroutineScope,
    // S1477: resolves a catalog rubric to its localized label, so rubric sorting follows the alphabet
    // the user sees rather than the catalog's English ids.
    private val topicLabelProvider: StreamTopicLabelProvider,
    // S1502: the catalog pass runs here, not on the main thread. Injected rather than hardcoded so
    // the dispatcher can be swapped for a deterministic one in a test.
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    // S1799: gates the per-channel "Send to watch" command on the build's watch bridge (Rule 14
    // surface - BuildConfig is never read here).
    private val mediaCapabilities: MediaCapabilities,
    // S1799: Lazy - the send path is cold until the user actually invokes the command (Rule 18).
    private val sendStreamToWatchUseCase: dagger.Lazy<SendStreamToWatchUseCase>,
) : ViewModel() {

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    // S0577: the streams screen reads the background-playback gate and exit preference to mirror the
    // player's behavior. Eager so `.value` is current when the Activity decides the playback path.
    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // S0783: URLs of favorited channels, so the per-channel overflow can label its action add vs remove.
    // Eager so `.value` is current when the Activity pushes the state into the adapters.
    val favoriteStreamIdentities: StateFlow<Set<String>> = favoritesUseCase.observeFavoriteStreamIdentities()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * S1842: whether this channel is favourited. Matching is by channel identity, so a catalog that
     * re-published the channel under a cosmetically different address no longer darkens the star.
     * Call sites ask this instead of comparing the raw URL themselves.
     */
    fun isFavoriteChannel(source: StreamSourceEntity): Boolean =
        favoriteStreamIdentities.value.contains(favoritesUseCase.channelIdentity(source))

    /**
     * S1799: the canonical companion gate - the user's setting AND the build's watch bridge
     * (MainActivity ADR-1 shape). Either alone would offer a command that cannot run.
     */
    fun isWearSendAvailable(): Boolean =
        settings.value.enableWearCompanion && mediaCapabilities.supportsWearCompanion

    /** S1799: sends one channel to the watch and reports the outcome as a one-shot message. */
    fun sendStreamToWatch(source: StreamSourceEntity) {
        Timber.d("S1799: send to watch requested for ${source.url}")
        viewModelScope.launch {
            val outcome = sendStreamToWatchUseCase.get()(source.title, source.url, source.mediaKind)
            _events.send(StreamsEvent.Message(outcome.toMessageRes()))
        }
    }

    @StringRes
    private fun SendStreamToWatchUseCase.Outcome.toMessageRes(): Int = when (this) {
        is SendStreamToWatchUseCase.Outcome.Delivered ->
            if (updated) R.string.stream_send_to_watch_updated else R.string.stream_send_to_watch_done

        SendStreamToWatchUseCase.Outcome.WatchUnavailable ->
            R.string.stream_send_to_watch_watch_unavailable

        SendStreamToWatchUseCase.Outcome.NoReply -> R.string.stream_send_to_watch_no_reply
        is SendStreamToWatchUseCase.Outcome.Error -> R.string.stream_send_to_watch_failed
    }

    // S1502: play outcome per channel id. Deliberately NOT part of StreamsUiState - folding it into
    // the combined state would put a per-probe signal back on the per-keystroke catalog pass that
    // Phases 02 and 03 just made cheap. Eager, mirroring favoriteStreamIdentities above.
    val playOutcomes: StateFlow<Map<String, String>> = observeStreamPlayOutcomes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    // S1476: facets depend only on the catalog, never on the filter, but the combine below re-runs on
    // every filter change - which is every keystroke in the search box. Recomputing them there means
    // four full passes with distinct+sort over the whole catalog per keystroke; at the catalog's
    // current size that is tens of thousands of rows of work for a result that cannot have changed.
    // The source list is an immutable snapshot from Room, so identity is a sound cache key.
    private var facetsSourceSnapshot: List<StreamSourceEntity>? = null
    private var facetsCache: StreamsFacets = StreamsFacets()

    private fun cachedFacetsOf(sources: List<StreamSourceEntity>): StreamsFacets {
        if (facetsSourceSnapshot === sources) return facetsCache
        val facets = facetsOf(sources)
        facetsSourceSnapshot = sources
        facetsCache = facets
        return facets
    }

    init {
        // S0659: restore the last session before the combine renders, falling back to the user defaults.
        // Applied once so a fast user interaction during the async DataStore read is never clobbered.
        viewModelScope.launch { seedInitialFilter() }

        // S1502: the transform below walks the whole catalog - filter, then partition, then sort -
        // and it re-runs on every keystroke. Measured on a 19,855-row catalog before this change,
        // typing four characters produced 21% janky frames, because viewModelScope collects on
        // Dispatchers.Main.immediate and the pass therefore competed with drawing. flowOn moves the
        // transform to the background; onEach and the _state write stay on the collector's context.
        combine(observeStreamSources(), _filter) { sources, filter ->
            val filtered = applyFilter(sources, filter, topicLabelProvider::label)
            StreamsUiState(
                // The only join of the two halves: consumers that need the flat list read this, and the
                // ones that need the split read `pinned` / `unpinned` instead of partitioning it again.
                sources = filtered.pinned + filtered.unpinned,
                pinned = filtered.pinned,
                unpinned = filtered.unpinned,
                filter = filter,
                facets = cachedFacetsOf(sources),
                isLoading = false,
            )
        }
            .flowOn(defaultDispatcher)
            .onEach { newState ->
                _state.update { newState.copy(isImporting = it.isImporting, displayMode = it.displayMode) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * S0659: seed [_filter] from the persisted last session, else from the user defaults. Only sort and
     * media-kind come from the user defaults; the catalog-derived facets are restored below (S0697).
     * Skips if the user already changed the filter while the read was in flight.
     *
     * S1054: the free-text query is deliberately NOT restored - it stays empty on every open so the search
     * field and the applied text filter can never diverge (empty field + already-filtered list).
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
            // S1054: query intentionally omitted - starts empty each open (default StreamsFilter().query).
            // S0697: restore the facet selections + pinned-only toggle too. A restored facet value that no
            // longer exists in the catalog simply yields an empty list with the filter shown active, so the
            // user can clear it - no crash, no silent wrong data.
            category = session.lastCategory,
            topic = session.lastTopic,
            language = session.lastLanguage,
            country = session.lastCountry,
            pinnedOnly = session.lastPinnedOnly ?: false,
        )
        val restoredMode = session.lastDisplayMode?.toDisplayMode() ?: DisplayMode.LIST
        _state.update { it.copy(displayMode = restoredMode) }
        // S0699: ask the Activity to land on the remembered position once the list renders. A buffered
        // event survives init ordering, so it is delivered whether the read finishes before or after the
        // first list emission; the Activity applies it once the row at that position exists.
        session.lastScrollPosition?.let { _events.send(StreamsEvent.RestoreScroll(it)) }
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

    /**
     * S1147: add a manual channel. Invalid-url and duplicate-url surface a one-shot message; success
     * stays silent (Room re-renders the list). The duplicate guard replaces the former unhandled
     * SQLiteConstraintException crash when the url already exists.
     */
    fun onAdd(url: String, title: String?) = viewModelScope.launch {
        when (addStreamSource(url, title)) {
            AddStreamSourceUseCase.AddResult.InvalidUrl ->
                _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
            AddStreamSourceUseCase.AddResult.Duplicate ->
                _events.send(StreamsEvent.Message(R.string.streams_error_duplicate_url))
            AddStreamSourceUseCase.AddResult.Success -> Unit
        }
    }

    /**
     * S0660/S1145: persist an in-place edit of a manual channel. [mediaKindOverride] carries an explicit
     * type (null = auto-derive). Invalid-url and duplicate-url surface a one-shot message; success and
     * not-editable stay silent (the list re-renders itself via Room on success).
     */
    fun onEdit(source: StreamSourceEntity, url: String, title: String?, mediaKindOverride: String? = null) =
        viewModelScope.launch {
            when (updateStreamSource(source, url, title, mediaKindOverride)) {
                UpdateStreamSourceUseCase.UpdateResult.InvalidUrl ->
                    _events.send(StreamsEvent.Message(R.string.streams_error_invalid_url))
                UpdateStreamSourceUseCase.UpdateResult.Duplicate ->
                    _events.send(StreamsEvent.Message(R.string.streams_error_duplicate_url))
                UpdateStreamSourceUseCase.UpdateResult.Success,
                UpdateStreamSourceUseCase.UpdateResult.NotEditable -> Unit
            }
        }

    /** S1144: stored track preference for [url], null when the channel has none. */
    suspend fun readTrackPreference(url: String): StreamTrackPreferenceUseCase.TrackPreference? =
        streamTrackPreferenceUseCase.read(url)

    /** S1144: persist the audio/subtitle picks the channel dialog produced. */
    fun writeTrackPreference(
        url: String,
        audioIso: String?,
        subtitleIso: String?,
        subtitlesEnabled: Boolean?,
    ) {
        viewModelScope.launch {
            streamTrackPreferenceUseCase.writeAudio(url, audioIso)
            streamTrackPreferenceUseCase.writeSubtitle(url, subtitleIso, subtitlesEnabled)
        }
    }

    /**
     * S1152: record this radio station as the last active stream, so a cold start resumes it. Only
     * AUDIO is ever recorded: video playback does not survive the process anyway, and recording it
     * made every later launch reopen this screen for 48 h (owner report 2026-07-26).
     */
    fun persistStreamResume(source: StreamSourceEntity) {
        if (source.mediaKind != "AUDIO") return
        viewModelScope.launch {
            streamResumeStateRepository.save(
                StreamResumeState(
                    url = source.url,
                    title = source.title,
                    mediaKind = source.mediaKind,
                    wasPlaying = true,
                    savedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** S1152: drop the resume record when the user explicitly stops the current stream. */
    fun clearStreamResume() {
        viewModelScope.launch { streamResumeStateRepository.clear() }
    }

    /**
     * S1152: leaving the screen without anything still playing means there is nothing to resume - drop
     * the record so the next cold start opens the normal main screen. Runs on the application scope
     * because viewModelScope is already cancelled by the time the host is torn down.
     */
    fun clearStreamResumeOnExit() {
        applicationScope.launch { streamResumeStateRepository.clear() }
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
    /**
     * S1780: drops every downloaded channel, keeping the hand-added ones.
     *
     * The confirmation belongs to the screen, not here - by the time this runs the user has already
     * agreed, and a ViewModel that re-asked would put the question in two places.
     */
    fun onClearDownloaded() = viewModelScope.launch {
        val removed = clearDownloadedStreams()
        _events.send(StreamsEvent.DownloadedCleared(removed))
    }

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
        topic: String? = null,
        language: String? = null,
        country: String? = null,
        mediaKind: MediaKindFilter = MediaKindFilter.ALL,
        pinnedOnly: Boolean = false,
    ) {
        val previousKind = _filter.value.mediaKind
        _filter.update {
            it.copy(
                category = category,
                topic = topic,
                language = language,
                country = country,
                mediaKind = mediaKind,
                pinnedOnly = pinnedOnly,
            )
        }
        afterFilterApplied(previousKind, mediaKind)
    }

    /**
     * S1473: the tail every filter change shares - the video display switch, then the session write.
     * Both entry points run it so a single-facet change cannot drift away from the dialog path.
     */
    private fun afterFilterApplied(previousKind: MediaKindFilter, newKind: MediaKindFilter) {
        applyVideoFilterDisplayMode(previousKind, newKind)
        persistSession()
    }

    /**
     * S1473: change only the media-kind facet, carrying every other facet over untouched.
     *
     * [onFilter] defaults each unpassed facet, so calling it from the inline trigger would silently
     * clear category, topic, language, country and the pinned-only flag. No default value here - a
     * defaulted single-facet setter is exactly how that trap was built.
     */
    fun onMediaKindFilter(mediaKind: MediaKindFilter) {
        val previousKind = _filter.value.mediaKind
        if (previousKind == mediaKind) return
        _filter.update { it.copy(mediaKind = mediaKind) }
        afterFilterApplied(previousKind, mediaKind)
    }

    // S1154: video previews are only meaningful in GRID, so entering the VIDEO filter auto-switches to
    // GRID and remembers the prior mode; leaving VIDEO restores it. The auto mode is never persisted as
    // the user's default (only onToggleDisplayMode writes it), so a manual LIST choice is never lost.
    private var modeBeforeVideoFilter: DisplayMode? = null

    private fun applyVideoFilterDisplayMode(previousKind: MediaKindFilter, newKind: MediaKindFilter) {
        val enteringVideo = newKind == MediaKindFilter.VIDEO && previousKind != MediaKindFilter.VIDEO
        val leavingVideo = previousKind == MediaKindFilter.VIDEO && newKind != MediaKindFilter.VIDEO
        when {
            enteringVideo -> {
                modeBeforeVideoFilter = _state.value.displayMode
                if (_state.value.displayMode != DisplayMode.GRID) {
                    _state.update { it.copy(displayMode = DisplayMode.GRID) }
                }
            }
            leavingVideo -> {
                modeBeforeVideoFilter?.let { restore -> _state.update { it.copy(displayMode = restore) } }
                modeBeforeVideoFilter = null
            }
        }
    }

    fun onSort(mode: SortMode) {
        _filter.update { it.copy(sort = mode) }
        persistSession()
    }

    /** S0675: flip list<->grid display mode, emit it, and persist the new mode for the next screen open. */
    fun onToggleDisplayMode() {
        val newMode = if (_state.value.displayMode == DisplayMode.GRID) DisplayMode.LIST else DisplayMode.GRID
        _state.update { it.copy(displayMode = newMode) }
        // S1154: a deliberate switch while the VIDEO filter is active becomes the new restore baseline,
        // so leaving the filter does not clobber the in-filter choice with the pre-video mode.
        if (_filter.value.mediaKind == MediaKindFilter.VIDEO && modeBeforeVideoFilter != null) {
            modeBeforeVideoFilter = newMode
        }
        viewModelScope.launch { sessionStore.writeDisplayMode(newMode.name) }
    }

    /**
     * S0659: persist the user-chosen sort/media-filter (plus facets) so the next open restores them. Marks
     * the seed as applied so the async init seed can never overwrite a change the user just made.
     * S1054: the free-text query is not written - it is a one-shot input that resets on every open.
     */
    private fun persistSession() {
        initialFilterApplied = true
        val filter = _filter.value
        viewModelScope.launch {
            sessionStore.writeFilterState(
                sort = filter.sort.name,
                mediaFilter = filter.mediaKind.name,
                category = filter.category,
                topic = filter.topic,
                language = filter.language,
                country = filter.country,
                pinnedOnly = filter.pinnedOnly,
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
        StreamDefaultSort.COUNTRY -> SortMode.COUNTRY
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

    // Decode a persisted DisplayMode name; an unknown/legacy name yields null so the caller falls back to LIST.
    private fun String.toDisplayMode(): DisplayMode? =
        DisplayMode.values().firstOrNull { it.name == this }

    /** S0783: add or remove the channel from the shared Favorites (independent of pin). */
    fun toggleStreamFavorite(source: StreamSourceEntity) = viewModelScope.launch {
        favoritesUseCase.toggleStreamFavorite(source)
    }

    /** S0637: resolve a home-screen shortcut URL to its source and ask the Activity to play it. */
    fun playByUrl(url: String) = viewModelScope.launch {
        val source = getStreamSourceByUrl(url)
        if (source != null) {
            _events.send(StreamsEvent.PlayRequested(source))
        } else {
            _events.send(StreamsEvent.Message(R.string.streams_shortcut_channel_missing))
        }
    }

    /**
     * S0711: cheap synchronous snapshot of whether any network transport is active. A stream cannot
     * be reached with no Wi-Fi/cellular/ethernet, so the play path consults this to refuse fast
     * instead of letting ExoPlayer/the fullscreen player spin until a connection timeout.
     */
    fun hasNetworkForStream(): Boolean = networkContextAnalyzer.hasAnyNetwork()

    /** S0699: remember the user's current list position so the next screen open lands on the same channel. */
    fun onScrollPositionChanged(position: Int) = viewModelScope.launch {
        if (position >= 0) sessionStore.writeScrollPosition(position)
    }

    // S0695: the pin affordance is a toggle - re-tapping a pinned channel unpins it (the icon already
    // flips filled/outline on rebind, so the gesture is self-evident). Without this a pinned row could
    // only be unpinned via the overflow menu, contradicting the documented long-press toggle.
    fun onPin(source: StreamSourceEntity) = viewModelScope.launch {
        if (source.pinned) unpinStreamSource(source.id) else pinStreamSource(source.id)
    }

    /** S0938: move a pinned channel within the pinned set; the ordered DAO queries re-emit the new order. */
    fun onMovePinned(source: StreamSourceEntity, move: PinnedStreamMove) = viewModelScope.launch {
        reorderPinnedStream(source.id, move)
    }

    fun onRemove(source: StreamSourceEntity) = viewModelScope.launch {
        removeStreamSource(source)
        // S0712: drop the channel's persisted last-frame thumbnail so removed channels leave no orphan.
        streamFramePersistentStore.remove(source.url)
    }

    /** S0593: the inline-audio stream reached playback - green bullet, and counted as a play. */
    fun recordStreamPlaySuccess(id: String) =
        viewModelScope.launch { recordStreamPlayOutcome.recordPlaySuccess(id) }

    /** S0700: record a reachability-probe / grid-capture outcome - reachable -> green, else amber (not red). */
    fun recordStreamProbeOutcome(id: String, reachable: Boolean) =
        viewModelScope.launch { recordStreamPlayOutcome.recordProbe(id, reachable) }

    /**
     * S1509: record a terminal inline-audio failure, charging it to the channel only when the device
     * had a network. [hasNetwork] is passed in rather than sampled here so the row bullet and the
     * dialog the caller raises describe the same instant.
     */
    fun recordStreamPlayFailure(id: String, hasNetwork: Boolean) =
        viewModelScope.launch { recordStreamPlayOutcome.recordPlayFailure(id, hasNetwork) }

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
         *
         * S1502: returns the two halves separately rather than concatenated. This is the only place the
         * catalog is partitioned - every downstream consumer reads the halves off the state instead of
         * walking all ~20k rows again to re-derive a split that was already computed here.
         */
        internal fun applyFilter(
            sources: List<StreamSourceEntity>,
            filter: StreamsFilter,
            // S1477: rubric sorting orders by the LABEL the user reads, not by the catalog's English id -
            // otherwise "По рубрике" lists Russian names in English alphabetical order, which reads as
            // no sorting at all. Identity by default so the pure filter stays testable without a Context.
            topicLabel: (String?) -> String? = { it },
        ): FilteredStreams {
            // S1502: the query is trimmed but NOT lowercased - matching folds case per comparison instead,
            // so neither side allocates a lowercased copy per catalog row on every keystroke.
            val query = filter.query.trim()
            val matched = sources.filter { source -> matchesFacets(source, filter, query) }
            val secondary: Comparator<StreamSourceEntity> = when (filter.sort) {
                SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                SortMode.TOPIC -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { topicLabel(it.topic) }
                SortMode.LANGUAGE -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.language }
                SortMode.COUNTRY -> compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.country }
                SortMode.RECENT -> compareByDescending { it.addedAt }
            }
            // S0938: pinned rows keep their manual order (the incoming list is already sortIndex-ordered
            // within pinned by the DAO), so the reorder menu commands are visible here; only the unpinned
            // catalog rows follow the chosen secondary sort. `partition` preserves the input order.
            val (pinned, unpinned) = matched.partition { it.pinned }
            return FilteredStreams(pinned = pinned, unpinned = unpinned.sortedWith(secondary))
        }

        /**
         * One row against every active facet, ANDed. Split out of [applyFilter] so each half stays
         * within the complexity budget: this is the facet logic, [applyFilter] is the ordering.
         * `query` arrives pre-trimmed but in the user's original case - it is the same for every row,
         * and each comparison folds case itself rather than allocating a lowercased copy of the row.
         */
        private fun matchesFacets(
            source: StreamSourceEntity,
            filter: StreamsFilter,
            query: String,
        ): Boolean {
            val queryHit = query.isEmpty() ||
                source.title.contains(query, ignoreCase = true) ||
                source.topic?.contains(query, ignoreCase = true) == true ||
                source.language?.contains(query, ignoreCase = true) == true
            val categoryHit = filter.category == null || source.category == filter.category
            val languageHit = filter.language == null ||
                source.language.tokens().any { it.equals(filter.language, ignoreCase = true) }
            // Country is a single code, so a plain equality (like category), not token matching.
            val countryHit = filter.country == null || source.country == filter.country
            // mediaKind values are the StreamSourceEntity contract ("AUDIO" / "VIDEO" / "RTSP").
            val mediaHit = when (filter.mediaKind) {
                MediaKindFilter.ALL -> true
                MediaKindFilter.AUDIO -> source.mediaKind == "AUDIO"
                // RTSP is a video transport, so it shares the "video" bucket.
                MediaKindFilter.VIDEO -> source.mediaKind == "VIDEO" || source.mediaKind == "RTSP"
            }
            val topicHit = filter.topic == null || source.topic == filter.topic
            // S0696: pinned-only keeps just the user-pinned rows when the facet is on.
            val pinnedHit = !filter.pinnedOnly || source.pinned
            return queryHit && categoryHit && languageHit && countryHit && mediaHit && topicHit && pinnedHit
        }

        internal fun facetsOf(sources: List<StreamSourceEntity>): StreamsFacets {
            val facets = StreamsFacets(
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
                // Country is a single ISO 3166-1 alpha-2 code per row (never comma-split, unlike language).
                countries = sources.mapNotNull { it.country?.takeIf(String::isNotBlank) }.distinct().sorted(),
            )
            return facets
        }
    }

    /**
     * S1502: the filter's two halves, kept apart all the way to the adapters. Concatenating them is a
     * one-line join at the single point that needs a flat list; re-deriving the split by partitioning
     * that flat list is a full catalog walk, which is what this type exists to stop.
     */
    internal data class FilteredStreams(
        val pinned: List<StreamSourceEntity>,
        val unpinned: List<StreamSourceEntity>,
    )

    data class StreamsUiState(
        val sources: List<StreamSourceEntity> = emptyList(),
        // S1502: the pinned/unpinned split as applyFilter computed it - never re-derived from `sources`.
        val pinned: List<StreamSourceEntity> = emptyList(),
        val unpinned: List<StreamSourceEntity> = emptyList(),
        val filter: StreamsFilter = StreamsFilter(),
        val facets: StreamsFacets = StreamsFacets(),
        val isLoading: Boolean = true,
        val isImporting: Boolean = false,
        val displayMode: DisplayMode = DisplayMode.LIST,
    ) {
        val isEmpty: Boolean get() = !isLoading && sources.isEmpty()
    }

    /** Distinct facet values present in the catalog, surfaced for the filter UI. */
    data class StreamsFacets(
        val categories: List<String> = emptyList(),
        val topics: List<String> = emptyList(),
        val languages: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
    )

    data class StreamsFilter(
        val query: String = "",
        val category: String? = null,
        val topic: String? = null,
        val language: String? = null,
        val country: String? = null,
        val mediaKind: MediaKindFilter = MediaKindFilter.ALL,
        // S0696: when true, keep only the streams the user personally pinned.
        val pinnedOnly: Boolean = false,
        val sort: SortMode = SortMode.NAME,
    )

    enum class SortMode { NAME, TOPIC, LANGUAGE, COUNTRY, RECENT }

    /** Media-kind facet: ALL passes everything, AUDIO matches audio rows, VIDEO matches VIDEO + RTSP rows. */
    enum class MediaKindFilter { ALL, AUDIO, VIDEO }

    sealed interface StreamsEvent {
        data class Message(@StringRes val messageResId: Int) : StreamsEvent
        data class ImportFinished(val inserted: Int) : StreamsEvent

        /** S1780: how many downloaded channels the clear actually removed. */
        data class DownloadedCleared(val removed: Int) : StreamsEvent
        data class CatalogUpdated(val added: Int, val updated: Int, val removed: Int) : StreamsEvent
        data class PlayRequested(val source: StreamSourceEntity) : StreamsEvent

        /** S0699: ask the Activity to restore the saved list position once the row at [position] exists. */
        data class RestoreScroll(val position: Int) : StreamsEvent

        /** S0659: ON_OPEN policy asks the Activity to surface a dismissible catalog-refresh suggestion. */
        data object SuggestCatalogRefresh : StreamsEvent
    }
}

/** Splits a catalog language cell (e.g. "russian,ukrainian") into trimmed, non-blank language tokens. */
private fun String?.tokens(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
