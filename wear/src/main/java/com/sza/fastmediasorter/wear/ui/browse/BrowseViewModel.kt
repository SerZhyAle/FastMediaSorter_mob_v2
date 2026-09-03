package com.sza.fastmediasorter.wear.ui.browse

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.network.WearNetworkDataSources
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.browse.BrowseListProjection
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineKeys
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineRestore
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineState
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.NetworkBasePath
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.model.asContentType
import com.sza.fastmediasorter.wear.domain.model.contentTypeForMime
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import com.sza.fastmediasorter.wear.ui.common.BrowseCategoryPresentation
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val VIEW_MODE_SUBSCRIPTION_MS = 5_000L

/**
 * ViewModel for the browse screen.
 * Handles loading media files from both local MediaStore and SMB network sources.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val networkDataSources: WearNetworkDataSources,
    private val networkSourceRepository: NetworkSourceRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val thumbnailRepository: WearThumbnailRepository,
    /** S2444: owns the selection and the file-operation batch - see [BrowseFileOperationsManager]. */
    val fileOperations: BrowseFileOperationsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /**
     * S2136: the list exactly as the source returned it, never narrowed.
     *
     * Private because [uiState] publishes the projection of it and ADR-6 makes that substitution the
     * whole point: every existing reader of `Success.files` - the player's paging set, "select all",
     * the allowed-operation intersection - must follow what the wearer can actually see. Keeping the
     * full list here rather than beside the projected one is what lets a cleared query re-project in
     * memory instead of walking the folder or the network again.
     */
    private var loadedFiles: List<WearMediaFile> = emptyList()

    private val _refineState = MutableStateFlow(BrowseRefineState())
    val refineState: StateFlow<BrowseRefineState> = _refineState.asStateFlow()

    private val _selectedFile = MutableStateFlow<WearMediaFile?>(null)
    val selectedFile: StateFlow<WearMediaFile?> = _selectedFile.asStateFlow()

    /**
     * S2444: the files on screen right now, which is the only slice of [uiState] the operations
     * helper needs.
     *
     * Started eagerly, unlike the subscription-scoped flows below: `selectAll` and `runOperation`
     * read `.value` synchronously off a tap, and under `WhileSubscribed` that would answer with the
     * initial empty list whenever nothing happened to be collecting.
     */
    private val displayedFiles: StateFlow<List<WearMediaFile>> = _uiState
        .map { (it as? BrowseUiState.Success)?.files.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The file list's own stored view, separate from the navigation screens' mode. */
    val fileListViewMode: StateFlow<WearViewMode> = preferencesRepository.fileListViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    private val _thumbnails = MutableStateFlow<Map<Long, WearThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, WearThumbnail>> = _thumbnails.asStateFlow()

    // Navigation arguments - to be set from UI layer
    private var _mediaType: MediaType = MediaType.MUSIC
    private var _sourceId: String? = null
    private var _sourceName: String? = null

    /**
     * S2130: the category the route asked for, which is what [_mediaType] cannot express.
     *
     * Documents, "all" and "recents" are categories with no [MediaType] of their own - the first has
     * no typed MediaStore collection and the other two are ways of looking at every type at once.
     * Before this the route argument was collapsed into a [MediaType] on the way in, so those three
     * were indistinguishable from a music request by the time the load ran.
     */
    private var _categoryToken: String? = null

    val mediaType: MediaType get() = _mediaType
    val isNetworkSource: Boolean get() = _sourceId != null
    val sourceName: String? get() = _sourceName

    /**
     * Initialize navigation arguments. Call this from the composable.
     */
    fun setNavigationArgs(
        mediaType: MediaType,
        sourceId: String? = null,
        sourceName: String? = null,
        categoryToken: String? = null
    ) {
        _mediaType = mediaType
        _sourceId = sourceId
        _sourceName = sourceName
        _categoryToken = categoryToken
    }

    private val refineRestore = BrowseRefineRestore()

    init {
        Timber.d("BrowseViewModel initialized")
        // S2444: the helper's four missing pieces, none of which exists before the constructor has
        // run. `isNetworkSource` is passed as a lambda rather than a value because the route sets it
        // later, in setNavigationArgs.
        fileOperations.bind(
            scope = viewModelScope,
            displayedFiles = displayedFiles,
            isNetworkSource = { isNetworkSource },
            onListInvalidated = ::loadMediaFiles
        )
        // loadMediaFiles() will be called after setNavigationArgs() from UI
        viewModelScope.launch {
            // S2199: the order applies at once because it needs no list. The type filter cannot:
            // which types exist is a property of what this route loaded, so it waits for the load.
            // Assigned straight to the state rather than through updateRefine, which would write the
            // value back out and turn a restore into a save.
            refineRestore.remember(preferencesRepository.browseContentTypes.first())
            val storedOrder = preferencesRepository.browseSortOrder.first()
            if (storedOrder != BrowseSortOrder.DEFAULT) {
                _refineState.value = _refineState.value.copy(sortOrder = storedOrder)
            }
            // Reading the two values suspends on a disk read, so a fast source can publish its list
            // before this line is reached; without this the restore would be silently skipped
            // whenever the load won the race. Republishing the list already held re-runs the apply.
            // Both paths run on the main dispatcher, so this cannot observe a half-assigned list.
            if (loadedFiles.isNotEmpty()) {
                publishLoaded(loadedFiles)
            }
        }
    }

    fun loadMediaFiles() {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            // A reload replaces the list, so ids held from the previous one would address files
            // that are no longer on screen.
            fileOperations.clearFileSelection()

            if (isNetworkSource && _sourceId != null) {
                // Load from network source
                loadNetworkFiles(_sourceId!!)
            } else {
                // Load from local storage
                loadLocalFiles()
            }
        }
    }

    private suspend fun loadLocalFiles() {
        val source = localSource()
        if (source == null) {
            _uiState.value = BrowseUiState.Empty(ScreenTitle.Resource(R.string.browse_media_type_disabled))
            return
        }

        source
            .catch { e ->
                Timber.e(e, "Error loading local media files")
                _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
            }
            .collect { result ->
                result.fold(
                    onSuccess = { files ->
                        publishLoaded(files)
                    },
                    onFailure = { e ->
                        _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
                    }
                )
            }
    }

    /**
     * S2130: which watch-store listing this route asked for, or null when a setting hides it.
     *
     * The route's category token decides, not its media type: documents come from a
     * `MediaStore.Files` query with no typed collection behind it, and "all" and "recents" read the
     * same flat merged listing - §6 settled that recency is the newest-first sort plus the first
     * page, so the two differ in label and in how far the wearer scrolls, not in the query.
     *
     * Only a content type can be switched off. "All" and "recents" are ways of looking at whatever
     * is allowed rather than types of their own, which is the same rule
     * `BrowseCategoryCatalog.DISABLEABLE_TYPES` states for the chips that lead here.
     */
    private suspend fun localSource(): Flow<Result<List<WearMediaFile>>>? = when (_categoryToken) {
        BrowseCategoryCatalog.TOKEN_ALL,
        BrowseCategoryCatalog.TOKEN_RECENTS -> mediaRepository.getAllMediaFiles()

        BrowseCategoryCatalog.TOKEN_DOCUMENTS -> if (preferencesRepository.isDocumentsEnabled.first()) {
            mediaRepository.getDocumentFiles()
        } else {
            null
        }

        else -> if (isMediaTypeEnabled()) {
            mediaRepository.getMediaFiles(mediaType)
        } else {
            null
        }
    }

    private suspend fun isMediaTypeEnabled(): Boolean = when (mediaType) {
        MediaType.MUSIC -> preferencesRepository.isAudioEnabled.first()
        MediaType.VIDEO -> preferencesRepository.isVideoEnabled.first()
        MediaType.PHOTO -> preferencesRepository.isImagesEnabled.first()
    }

    private suspend fun loadNetworkFiles(sourceId: String) {
        withContext(Dispatchers.IO) {
            try {
                // First, get the saved NetworkSource by ID
                val source = networkSourceRepository.getSourceById(sourceId)
                if (source == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = BrowseUiState.Error(
                            ScreenTitle.Resource(R.string.browse_network_source_not_found)
                        )
                    }
                    return@withContext
                }

                // S1556: the source's own path, not the server root. The watch's SFTP connection
                // test already asserts this path is reachable, so listing the root instead showed
                // the user something other than what the same screen had just verified.
                // Normalised again here, idempotently: a source stored before S1556's import fix
                // still holds the phone's URL form, and no phone has to be nearby to correct it.
                val currentPath = NetworkBasePath
                    .normalize(source.basePath, source.type, source.shareName)
                    .ifBlank { "/" }
                Timber.d("Connecting to ${source.type} source: ${source.server}")

                val mediaFiles: List<WearMediaFile> = when (source.type) {
                    NetworkSourceType.SMB -> {
                        val connectResult = networkDataSources.smb.connect(source)
                        if (connectResult.isFailure) {
                            val error = connectResult.exceptionOrNull()?.message ?: "Connection failed"
                            Timber.e("Failed to connect to SMB: $error")
                            withContext(Dispatchers.Main) {
                                _uiState.value = BrowseUiState.Error(
                                    ScreenTitle.Resource(R.string.browse_network_connection_failed)
                                )
                            }
                            return@withContext
                        }
                        val result = networkDataSources.smb.listFiles(currentPath)
                        if (result.isFailure) {
                            error(result.exceptionOrNull()?.message ?: "SMB list failed")
                        }
                        result.getOrDefault(emptyList()).mapIndexed { index, entry ->
                            val fullPath = if (currentPath == "/" || currentPath.isEmpty()) {
                                entry.name
                            } else {
                                "${currentPath.trimEnd('/')}/${entry.name}"
                            }
                            WearMediaFile(
                                id = index.toLong(),
                                name = entry.name,
                                uri = android.net.Uri.parse(fullPath),
                                mimeType = MediaMimeTypes.fromFileName(entry.name),
                                size = entry.size,
                                dateModified = entry.modifiedTime,
                                duration = 0
                            )
                        }
                    }
                    NetworkSourceType.FTP -> networkDataSources.ftp.listDirectory(source, currentPath)
                    NetworkSourceType.SFTP -> networkDataSources.sftp.listDirectory(source, currentPath)
                    NetworkSourceType.GOOGLE_DRIVE -> error("Google Drive not supported on Wear")
                }.filter { matchesMediaType(it.mimeType, mediaType) }
                Timber.d("Loaded ${mediaFiles.size} media files from ${source.type}")
                withContext(Dispatchers.Main) {
                    publishLoaded(mediaFiles)
                }
            } catch (e: Exception) {
                Timber.d("S2278: browse network load catch entered, cancellation cure routes it")
                e.errorUnlessCancellation("Exception loading network files")
                withContext(Dispatchers.Main) {
                    _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
                }
            }
        }
    }

    /**
     * S2136: takes what the source returned and shows the refined view of it.
     *
     * The only place [loadedFiles] is set, so there is one answer to "what did the source say" no
     * matter which of the two load paths asked.
     */
    private fun publishLoaded(files: List<WearMediaFile>) {
        loadedFiles = files
        // S2199: the remembered type filter is applied here and nowhere else, because this is the
        // only point at which the types this route actually holds are known.
        refineRestore.consume(presentContentTypes())?.let { types ->
            _refineState.value = _refineState.value.copy(contentTypes = types)
        }
        republish()
    }

    /**
     * S2136: how to read the refine keys off a file on this screen.
     *
     * The content type resolves the mime type and falls back to the route's own media type, which is
     * what `MediaFileGrid` already does for the badge - a file whose extension the resolver does not
     * know still filters as the thing the route is listing, rather than collapsing into "other".
     */
    private fun refineKeys(): BrowseRefineKeys<WearMediaFile> = BrowseRefineKeys(
        name = WearMediaFile::name,
        contentType = { contentTypeForMime(it.mimeType) ?: unresolvedContentType() },
        dateModified = WearMediaFile::dateModified,
        sizeBytes = WearMediaFile::size
    )

    /**
     * S2130: what an unrecognised file counts as, taken from the category rather than the media type.
     *
     * The mime resolver knows image, video and audio and nothing else, so on the documents list every
     * row would otherwise fall back to the route's media type and filter as music. The catalog's
     * answer is right for all three shapes at once: DOCUMENT on the documents list, and OTHER on the
     * two mixed listings, where a file the resolver cannot place genuinely is unclassified.
     */
    private fun unresolvedContentType(): WearContentType =
        BrowseCategoryCatalog.categoryForToken(_categoryToken)?.type ?: mediaType.asContentType()

    /**
     * S2136: recomputes the published state from the held list and the current refine state.
     *
     * Called by every setter, and never by a loader other than through [publishLoaded]. An empty
     * projection over a non-empty list is [BrowseUiState.NoMatches] rather than `Empty`: strategic
     * goal 6 requires the wearer to tell "nothing matched" from "this resource is empty", and only
     * this function knows both counts at once.
     */
    private fun republish() {
        if (loadedFiles.isEmpty()) {
            _uiState.value = BrowseUiState.Empty(ScreenTitle.Resource(R.string.browse_no_media_files))
            return
        }
        val shown = BrowseListProjection.refine(loadedFiles, refineKeys(), _refineState.value)
        _uiState.value = if (shown.isEmpty()) {
            BrowseUiState.NoMatches
        } else {
            BrowseUiState.Success(shown)
        }
    }

    /**
     * S2136: the content types actually present in the loaded list.
     *
     * The screen offers the filter icon only when this holds more than one entry - ADR-2 makes that
     * a property of the list rather than of the route, so a new route inherits the rule silently.
     */
    fun presentContentTypes(): List<WearContentType> =
        BrowseListProjection.presentTypes(loadedFiles, refineKeys())

    /** S2136: the orders this list can be shown in - all seven, since a file carries every key. */
    fun availableSortOrders(): List<BrowseSortOrder> = refineKeys().availableSortOrders()

    fun setSearchQuery(query: String) = updateRefine { it.copy(searchQuery = query) }

    fun setContentTypes(types: Set<WearContentType>) = updateRefine { it.copy(contentTypes = types) }

    fun setSortOrder(order: BrowseSortOrder) = updateRefine { it.copy(sortOrder = order) }

    fun setShowSearchDialog(show: Boolean) = updateRefine { it.copy(showSearchDialog = show) }

    fun setShowFilterDialog(show: Boolean) = updateRefine { it.copy(showFilterDialog = show) }

    fun setShowSortDialog(show: Boolean) = updateRefine { it.copy(showSortDialog = show) }

    fun setSearchInputUnavailable(unavailable: Boolean) =
        updateRefine { it.copy(searchInputUnavailable = unavailable) }

    /**
     * S2136: the one path every refine setter takes.
     *
     * Recomputes rather than reloads - strategic goal 5 requires a choice to apply without touching
     * the source, and on this screen the source may be a network share reached through the phone.
     */
    private fun updateRefine(transform: (BrowseRefineState) -> BrowseRefineState) {
        val previous = _refineState.value
        val updated = transform(previous)
        _refineState.value = updated
        // S2199: only the two choices the wearer made deliberately. The query and the three dialog
        // flags stay in memory: a restored query narrows the list on a word nobody can see, and a
        // restored flag would reopen a dialog the moment the screen is entered. The equality guard
        // keeps opening a dialog from touching the disk.
        if (previous.contentTypes != updated.contentTypes || previous.sortOrder != updated.sortOrder) {
            viewModelScope.launch {
                preferencesRepository.setBrowseContentTypes(updated.contentTypes)
                preferencesRepository.setBrowseSortOrder(updated.sortOrder)
            }
        }
        republish()
    }

    /**
     * Publishes this file's picture once and keeps the answer.
     *
     * A file already carrying an entry is never asked again, so re-laying the same items in another
     * view mode costs no second trip to the network. The read runs in [viewModelScope], so leaving
     * the screen cancels whatever has not finished.
     */
    fun thumbnailFor(file: WearMediaFile) {
        if (_thumbnails.value.containsKey(file.id)) return
        publish(file.id, WearThumbnail.Loading)
        viewModelScope.launch {
            publish(file.id, thumbnailRepository.thumbnailFor(file, _sourceId))
        }
    }

    /**
     * A long folder must not turn this map into an unbounded bitmap holder, so it is capped at the
     * same count the repository caches. Dropping the oldest entry is safe: a scroll back re-asks,
     * and the repository answers from its own cache without reopening a connection.
     */
    private fun publish(id: Long, thumbnail: WearThumbnail) {
        _thumbnails.update { current ->
            val next = current + (id to thumbnail)
            val excess = next.size - WearThumbnailBudget.MAX_CACHED_THUMBNAILS
            if (excess <= 0) next else next.entries.drop(excess).associate { it.key to it.value }
        }
    }

    fun selectFile(file: WearMediaFile) {
        Timber.d("File selected: ${file.name}")
        _selectedFile.value = file

        // The set the player will page through is fixed here, from the list on screen, because a
        // later re-query would answer in a different order than the user saw (S1683 ADR-2).
        val displayed = (_uiState.value as? BrowseUiState.Success)?.files
        if (displayed != null) {
            playbackSetManager.publish(displayed, displayed.indexOfFirst { it.id == file.id })
        }

        // Save to SelectedMediaManager for player access.
        // The uri shape differs per protocol and the player has to know which it got: SMB files
        // carry a path relative to the share, FTP and SFTP files carry a full ftp:// / sftp:// URI.
        // S1687: the source id travels with them, because it is the only way the player can reach
        // the protocol and the credentials this file actually needs.
        selectedMediaManager.selectFile(
            file = file,
            isNetworkSource = isNetworkSource,
            streamUri = file.uri.toString(),
            sourceId = _sourceId
        )
    }

    fun clearSelection() {
        _selectedFile.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // The manager is a singleton and would otherwise outlive this screen, leaving a player
        // opened by any later route paging through a list the user has already left.
        playbackSetManager.clear()
    }

    /**
     * S1683: the title is either a source's own name, which no dictionary can translate, or one of
     * four resources. It used to be four English literals, so the browse list stayed English under a
     * Russian interface - the defect that put the localization constraint in the spec at all.
     *
     * S2130: the three type titles read the same keys the category chips do. A chip labelled "Images"
     * used to open a screen titled "Photos", because the chip and the screen it opens were labelled
     * from two different key sets.
     */
    fun getScreenTitle(): ScreenTitle {
        if (isNetworkSource) {
            val name = sourceName
            return if (name.isNullOrBlank()) {
                ScreenTitle.Resource(R.string.network_storage)
            } else {
                ScreenTitle.Text(name)
            }
        }
        return ScreenTitle.Resource(localTitleRes(_categoryToken, mediaType))
    }
}

