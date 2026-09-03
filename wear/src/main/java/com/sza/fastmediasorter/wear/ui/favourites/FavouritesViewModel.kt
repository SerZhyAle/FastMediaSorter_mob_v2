package com.sza.fastmediasorter.wear.ui.favourites

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearSendToReceiverFilter
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_LOCAL
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val VIEW_MODE_SUBSCRIPTION_MS = 5_000L

/** S1846: what came of a tap on a favourite row. */
sealed interface FavouriteOpenRequest {

    /** Handed to the players and addressable by [fileId]. */
    data class Ready(val fileId: Long, val mimeType: String) : FavouriteOpenRequest

    /** A mark made before the watch recorded the kind - there is no player to choose. */
    data object Unopenable : FavouriteOpenRequest
}

/** S1846: what the Favourites section is showing right now. */
sealed interface FavouritesUiState {

    data object Loading : FavouritesUiState

    /** Nothing has been marked on this watch - a statement of fact, not a failure. */
    data object Empty : FavouritesUiState

    data class Content(val records: List<WearFavoriteRecord>) : FavouritesUiState
}

/**
 * S1846: holds the favourites this watch itself recorded.
 *
 * The list is the watch's own store and nothing else. There is no incoming transfer of favourites from the
 * phone - the repository only writes, reads and hands back an outgoing delta - so "everything the user
 * marked anywhere" is not a state this screen could reach without a transport that does not exist.
 */
