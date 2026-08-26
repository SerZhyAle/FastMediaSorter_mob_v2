package com.sza.fastmediasorter.wear.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.network.WearNetworkDataSources
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.NetworkBasePath
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val performFileOperation: PerformWearFileOperationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val _selectedFile = MutableStateFlow<WearMediaFile?>(null)
    val selectedFile: StateFlow<WearMediaFile?> = _selectedFile.asStateFlow()

    /**
     * Kept apart from [uiState] on purpose: folding it in would re-emit the whole list on every tap
     * and re-run the per-id thumbnail effects the grid keys on.
     */
    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    private val _operationRun = MutableStateFlow(WearFileOperationRunState())
    val operationRun: StateFlow<WearFileOperationRunState> = _operationRun.asStateFlow()

    /** Cancelled by [viewModelScope] when the screen goes, so an abandoned batch stops copying. */
    private var operationJob: Job? = null

    /**
     * The operations every selected file permits - an intersection, never a union.
     *
     * A mixed selection is the normal case once "select all" exists, and offering an action only
     * some of its files accept is the trust failure strategic 7 rates first.
     */
    val allowedOperations: StateFlow<Set<WearFileOperationKind>> =
        combine(_uiState, _selectedFileIds) { state, ids -> allowedFor(state, ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), emptySet())

    /** The file list's own stored view, separate from the navigation screens' mode. */
    val fileListViewMode: StateFlow<WearViewMode> = preferencesRepository.fileListViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    private val _thumbnails = MutableStateFlow<Map<Long, WearThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, WearThumbnail>> = _thumbnails.asStateFlow()

    // Navigation arguments - to be set from UI layer
    private var _mediaType: MediaType = MediaType.MUSIC
    private var _sourceId: String? = null
    private var _sourceName: String? = null

    val mediaType: MediaType get() = _mediaType
    val isNetworkSource: Boolean get() = _sourceId != null
    val sourceName: String? get() = _sourceName

    /**
     * Initialize navigation arguments. Call this from the composable.
     */
    fun setNavigationArgs(mediaType: MediaType, sourceId: String? = null, sourceName: String? = null) {
        _mediaType = mediaType
        _sourceId = sourceId
        _sourceName = sourceName
    }

    init {
        Timber.d("BrowseViewModel initialized")
        // loadMediaFiles() will be called after setNavigationArgs() from UI
    }

    fun loadMediaFiles() {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            // A reload replaces the list, so ids held from the previous one would address files
            // that are no longer on screen.
            _selectedFileIds.value = emptySet()

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
        // Check if media type is enabled in settings
        val isEnabled = when (mediaType) {
            MediaType.MUSIC -> preferencesRepository.isAudioEnabled.first()
            MediaType.VIDEO -> preferencesRepository.isVideoEnabled.first()
            MediaType.PHOTO -> preferencesRepository.isImagesEnabled.first()
        }

        if (!isEnabled) {
            _uiState.value = BrowseUiState.Empty(ScreenTitle.Resource(R.string.browse_media_type_disabled))
            return
        }

        mediaRepository.getMediaFiles(mediaType)
            .catch { e ->
                Timber.e(e, "Error loading local media files")
                _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
            }
            .collect { result ->
                result.fold(
                    onSuccess = { files ->
                        _uiState.value = if (files.isEmpty()) {
                            BrowseUiState.Empty(ScreenTitle.Resource(R.string.browse_no_media_files))
                        } else {
                            BrowseUiState.Success(files)
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
                    }
                )
            }
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
                    _uiState.value = if (mediaFiles.isEmpty()) {
                        BrowseUiState.Empty(ScreenTitle.Resource(R.string.browse_no_media_files))
                    } else {
                        BrowseUiState.Success(mediaFiles)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception loading network files")
                withContext(Dispatchers.Main) {
                    _uiState.value = BrowseUiState.Error(ScreenTitle.Resource(R.string.browse_load_failed))
                }
            }
        }
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

    /** Long press opens selection mode on the pressed file. */
    fun enterSelection(file: WearMediaFile) {
        if (!isActionable(file)) return
        _selectedFileIds.value = setOf(file.id)
    }

    fun toggleSelection(file: WearMediaFile) {
        if (!isActionable(file)) return
        _selectedFileIds.update { current ->
            if (file.id in current) current - file.id else current + file.id
        }
    }

    fun selectAll() {
        val displayed = (_uiState.value as? BrowseUiState.Success)?.files ?: return
        _selectedFileIds.value = displayed.filter(::isActionable).map { it.id }.toSet()
    }

    fun clearFileSelection() {
        _selectedFileIds.value = emptySet()
    }

    /**
     * A file no operation accepts must never enter the selection: it would count towards the batch
     * and let the action menu offer work its source cannot perform.
     */
    private fun isActionable(file: WearMediaFile): Boolean {
        val storageClass = capabilityPolicy.classify(file, isNetworkSource)
        return capabilityPolicy.allowedOperations(storageClass).isNotEmpty()
    }

    /** An empty intersection when nothing is selected, so the action chip has nothing to open. */
    private fun allowedFor(state: BrowseUiState, ids: Set<Long>): Set<WearFileOperationKind> {
        val selected = (state as? BrowseUiState.Success)
            ?.files
            ?.filter { it.id in ids }
            .orEmpty()
        return if (selected.isEmpty()) {
            emptySet()
        } else {
            selected
                .map { capabilityPolicy.allowedOperations(capabilityPolicy.classify(it, isNetworkSource)) }
                .reduce { acc, allowed -> acc intersect allowed }
        }
    }

    /**
     * Runs [operation] over the current selection, reporting each file as its own result.
     *
     * The run is not collapsed into one verdict: strategic 11 criterion 6 requires the user to read
     * the partial success of a batch, so every emission is accumulated rather than replaced.
     */
    fun runOperation(operation: WearFileOperation) {
        val targets = selectedFiles()
        if (targets.isEmpty()) {
            Timber.w("Wear file operation requested with an empty selection")
        } else {
            startOperation(targets, operation)
        }
    }

    private fun startOperation(targets: List<WearMediaFile>, operation: WearFileOperation) {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            Timber.d("S1863: start $operation over ${targets.size} file(s)")
            _operationRun.value = WearFileOperationRunState(running = true, total = targets.size)
            try {
                collectRun(targets, operation)
            } finally {
                // Also on cancellation: without this the progress dialog would keep the screen with
                // running = true forever, and the files never reached would have no answer at all.
                finishRun(targets)
            }
            _selectedFileIds.value = emptySet()
            // Only a run that actually changed the directory invalidates the list on screen; a send
            // that left every file where it was would reload for nothing.
            val changed = _operationRun.value.results.any { it.outcome == WearFileOperationOutcome.SUCCEEDED }
            Timber.d("S1863: finished ${_operationRun.value.results.size} result(s), changed=$changed")
            if (operation.mutatesList() && changed) {
                loadMediaFiles()
            }
        }
    }

    /**
     * A failure upstream ends the batch, not the process.
     *
     * The stager reads a MediaStore row through the content resolver, which throws past the
     * [java.io.IOException] it handles when a provider or a grant has gone; unhandled, that killed the
     * app mid-batch and left the progress dialog owning the screen.
     */
    private suspend fun collectRun(targets: List<WearMediaFile>, operation: WearFileOperation) {
        performFileOperation(targets, operation, isNetworkSource)
            .catch { throwable ->
                Timber.e(throwable, "Wear file operation failed mid-batch")
                val answered = _operationRun.value.results.map { it.fileName }.toSet()
                targets.filterNot { it.name in answered }.forEach { pending ->
                    emit(WearFileOperationResult(pending.name, WearFileOperationOutcome.FAILED))
                }
            }
            .collect { result ->
                _operationRun.update { current ->
                    current.copy(completed = current.completed + 1, results = current.results + result)
                }
            }
    }

    /**
     * Closes the run, giving every file the batch never reached an explicit CANCELLED line.
     *
     * Silence would otherwise be indistinguishable from success on a screen the user reads once.
     */
    private fun finishRun(targets: List<WearMediaFile>) {
        _operationRun.update { current ->
            val answered = current.results.map { it.fileName }.toSet()
            val cancelled = targets
                .filterNot { it.name in answered }
                .map { WearFileOperationResult(it.name, WearFileOperationOutcome.CANCELLED) }
            current.copy(running = false, results = current.results + cancelled)
        }
    }

    /** Stops a run in flight; [finishRun] then records what it never reached. */
    fun cancelOperation() {
        operationJob?.cancel()
    }

    /** The results stay until the user dismisses them, outliving the reload a run may have caused. */
    fun dismissOperationResults() {
        _operationRun.value = WearFileOperationRunState()
    }

    private fun selectedFiles(): List<WearMediaFile> {
        val ids = _selectedFileIds.value
        return (_uiState.value as? BrowseUiState.Success)
            ?.files
            ?.filter { it.id in ids }
            .orEmpty()
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
        return ScreenTitle.Resource(
            when (mediaType) {
                MediaType.MUSIC -> R.string.music
                MediaType.VIDEO -> R.string.videos
                MediaType.PHOTO -> R.string.photos
            }
        )
    }

    /**
     * Check if a MIME type matches the expected media type category.
     */
    private fun matchesMediaType(mimeType: String?, mediaType: MediaType): Boolean {
        if (mimeType == null) return false

        return when (mediaType) {
            MediaType.PHOTO -> mimeType.startsWith("image/")
            MediaType.VIDEO -> mimeType.startsWith("video/")
            MediaType.MUSIC -> mimeType.startsWith("audio/")
        }
    }
}

/**
 * Whether a finished run leaves the list on screen describing files that are no longer there.
 *
 * A move removes the watch copy once the phone confirms, so it invalidates the list exactly as a
 * delete does; a plain send never touches the source.
 */
private fun WearFileOperation.mutatesList(): Boolean = when (this) {
    WearFileOperation.SendToPhone -> false
    WearFileOperation.MoveToPhone -> true
    WearFileOperation.Delete -> true
    is WearFileOperation.Rename -> true
    // Everything it changes happens on the phone; the watch copy it names is still where it was.
    is WearFileOperation.OpenOnPhone -> false
}
