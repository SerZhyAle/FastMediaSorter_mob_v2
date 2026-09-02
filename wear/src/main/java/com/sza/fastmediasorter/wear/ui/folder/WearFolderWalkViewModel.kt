package com.sza.fastmediasorter.wear.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderEntry
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The first window of any level, and the marker for "this load replaces the list rather than growing it". */
private const val FIRST_OFFSET = 0

/** One folder walked into: the address that lists it again, and the name the header titles it by. */
private data class FolderLevel(val address: WearFolderAddress, val name: String)

/** What the watch-local folder walk is showing right now. */
sealed interface WearFolderWalkUiState {

    data object Loading : WearFolderWalkUiState

    data class Content(
        val entries: List<WearFolderEntry>,
        val title: ScreenTitle,
        val canGoUp: Boolean,
        val canLoadMore: Boolean
    ) : WearFolderWalkUiState

    /** The level was listed and holds nothing this walk can show. */
    data class Empty(
        val title: ScreenTitle,
        val canGoUp: Boolean
    ) : WearFolderWalkUiState
}

/**
 * S2201: holds the position of the walk over the watch's own storage.
 *
 * The trail, the level title and the up-navigation reproduce the shape the paired-phone browser
 * settled on, against this ticket's repository rather than that screen's Data Layer client (ADR-4).
 * Reusing the phone view model was refused there because its seam is a concrete client addressing
 * folders by an opaque token the watch cannot interpret.
 */
@HiltViewModel
class WearFolderWalkViewModel @Inject constructor(
    private val repository: WearLocalFolderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Where the walk starts, from the route argument.
     *
     * An unparseable token falls back to the root rather than to an error state: the argument can
     * only reach here from a route this app built, so a token that does not parse means an address
     * scheme that has since changed - and the entrance is a working screen, while a dead end is not.
     */
    private val startAddress: WearFolderAddress =
        WearFolderAddress.parse(savedStateHandle.get<String>(WearRoutes.ARG_FOLDER_TOKEN))
            ?: WearFolderAddress.Root

    /** The folders descended into below [startAddress], deepest last. Empty means standing on it. */
    private val trail = ArrayDeque<FolderLevel>()

    private val _uiState = MutableStateFlow<WearFolderWalkUiState>(WearFolderWalkUiState.Loading)
    val uiState: StateFlow<WearFolderWalkUiState> = _uiState.asStateFlow()

    /**
     * The level as far as it has been read, kept apart from the published state so a further window
     * appends to it. Reading it back off the state would make growing the list depend on which
     * branch the state happens to be in.
     */
    private var entries: List<WearFolderEntry> = emptyList()

    /** Where the next window of this level starts, or null once the level is exhausted. */
    private var nextOffset: Int? = null

    private var loadJob: Job? = null

    init {
        load(FIRST_OFFSET)
    }

    /**
     * Descends into [entry], which must be a directory.
     *
     * A file carries no address, and the walk has nothing to list for one - the screen sends those
     * to a player instead, so this refuses rather than pushing a level that would list nothing.
     */
    fun openFolder(entry: WearFolderEntry) {
        val address = entry.address ?: return
        trail.addLast(FolderLevel(address = address, name = entry.name))
        load(FIRST_OFFSET)
    }

    /**
     * Steps back one level, reporting whether there was one.
     *
     * False means the walk is standing where it started and the gesture belongs to the screen, which
     * leaves. Nothing is reloaded in that case: the level being left is about to disappear, and a
     * request for it would land on a screen that is no longer there.
     */
    fun navigateUp(): Boolean {
        val stepped = trail.removeLastOrNull() != null
        if (stepped) {
            load(FIRST_OFFSET)
        }
        return stepped
    }

    /** Appends the next window of the current level, or does nothing once it is exhausted. */
    fun loadMore() {
        val offset = nextOffset ?: return
        load(offset)
    }

    /**
     * Lists the current level from [offset].
     *
     * A load in flight is cancelled first, because every caller changes which level is current: a
     * page that arrived late would append rows of the folder just left to the folder now shown.
     */
    private fun load(offset: Int) {
        loadJob?.cancel()
        val address = trail.lastOrNull()?.address ?: startAddress
        Timber.d("S2201: walk level depth=${trail.size} offset=$offset")
        if (offset == FIRST_OFFSET) {
            entries = emptyList()
            nextOffset = null
            _uiState.value = WearFolderWalkUiState.Loading
        }
        loadJob = viewModelScope.launch {
            repository.listLevel(address, offset)
                .onSuccess { page ->
                    entries = if (offset == FIRST_OFFSET) page.entries else entries + page.entries
                    nextOffset = page.nextOffset
                    _uiState.value = stateFor()
                }
                .onFailure { error ->
                    // The repository already refuses to throw so the trail survives a level it could
                    // not read. Publishing the level as it stands keeps that promise: a first window
                    // that failed shows the empty branch, a later one leaves the rows already there.
                    Timber.w(error, "Folder level unreadable, staying on the walk: %s", address)
                    nextOffset = null
                    _uiState.value = stateFor()
                }
        }
    }

    private fun stateFor(): WearFolderWalkUiState {
        val title = trail.lastOrNull()
            ?.let { ScreenTitle.Text(it.name) }
            // The entrance has no folder name of its own, so it takes the word the tile that opens
            // the walk is labelled with rather than a second name for the same place.
            ?: ScreenTitle.Resource(R.string.wear_phone_browse)
        val canGoUp = trail.isNotEmpty()
        return if (entries.isEmpty()) {
            WearFolderWalkUiState.Empty(title = title, canGoUp = canGoUp)
        } else {
            WearFolderWalkUiState.Content(
                entries = entries,
                title = title,
                canGoUp = canGoUp,
                canLoadMore = nextOffset != null
            )
        }
    }
}