@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favoritesRepository: WearFavoritesRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val selectedMediaManager: SelectedMediaManager,
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val performFileOperation: PerformWearFileOperationUseCase,
    private val sendToReceiversRepository: WearSendToReceiversRepository,
    preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    /**
     * The row the user just asked to open, consumed once by the screen.
     *
     * A one-shot rather than part of [uiState]: the list does not change when a file opens, and folding a
     * navigation event into the list state would replay the navigation on every recomposition.
     */
    private val _openRequest = MutableStateFlow<FavouriteOpenRequest?>(null)
    val openRequest: StateFlow<FavouriteOpenRequest?> = _openRequest.asStateFlow()

    /** The run in flight, kept only so a second press cannot start a parallel one (S2142). */
    private var operationJob: Job? = null

    /** What the last file operation came to, or null when there is nothing to report. */
    private val _operationNotice = MutableStateFlow<WearFileOperationOutcome?>(null)
    val operationNotice: StateFlow<WearFileOperationOutcome?> = _operationNotice.asStateFlow()

    /**
     * The file-list view, not the resource-list view.
     *
     * S1730 split the two on purpose, and this screen is a list of files - so it follows the same setting
     * the browser's file list follows rather than growing a third view mode of its own.
     */
    val fileListViewMode: StateFlow<WearViewMode> = preferencesRepository.fileListViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    init {
        load()
    }

    fun refresh() {
        load()
    }

    /**
     * Prepares [record] for a player and asks the screen to go there.
     *
     * The hand-off through [SelectedMediaManager] is the load-bearing half, not the navigation: the player
     * routes address a file by id, and a favourite has no MediaStore row to look that id up in - research
     * artifact 02 §3 records that nothing resolved a stored path back to an openable item. Handing the file
     * over first is what makes the id mean something when the player asks.
     *
     * A record written before this ticket carries no kind, so no player can be chosen for it and the screen
     * is told so instead of being sent somewhere that would open empty.
     */
    fun open(record: WearFavoriteRecord) {
        val mimeType = record.mimeType
        if (mimeType == null) {
            _openRequest.value = FavouriteOpenRequest.Unopenable
            return
        }
        val fileId = record.identity.hashCode().toLong()
        selectedMediaManager.selectFile(
            file = WearMediaFile(
                id = fileId,
                name = record.displayName,
                uri = Uri.parse(record.filePath),
                mimeType = mimeType,
                size = 0L,
                dateModified = 0L
            ),
            isNetworkSource = record.sourceId != SOURCE_ID_LOCAL,
            sourceId = record.sourceId.takeIf { it != SOURCE_ID_LOCAL }
        )
        _openRequest.value = FavouriteOpenRequest.Ready(fileId = fileId, mimeType = mimeType)
    }

    /** Called once the screen has acted, so a recomposition does not open the same row twice. */
    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /**
     * What may be done with the file [record] points at.
     *
     * A network favourite is asked about as a network entry, so the policy answers with the empty set
     * the source really permits rather than with the four operations a local path would allow.
     */
    fun allowedOperationsFor(record: WearFavoriteRecord): Set<WearFileOperationKind> {
        val storageClass = capabilityPolicy.classify(record.toMediaFile(), record.isNetwork())
        // S2004: the policy answers about the file; this subtracts what the *surface* cannot address.
        // A favourited copy of a phone file classifies as a phone copy, so the policy rightly offers
        // opening it there - but the phone resolves an open by the token its browse protocol issued,
        // and a favourite is addressed by its own record and carries no token. Offering it here would
        // put a refusal behind a menu row, which is the one thing strategic 11 criterion 7 forbids.
        val addressable = capabilityPolicy.allowedOperations(storageClass) - WearFileOperationKind.OPEN_ON_PHONE
        // S2142: writing to a foreign MediaStore row goes through a system confirmation, and only the
        // browse list mounts the launcher that can show one. Offering delete or rename here would put
        // a refusal behind a menu row - the same reason OPEN_ON_PHONE is withheld just above.
        return if (storageClass == WearFileStorageClass.MEDIA_STORE) {
            addressable - WearFileOperationKind.DELETE - WearFileOperationKind.RENAME
        } else {
            addressable
        }
    }

    /** The file the action menu acts on - the same one the player is handed. */
    fun actionTargetFor(record: WearFavoriteRecord): WearMediaFile = record.toMediaFile()

    /**
     * S2142: the receivers [record] may be handed to, through the filter every surface shares.
     *
     * Per record rather than one list for the screen: the type filter is a property of the file, so
     * a screen-wide list would offer the wrong receivers for whichever row was pressed.
     */
    fun sendToReceiversFor(record: WearFavoriteRecord): List<WearSendToReceiverEntry> =
        WearSendToReceiverFilter.apply(
            sendToReceiversRepository.observe().value,
            listOf(record.toMediaFile())
        )

    /**
     * Runs [operation] over [record]'s file and reports what came of it.
     *
     * S2142: a second press while the first run is still going is ignored, not queued behind it and
     * not allowed to start beside it. A send through the phone crosses the bridge in its middle, so
     * a second run of the same operation is how one file reaches a receiver twice.
     */
    fun runOperation(record: WearFavoriteRecord, operation: WearFileOperation) {
        if (operationJob?.isActive == true) {
            Timber.i("Wear file operation ignored: a run is already in progress")
            return
        }
        operationJob = viewModelScope.launch {
            performFileOperation(listOf(record.toMediaFile()), operation, record.isNetwork())
                .collect { result -> _operationNotice.value = result.outcome }
        }
    }

    /**
     * Reports that this screen cannot ask the phone to open the pressed favourite.
     *
     * [allowedOperationsFor] withholds that action here, so this is the arm that cannot normally be
     * reached - kept because the menu's `when` must be total over the operation kinds, and because a
     * silent no-op would be the wrong answer if the withholding above ever stopped happening.
     */
    fun reportOpenOnPhoneUnavailable() {
        _operationNotice.value = WearFileOperationOutcome.REFUSED_UNSUPPORTED
    }

    /** Called once the screen has shown the notice, so it does not outlive the action it reports. */
    fun consumeOperationNotice() {
        _operationNotice.value = null
    }

    /**
     * Unmarks [record] and drops it from the list at once.
     *
     * The row leaves immediately rather than after a reload: the user asked for it to go, and re-reading an
     * encrypted store to learn what the user just told us is a visible pause for no new information. The
     * delta the phone will collect is written by the same use case the players use.
     */
    fun unmark(record: WearFavoriteRecord) {
        val current = _uiState.value
        if (current !is FavouritesUiState.Content) {
            return
        }
        val remaining = current.records.filterNot { it.identity == record.identity }
        _uiState.value = if (remaining.isEmpty()) FavouritesUiState.Empty else current.copy(records = remaining)
        viewModelScope.launch {
            toggleFavoriteUseCase.toggle(record.sourceId, record.filePath, wasFavorite = true)
        }
    }

    private fun WearFavoriteRecord.isNetwork(): Boolean = sourceId != SOURCE_ID_LOCAL

    /** The same file the player is handed, so the menu and the player never disagree about identity. */
    private fun WearFavoriteRecord.toMediaFile(): WearMediaFile = WearMediaFile(
        id = identity.hashCode().toLong(),
        name = displayName,
        uri = Uri.parse(filePath),
        mimeType = mimeType,
        size = 0L,
        dateModified = 0L
    )

    private fun load() {
        _uiState.value = FavouritesUiState.Loading
        viewModelScope.launch {
            val records = favoritesRepository.getFavorites()
            _uiState.value = if (records.isEmpty()) {
                FavouritesUiState.Empty
            } else {
                FavouritesUiState.Content(records)
            }
        }
    }
}
