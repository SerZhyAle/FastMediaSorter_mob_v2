package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.data.repository.WearPhonePinsRepository
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamUsage
import com.sza.fastmediasorter.wear.domain.model.foldWearStreamIdentity
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import com.sza.fastmediasorter.wear.domain.usecase.ImportWearStreamCatalogUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.isVideoKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2149: how long the inputs must stand still before the catalogue is projected again.
 *
 * This is an input pause, not a frame budget: it exists so a burst of keystrokes or a run of filter
 * taps costs one projection of a nineteen-thousand-row catalogue instead of one per event.
 */
private const val PROJECTION_INPUT_PAUSE_MS = 150L

/**
 * S1708/S1871: ViewModel for the Wear OS streams list screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val importCatalogUseCase: ImportWearStreamCatalogUseCase,
    private val faviconAtlasStore: WearFaviconAtlasStore,
    private val preferencesRepository: WearPreferencesRepository,
    private val preparePlayback: PrepareWearStreamPlaybackUseCase,
    private val favoritesRepository: WearFavoritesRepository,
    private val phonePinsRepository: WearPhonePinsRepository,
    private val usageRepository: WearStreamUsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private val faviconSlicer = WearFaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    /**
     * S2149: everything the projection reads, as one value.
     *
     * Collapsing the inputs into a single flow is what allows exactly one consumer: before this, each
     * of the six setters projected the whole catalogue inline inside a state update, which put a
     * 19534-row filter and sort on the thread drawing the screen, once per keystroke.
     */
    private val projectionInputs = MutableStateFlow(ProjectionInputs())

    init {
        viewModelScope.launch {
            preferencesRepository.viewMode.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }

        // S2149: the one place the catalogue is projected. `mapLatest` abandons a projection whose
        // inputs are already stale rather than letting both finish and race to write the list, and
        // `displayChannels` is only ever replaced by a completed projection - never cleared while one
        // is running, so the previous list stays on screen instead of blinking empty between
        // keystrokes. That is deliberate: this ticket adds no "recomputing" indicator.
        viewModelScope.launch {
            projectionInputs
                .debounce(PROJECTION_INPUT_PAUSE_MS)
                .mapLatest { inputs ->
                    withContext(Dispatchers.Default) { computeDisplayChannels(inputs) }
                }
                .collect { display ->
                    Timber.d("S2146: projection sorted by ${projectionInputs.value.sortOrder} - ${display.size} rows")
                    Timber.d("S2149: projection ready - ${display.size} rows off the drawing thread")
                    _uiState.update { it.copy(displayChannels = display) }
                }
        }

        // S2149: the phone's pinned set arrives on its own schedule - it can land before the catalogue
        // on a cold start, or a day later when the watch comes back in range - so it is collected
        // rather than read once, and the projection is rebuilt from whichever of the two arrives.
        viewModelScope.launch {
            phonePinsRepository.observe().collect { identities ->
                _uiState.update { it.copy(phonePinnedIdentities = identities) }
                projectionInputs.update { it.copy(phonePinnedIdentities = identities) }
            }
        }

        // S2146: the stored selection is applied BEFORE the catalogue is observed, never beside it.
        // The two started in parallel would race, and the loser is visible: a catalogue that arrives
        // first is projected with the default order and then reshuffles under the wearer's eyes.
        viewModelScope.launch {
            restoreStoredSelection()
            observeCatalog()
        }
    }

    /**
     * S2146: seeds the screen from the four stored keys, per strategic §11 criterion 7.
     *
     * A stored enum name this build no longer knows falls back to the default instead of throwing.
     * `valueOf` would raise on a wearer's watch after an upgrade that renamed a constant - a place
     * where nobody can read the stack trace, and where the screen simply would not open.
     *
     * A stored facet the current catalogue does not contain is kept as the selection rather than
     * cleared. It then matches nothing, which is honest; clearing it would mean a catalogue that
     * failed to download silently erased the wearer's choice.
     */
    private suspend fun restoreStoredSelection() {
        // The store is allowed to fail without taking the screen with it. `DataStore.data` can raise
        // on a corrupt file, and this runs on the same coroutine that then observes the catalogue - so
        // an escaping throw would leave the list permanently empty, which is a far worse outcome than
        // opening on the default order. Recovery is the whole default selection.
        val stored = runCatching {
            StoredSelection(
                sortName = preferencesRepository.streamsSortOrderName.first(),
                kindName = preferencesRepository.streamsFilterKindName.first(),
                topic = preferencesRepository.streamsSelectedTopic.first(),
                language = preferencesRepository.streamsSelectedLanguage.first()
            )
        }.getOrElse { error ->
            // No ticket id here: this line is permanent, and Rule 2 reserves the `Sxxxx:` prefix for
            // the temporary probes so their removal grep cannot take a lasting log with it.
            Timber.w(error, "StreamsViewModel: stored selection unreadable - opening on defaults")
            StoredSelection()
        }

        val sortOrder = enumValues<StreamSortOrder>().firstOrNull { it.name == stored.sortName }
            ?: StreamSortOrder.MOST_USED
        val filterKind = enumValues<StreamFilterKind>().firstOrNull { it.name == stored.kindName }
            ?: StreamFilterKind.ALL
        val topic = stored.topic
        val language = stored.language

        Timber.d("S2146: restored selection sort=$sortOrder kind=$filterKind topic=$topic lang=$language")
        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                filterKind = filterKind,
                selectedTopic = topic,
                selectedLanguage = language
            )
        }
        projectionInputs.update {
            it.copy(
                sortOrder = sortOrder,
                filterKind = filterKind,
                selectedTopic = topic,
                selectedLanguage = language
            )
        }
    }

    private suspend fun observeCatalog() {
        repository.observeChannels().collect { channels ->
            // S1954: re-read the marks with the catalogue rather than per row. This is the only
            // point where the set can have changed without this screen being rebuilt.
            val pinned = loadPinnedStreamIds()
            val usage = usageRepository.usageByIdentity()
            // S2146: both facet lists are derived here, once per emission, never per row - the
            // counting walks the whole catalogue and strategic §7 forbids that on a scroll frame.
            val topics = deriveTopicFacets(channels)
            val languages = deriveLanguageFacets(channels)
            _uiState.update { state ->
                state.copy(
                    channels = channels,
                    availableTopics = topics,
                    availableLanguages = languages,
                    pinnedStreamIds = pinned
                )
            }
            projectionInputs.update {
                it.copy(channels = channels, pinnedIdentities = pinned, usageByIdentity = usage)
            }
            if (channels.isEmpty() && !_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                refreshCatalog(isInitial = true)
            }
        }
    }

    /**
     * S1954/S2146: re-reads both marks and play counts after the player may have changed them.
     *
     * The catalogue collector below is no longer the only writer: the video player marks a channel and
     * playing one counts it, neither of which makes the catalogue emit anything, so a screen returning
     * from the player would keep the order it was built with. Strategic §11 criterion 5 is exactly
     * this - a channel just played rises on return to the list, not at the next app start.
     *
     * Renamed off `refreshPinnedStreams` when the counter joined it: a name promising only pins would
     * have hidden the second read from the next reader of the resume path.
     */
    fun refreshPinsAndUsage() {
        Timber.d("S1954: streams screen re-reading pinned marks and play counts")
        viewModelScope.launch {
            val pinned = loadPinnedStreamIds()
            val usage = usageRepository.usageByIdentity()
            _uiState.update { it.copy(pinnedStreamIds = pinned) }
            projectionInputs.update { it.copy(pinnedIdentities = pinned, usageByIdentity = usage) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                showSearchDialog = false,
                // A query arriving is proof the input path answered, so any earlier refusal is stale.
                searchInputUnavailable = false,
            )
        }
        projectionInputs.update { it.copy(query = query) }
    }

    /** S1946: no text or speech input activity answered, and the user is owed that in words. */
    fun setSearchInputUnavailable() {
        _uiState.update { it.copy(searchInputUnavailable = true) }
    }

    // S2146: each of the four selection setters is also a writer. The search query deliberately is
    // not - strategic Non-goals keep this to filter and sort, and a restored query would empty the
    // list on a word the wearer cannot see.
    fun setFilterKind(kind: StreamFilterKind) {
        _uiState.update { it.copy(filterKind = kind, showFilterDialog = false) }
        projectionInputs.update { it.copy(filterKind = kind) }
        viewModelScope.launch { preferencesRepository.setStreamsFilterKindName(kind.name) }
    }

    fun setSelectedTopic(topic: String?) {
        Timber.d("S1947: setSelectedTopic topic=$topic")
        _uiState.update { it.copy(selectedTopic = topic, showFilterDialog = false) }
        projectionInputs.update { it.copy(selectedTopic = topic) }
        viewModelScope.launch { preferencesRepository.setStreamsSelectedTopic(topic) }
    }

    fun setSelectedLanguage(language: String?) {
        Timber.d("S1947: setSelectedLanguage language=$language")
        _uiState.update { it.copy(selectedLanguage = language, showFilterDialog = false) }
        projectionInputs.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch { preferencesRepository.setStreamsSelectedLanguage(language) }
    }

    fun setSortOrder(order: StreamSortOrder) {
        _uiState.update { it.copy(sortOrder = order, showSortDialog = false) }
        projectionInputs.update { it.copy(sortOrder = order) }
        viewModelScope.launch { preferencesRepository.setStreamsSortOrderName(order.name) }
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
     *
     * S2039: the stored path is normalized on the way out, because it is stored in whatever spelling
     * the writer used - an earlier build wrote the raw catalogue address. Comparing the raw form here
     * is what made a marked station silently never pin.
     */
    private suspend fun loadPinnedStreamIds(): Set<String> =
        favoritesRepository.getFavorites()
            .filter { it.sourceId == SOURCE_ID_STREAM }
            .mapTo(mutableSetOf()) { foldWearStreamIdentity(it.filePath) }
            .also { Timber.d("S2039: streams list pinned identities $it") }
}