/**
 * S2130: the chip's own label, so documents, "all" and "recents" are titled at all.
 *
 * Those three have no media type to map, and the presentation table already holds the word each
 * chip is written with - reading it here is what keeps the screen titled the same as the chip that
 * opened it, which is the defect the key set above records.
 *
 * Top-level rather than a member, on the same grounds as [matchesMediaType] below: it reads no
 * state the caller cannot hand it, and both of its inputs are arguments.
 */
@StringRes
private fun localTitleRes(categoryToken: String?, mediaType: MediaType): Int =
    BrowseCategoryCatalog.categoryForToken(categoryToken)
        ?.let { BrowseCategoryPresentation.labelFor(it) }
        ?: when (mediaType) {
            MediaType.MUSIC -> R.string.wear_phone_audio
            MediaType.VIDEO -> R.string.wear_phone_video
            MediaType.PHOTO -> R.string.wear_phone_images
        }

/**
 * Whether a MIME type belongs to the expected media type category.
 *
 * Top-level rather than a member: it reads no state of the screen, taking both of its inputs as
 * arguments, and sat inside the class only by habit.
 */
private fun matchesMediaType(mimeType: String?, mediaType: MediaType): Boolean {
    if (mimeType == null) {
        return false
    }
    return when (mediaType) {
        MediaType.PHOTO -> mimeType.startsWith("image/")
        MediaType.VIDEO -> mimeType.startsWith("video/")
        MediaType.MUSIC -> mimeType.startsWith("audio/")
    }
}