/**
 * S2149: everything the projection reads, as one value.
 *
 * Collapsing the inputs into a single flow is what allows exactly one consumer: before this, each of
 * the six setters projected the whole catalogue inline inside a state update, which put a 19534-row
 * filter and sort on the thread drawing the screen, once per keystroke. Passing the value on rather
 * than unpacking it also keeps the projection inside detekt's parameter-list ceiling.
 */
internal data class ProjectionInputs(
    val channels: List<WearStreamChannel> = emptyList(),
    val query: String = "",
    val filterKind: StreamFilterKind = StreamFilterKind.ALL,
    val sortOrder: StreamSortOrder = StreamSortOrder.MOST_USED,
    val selectedTopic: String? = null,
    val selectedLanguage: String? = null,
    val pinnedIdentities: Set<String> = emptySet(),
    val phonePinnedIdentities: Set<String> = emptySet(),
    /**
     * S2146: the play counter, read once per catalogue emission and carried in as a ready map. A row
     * that looked its own count up would turn scrolling nineteen thousand rows into a store read per
     * frame, which strategic §7 names as the performance risk of this ticket.
     */
    val usageByIdentity: Map<String, WearStreamUsage> = emptyMap()
)

private fun sortChannels(
    channels: List<WearStreamChannel>,
    sortOrder: StreamSortOrder,
    usageByIdentity: Map<String, WearStreamUsage>
): List<WearStreamChannel> = when (sortOrder) {
    // S2146: three levels, no special case for the fresh install. An empty counter makes the first two
    // keys constant, so the comparator degrades exactly to NAME_ASC - which is strategic §11 criterion
    // 4. Recency second is ADR-5's answer to a mistap: it sinks as real use accumulates elsewhere.
    StreamSortOrder.MOST_USED -> channels.sortedByUsage(usageByIdentity)
    StreamSortOrder.NAME_ASC -> channels.sortedBy { it.name.lowercase() }
    StreamSortOrder.NAME_DESC -> channels.sortedByDescending { it.name.lowercase() }
    StreamSortOrder.KIND -> channels.sortedWith(
        compareBy<WearStreamChannel> { !it.isVideoKind() }.thenBy { it.name.lowercase() }
    )
}

/** S2146: the four stored keys as read, before any of them is parsed. Defaults mean "nothing stored". */
private data class StoredSelection(
    val sortName: String? = null,
    val kindName: String? = null,
    val topic: String? = null,
    val language: String? = null
)

/**
 * S2146: the languages this app is itself authored in, lifted above the rest of the language facet.
 *
 * Lowercase English names, because that is the spelling the catalogue's `language` column uses. Their
 * order here is the order they appear in - strategic §2 goal 3 asks for the three to lead, and leaves
 * which of the three leads unspecified, so the app's own authoring order is used rather than invented
 * ranking.
 */
private val INTERFACE_LANGUAGE_IDS = listOf("english", "russian", "ukrainian")

/** Position among [INTERFACE_LANGUAGE_IDS], or one past the end when the language is not one of them. */
private fun interfaceLanguageRank(id: String): Int {
    val index = INTERFACE_LANGUAGE_IDS.indexOfFirst { it.equals(id, ignoreCase = true) }
    return if (index < 0) INTERFACE_LANGUAGE_IDS.size else index
}

/**
 * S2146: the rubric facet, most populated first.
 *
 * Strategic §4 measured the harm the old `distinct().sorted()` did: the catalogue holds over two
 * thousand German rows against forty Arabic ones, and an alphabetical list opened on Arabic - which is
 * the "impossible to choose from" the owner reported.
 */
internal fun deriveTopicFacets(channels: List<WearStreamChannel>): List<StreamFacetValue> =
    channels.asSequence()
        .mapNotNull { it.topic?.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .map { (id, count) -> StreamFacetValue(id = id, channelCount = count) }
        .sortedWith(
            compareByDescending<StreamFacetValue> { it.channelCount }.thenBy { it.id.lowercase() }
        )

/**
 * S2146: the language facet, interface languages first and the rest by population.
 *
 * The comma split is not new - a catalogue cell may name several languages, and counting the raw cell
 * would invent a "english,german" facet nobody can pick. Splitting first means such a row counts once
 * towards each of its languages, which is also how the projection's filter matches it.
 */
internal fun deriveLanguageFacets(channels: List<WearStreamChannel>): List<StreamFacetValue> =
    channels.asSequence()
        .mapNotNull { it.language }
        .flatMap { it.split(",").asSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .map { (id, count) -> StreamFacetValue(id = id, channelCount = count) }
        .sortedWith(
            compareBy<StreamFacetValue> { interfaceLanguageRank(it.id) }
                .thenByDescending { it.channelCount }
                .thenBy { it.id.lowercase() }
        )

/**
 * S2146: a channel with its ordering keys already resolved.
 *
 * The keys are read once per row rather than from inside the comparator, because a comparator's
 * selector is evaluated per COMPARISON: folding the identity there would run [foldWearStreamIdentity],
 * which parses a URI, some n log n times - about half a million parses over the catalogue's nineteen
 * thousand rows, per projection. Strategic §11 criterion 9 forbids exactly that, and the per-row
 * reading it asks for is what this decoration is.
 */
private class UsageRanked(
    val channel: WearStreamChannel,
    val playCount: Int,
    val lastPlayedAt: Long,
    val name: String
)

private fun List<WearStreamChannel>.sortedByUsage(
    usageByIdentity: Map<String, WearStreamUsage>
): List<WearStreamChannel> = map { channel ->
    val usage = usageByIdentity[foldWearStreamIdentity(channel.url)]
    UsageRanked(
        channel = channel,
        playCount = usage?.playCount ?: 0,
        lastPlayedAt = usage?.lastPlayedAt ?: 0L,
        name = channel.name.lowercase()
    )
}.sortedWith(
    compareByDescending<UsageRanked> { it.playCount }
        .thenByDescending { it.lastPlayedAt }
        .thenBy { it.name }
).map { it.channel }

internal fun computeDisplayChannels(inputs: ProjectionInputs): List<WearStreamChannel> {
    val query = inputs.query
    val selectedTopic = inputs.selectedTopic
    val selectedLanguage = inputs.selectedLanguage
    var result = inputs.channels

    if (query.isNotBlank()) {
        val trimmed = query.trim()
        result = result.filter { ch ->
            ch.name.contains(trimmed, ignoreCase = true) ||
                (ch.category?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.topic?.contains(trimmed, ignoreCase = true) == true) ||
                (ch.language?.contains(trimmed, ignoreCase = true) == true)
        }
    }

    result = when (inputs.filterKind) {
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

    result = sortChannels(result, inputs.sortOrder, inputs.usageByIdentity)

    if (inputs.pinnedIdentities.isEmpty() && inputs.phonePinnedIdentities.isEmpty()) {
        return result
    }
    // S1954: partition last and by address, not row id. Pinning is a second ordering key applied over
    // whatever the filter and sort already decided, so both groups keep the order chosen above, and a
    // catalogue re-import that renumbers every row leaves the marks where they were.
    // S2149: the top group is the union of the two sources - marks made on this watch and pins that
    // arrived from the phone. They stay separate sets up to here so the phone can withdraw only its
    // own, and a channel named by both still appears once because a partition yields each row once.
    val topGroup = inputs.pinnedIdentities + inputs.phonePinnedIdentities
    val (pinned, unpinned) = result.partition { foldWearStreamIdentity(it.url) in topGroup }
    return pinned + unpinned
}
