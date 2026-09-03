package com.sza.fastmediasorter.wear.ui.phone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.browse.BrowseListProjection
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineKeys
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineState
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.files.WEAR_PHONE_FILE_CACHE_DIR
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearSendToReceiverFilter
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearListShape
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus.NO_RESOURCE_FOR_TYPE
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.model.contentTypeForMime
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import com.sza.fastmediasorter.wear.ui.common.BrowseCategoryPresentation
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.MediaCacheEvictor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val VIEW_MODE_SUBSCRIPTION_MS = 5_000L

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * S2004: how much of the watch the copies fetched from the phone may hold, in megabytes.
 *
 * The floor is [WEAR_FILE_TRANSFER_MAX_BYTES], the largest single file the bridge will carry: a cap
 * below it would let the evictor remove the very file that was just fetched, and opening large files
 * would break. This is four of those, so a handful of recently opened files survive while the
 * directory stays bounded - it holds convenience copies, not anything the user asked to keep.
 */
private const val PHONE_FILE_CACHE_CAP_BYTES = 128L * BYTES_PER_MB

/** S2129: maximum concurrent on-demand thumbnail requests to the phone. */
private const val MAX_IN_FLIGHT_THUMBNAILS = 3

/** S2129: maximum thumbnail cache size on watch. */
private const val MAX_CACHED_THUMBNAILS = 50

/** S1846: what came of the last tap on a phone file. */
sealed interface PhoneFileOpenOutcome {

    /** The transfer is running; the screen shows it rather than looking frozen. */
    data object Opening : PhoneFileOpenOutcome

    /** Delivered and handed to the players - [fileId] addresses it on the player route. */
    data class Ready(val fileId: Long, val mimeType: String) : PhoneFileOpenOutcome

    /** Delivered, but no player on the watch renders this kind. */
    data object Unsupported : PhoneFileOpenOutcome

    /** [reason] is a protocol status, or null when the phone never answered. */
    data class Failed(val reason: WearPhoneResourceResponseStatus?) : PhoneFileOpenOutcome
}

/**
 * Turns what the page carried into the cell's picture state.
 *
 * A missing field is the definite absence, never the not-yet state: an older phone sends no
 * thumbnails at all, and a not-yet state would leave every cell waiting for bytes that are never
 * coming instead of settling on a type icon.
 */
internal fun WearPhoneResourceItem.toWearThumbnail(): WearThumbnail {
    val encoded = thumbnailBase64 ?: return WearThumbnail.Unavailable
    val bitmap = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
        .getOrNull()
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    return bitmap?.let { WearThumbnail.Ready(it) } ?: WearThumbnail.Unavailable
}

/**
 * S1846: the screen is titled by the chip that opened it, so a filtered list is not labelled "Phone"
 * like the unfiltered one.
 *
 * S2130 took the word off this file: the title now asks the same table the chip on the home screen
 * asked, so a category cannot be called one thing where it is tapped and another where it opens. The
 * unfiltered folder browser carries no token at all and keeps the screen's own name.
 */
@StringRes
private fun titleResFor(categoryToken: String?): Int =
    BrowseCategoryCatalog.categoryForToken(categoryToken)
        ?.let { BrowseCategoryPresentation.labelFor(it) }
        ?: R.string.phone_resource_title

/** One folder of the walked path: the token that reloads it, and the name the header titles it by. */
private data class FolderLevel(val token: String, val name: String)

/** What the paired-phone screen is showing right now. */
sealed interface PhoneResourceUiState {

    data object Loading : PhoneResourceUiState

    data class Content(
        val items: List<WearPhoneResourceItem>,
        val parentToken: String?,
        val title: ScreenTitle
    ) : PhoneResourceUiState

    /** The phone answered, and there is nothing here it is willing to show. */
    data object Empty : PhoneResourceUiState

    /**
     * S2130: the phone has resources, and none of them is configured to hold this category.
     *
     * Apart from [Empty] because the two send the wearer to different places: an empty folder is
     * about files that are not there yet, this is about a setting on the phone. Strategic ADR-5 makes
     * naming the cause the fix - the owner read "Video is empty" as the watch failing while the phone
     * was answering correctly about resources that were never configured to carry video.
     */
    data object NoResourceForType : PhoneResourceUiState

    /**
     * S2136: the folder holds entries, and none survive the current search or type filter.
     *
     * Separate from [Empty] for the reason strategic goal 6 gives: one is a folder with nothing in
     * it, the other is a list the wearer narrowed and can widen again from the header above it.
     */
    data object NoMatches : PhoneResourceUiState

    /**
     * No usable answer. [reason] is a protocol status or null when the phone never replied, and the
     * screen turns it into copy - the raw status never reaches the user.
     */
    data class Unavailable(val reason: WearPhoneResourceResponseStatus?) : PhoneResourceUiState

    /**
     * S2275: no phone is connected to this watch at all, so nothing was ever asked.
     *
     * Apart from [Unavailable] for the reason [NoResourceForType] is apart from [Empty] - the two send
     * the wearer somewhere different. [Unavailable] means the pair exists and the app on the phone is
     * silent; this means there is no pair, and the Retry that state offers would ask nobody.
     */
    data object NotPaired : PhoneResourceUiState
}

/**
 * S1697: holds the browse position for the paired-phone resource. Every failure lands on a state
 * that offers Retry rather than an empty list, because a paired phone is absent often and briefly -
 * a screen that simply showed nothing would read as "the phone has no media".
 */
@HiltViewModel
class PhoneResourceViewModel @Inject constructor(
    private val phoneResourceClient: PhoneResourceClient,
    private val selectedMediaManager: SelectedMediaManager,
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val performFileOperation: PerformWearFileOperationUseCase,
    private val sendToReceiversRepository: WearSendToReceiversRepository,
    @ApplicationContext context: Context,
    preferencesRepository: WearPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cacheDir: File = File(context.cacheDir, WEAR_PHONE_FILE_CACHE_DIR).apply { mkdirs() }

    /**
     * S2130: the category this screen was opened for, exactly as the route carried it.
     *
     * Kept uncollapsed because it is the only thing that tells the two entries apart: the All chip
     * sends [BrowseCategoryCatalog.TOKEN_ALL] and the folder browser sends no argument at all, yet
     * both want a null *type filter*. Deriving the list shape from that shared null is what made All
     * and Browse open the same folder listing (strategic ADR-3), so the shape is read from here and
     * the filter from [mediaType].
     */
    private val categoryToken: String? = savedStateHandle
        .get<String>(WearRoutes.ARG_MEDIA_TYPE)
        ?.takeIf { it.isNotBlank() }

    /**
     * S1846: the kind of file this screen was opened for, or null when no kind narrows it.
     *
     * Held for the screen's lifetime rather than passed once: it travels on every request of the
     * session - the first load, a step into a folder, Back and Retry alike - because dropping it
     * halfway would widen the list back to everything without the user asking.
     *
     * Null for both the All chip and the folder browser, which is correct for a *filter* - the phone
     * reads a null type as "every type". What separates those two is [categoryToken], not this.
     */
    val mediaType: String? = categoryToken?.takeIf { it != BrowseCategoryCatalog.TOKEN_ALL }

    private val _uiState = MutableStateFlow<PhoneResourceUiState>(PhoneResourceUiState.Loading)
    val uiState: StateFlow<PhoneResourceUiState> = _uiState.asStateFlow()

    /**
     * S2136: the page exactly as the phone sent it, and the token it was fetched under.
     *
     * Private for the reason ADR-6 gives on the browse screen: [uiState] publishes the projection,
     * so every reader follows what the wearer sees. Keeping the page here is also what lets a
     * cleared query re-project locally - the alternative is another round trip over the Data Layer,
     * which on this screen costs a visible pause.
     */
    private var loadedItems: List<WearPhoneResourceItem> = emptyList()
    private var loadedParentToken: String? = null

    private val _refineState = MutableStateFlow(BrowseRefineState())
    val refineState: StateFlow<BrowseRefineState> = _refineState.asStateFlow()

    /** The same stored view the general file browser reads, so both lists change together. */
    val fileListViewMode: StateFlow<WearViewMode> = preferencesRepository.fileListViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    /**
     * S1846: the result of the last tap on a file, consumed once by the screen.
     *
     * A one-shot value rather than part of [uiState]: the list behind it does not change when a file
     * opens, and folding a navigation event into the list state would replay the navigation on every
     * recomposition that re-read the state.
     */
    private val _openOutcome = MutableStateFlow<PhoneFileOpenOutcome?>(null)
    val openOutcome: StateFlow<PhoneFileOpenOutcome?> = _openOutcome.asStateFlow()

    /** The run in flight, kept only so a second press cannot start a parallel one (S2142). */
    private var operationJob: Job? = null

    /** What the last file operation came to, or null when there is nothing to report. */
    private val _operationNotice = MutableStateFlow<WearFileOperationOutcome?>(null)
    val operationNotice: StateFlow<WearFileOperationOutcome?> = _operationNotice.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<String, WearThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<String, WearThumbnail>> = _thumbnails.asStateFlow()

    private val inFlightThumbnails = mutableSetOf<String>()

    /**
     * S2129: requests one item's thumbnail on-demand from the phone.
     *
     * A token already requested or in flight is never requested again.
     * Bounded by [MAX_IN_FLIGHT_THUMBNAILS] to avoid bridge decoder congestion.
     */
    fun requestThumbnail(itemToken: String) {
        if (itemToken.isEmpty() || _thumbnails.value.containsKey(itemToken) || inFlightThumbnails.contains(itemToken)) {
            return
        }
        if (inFlightThumbnails.size >= MAX_IN_FLIGHT_THUMBNAILS) return

        inFlightThumbnails.add(itemToken)
        _thumbnails.update { current -> current + (itemToken to WearThumbnail.Loading) }

        viewModelScope.launch {
            try {
                val outcome = phoneResourceClient.requestThumbnail(itemToken)
                val thumbnail = if (outcome is PhoneResourceOutcome.Page) {
                    outcome.page.items.firstOrNull()?.toWearThumbnail() ?: WearThumbnail.Unavailable
                } else {
                    WearThumbnail.Unavailable
                }
                _thumbnails.update { current ->
                    val next = current + (itemToken to thumbnail)
                    val excess = next.size - MAX_CACHED_THUMBNAILS
                    if (excess <= 0) next else next.entries.drop(excess).associate { it.key to it.value }
                }
            } finally {
                inFlightThumbnails.remove(itemToken)
            }
        }
    }

    /**
     * The folders walked into, each beside the name it is titled by, so Back returns one level
     * instead of leaving the screen and the header can name the level without reading the token.
     */
    private val trail = ArrayDeque<FolderLevel>()

    private var decodeJob: Job? = null

    /**
     * S1898: which list the screen currently stands on, bumped by every [load].
     *
     * A transfer runs for up to its 30 s timeout, and walking away does not stop it - cancelling it is
     * deliberately out of this ticket's scope. Clearing the outcome in [load] alone would therefore be
     * undone by the transfer landing afterwards and repainting a pinned row over a folder that never
     * held that file, so the result is published only while the list that asked for it is still shown.
     */
    private var loadGeneration = 0

    init {
        load(parentToken = null)
    }

    fun openFolder(token: String, name: String) {
        trail.addLast(FolderLevel(token = token, name = name))
        load(token)
    }

    /** Returns false when the screen is already at the root and Back should leave it. */
    fun navigateUp(): Boolean {
        val leaving = trail.removeLastOrNull() == null
        load(trail.lastOrNull()?.token)
        return !leaving
    }

    fun retry() {
        load(trail.lastOrNull()?.token)
    }

    /**
     * Asks the phone to deliver [entry] and hands the delivered copy to the players.
     *
     * The transfer lands in the cache directory, and the directory is trimmed back under
     * [PHONE_FILE_CACHE_CAP_BYTES] on every arrival: a phone file opened on the watch is a
     * convenience copy, not a download the user manages, so it must not accumulate as clutter the
     * user has to find and delete. Trimming happens here rather than on a schedule because this is the
     * only moment the directory grows - a periodic job would wake the watch for a directory that did
     * not change (S2004 ADR-6).
     *
     * S2092: an entry the watch cannot render is refused before the transfer, not after it. The phone
     * nulls the type of everything outside the three renderable families, so the answer arrived with
     * the list - fetching first spent a whole file over Bluetooth and a permanent cache entry to learn
     * what the row already said. The guard lives here rather than in the caller so no later caller can
     * reintroduce the wasted round trip.
     */
    fun openFile(entry: WearPhoneResourceItem) {
        if (entry.mimeType == null) {
            _openOutcome.value = PhoneFileOpenOutcome.Unsupported
            return
        }
        _openOutcome.value = PhoneFileOpenOutcome.Opening
        val openedFrom = loadGeneration
        viewModelScope.launch {
            val destination = File(cacheDir, entry.token.toCacheFileName(entry.name))
            val result = when (val outcome = phoneResourceClient.open(entry.token, destination)) {
                is PhoneResourceOutcome.Transferred -> {
                    evictOlderCopies(outcome.file)
                    handOver(entry, outcome.file)
                }
                is PhoneResourceOutcome.Rejected -> PhoneFileOpenOutcome.Failed(outcome.status)
                else -> PhoneFileOpenOutcome.Failed(null)
            }
            if (openedFrom == loadGeneration) {
                _openOutcome.value = result
            }
        }
    }

    /**
     * Trims the copy directory, sparing the file that has just arrived.
     *
     * Off the main thread: the evictor stats and deletes real files. `keep` is what makes the cap safe
     * at any size - the newest arrival is never the one removed, so a large file cannot be evicted
     * between landing and being read.
     */
    private suspend fun evictOlderCopies(justWritten: File) = withContext(Dispatchers.IO) {
        MediaCacheEvictor.evictOldestUntilUnderCap(
            cacheDir = cacheDir,
            keep = justWritten,
            capBytes = PHONE_FILE_CACHE_CAP_BYTES
        )
    }

    /** The screen calls this once it has acted on the outcome, so a rotation does not open twice. */
    fun consumeOpenOutcome() {
        _openOutcome.value = null
    }

    /**
     * What the watch may do with [entry] right now.
     *
     * Two answers about two different files, unioned. The copy-based operations are about the watch's
     * own copy and exist only once one has been fetched. Opening on the phone is about the phone's
     * original, which the browse token addresses whether or not this watch ever fetched anything -
     * the request carries the token and the display name and moves no bytes at all (S2092). Gating it
     * on a copy it never reads is what left the one action that can succeed reachable only after a
     * transfer that was going to be refused.
     */
    fun allowedOperationsFor(entry: WearPhoneResourceItem): Set<WearFileOperationKind> {
        val destination = destinationFor(entry)
        val onTheCopy = if (destination.exists()) {
            capabilityPolicy.allowedOperations(
                capabilityPolicy.classify(entry.toWatchFile(destination), isNetworkSource = false)
            )
        } else {
            emptySet()
        }
        return onTheCopy + WearFileOperationKind.OPEN_ON_PHONE
    }

    /**
     * S2142: the receivers [entry]'s fetched copy may be handed to, through the shared filter.
     *
     * Reached only once that copy exists, because [allowedOperationsFor] withholds the whole entry
     * until it does - a receiver takes bytes, and the phone's original is not on this watch.
     */
    fun sendToReceiversFor(entry: WearPhoneResourceItem): List<WearSendToReceiverEntry> =
        WearSendToReceiverFilter.apply(
            sendToReceiversRepository.observe().value,
            listOf(actionTargetFor(entry))
        )

    /**
     * Runs [operation] over the watch's copy of [entry] and reports what came of it.
     *
     * The list itself never changes: it shows what the phone holds, and this acts on the watch's copy
     * of one entry. So the outcome line is the only feedback there is, and staying silent would leave
     * a delete indistinguishable from a menu that did nothing.
     */
    fun runOperation(entry: WearPhoneResourceItem, operation: WearFileOperation) {
        // S2142: a second press while the first run is still going is ignored rather than started
        // beside it - a send through the phone hands bytes over in its middle, so a parallel run is
        // how one file reaches a receiver twice.
        if (operationJob?.isActive == true) {
            Timber.i("Wear file operation ignored: a run is already in progress")
            return
        }
        val local = actionTargetFor(entry)
        operationJob = viewModelScope.launch {
            performFileOperation(listOf(local), operation, isNetworkSource = false).collect { result ->
                _operationNotice.value = result.outcome
            }
        }
    }

    /** Called once the screen has shown the notice, so it does not outlive the action it reports. */
    fun consumeOperationNotice() {
        _operationNotice.value = null
    }

    /** The file the action menu acts on: the watch's copy of [entry], fetched or not yet. */
    fun actionTargetFor(entry: WearPhoneResourceItem): WearMediaFile =
        entry.toWatchFile(destinationFor(entry))

    /**
     * Where a copy of [entry] lives, or would live once one arrives.
     *
     * The destination is recomputed exactly as [openFile] computes it, so the two cannot drift apart
     * into a menu acting on a path the transfer never wrote.
     */
    private fun destinationFor(entry: WearPhoneResourceItem): File =
        File(cacheDir, entry.token.toCacheFileName(entry.name))

    /**
     * The entry as the file the operations address, whether or not [destination] has been written yet.
     *
     * Its size is read off the copy and so reads zero until one lands. Only the copy-based operations
     * consult it, and those are offered only once the copy exists.
     */
    private fun WearPhoneResourceItem.toWatchFile(destination: File): WearMediaFile = WearMediaFile(
        id = token.hashCode().toLong(),
        name = name,
        uri = Uri.fromFile(destination),
        mimeType = mimeType,
        size = destination.length(),
        dateModified = 0L
    )

    /**
     * The players address a file by id and read it through [SelectedMediaManager], the same hand-off the
     * general browser uses for a network file - a phone file is network-shaped in exactly that sense: it
     * has no MediaStore row for a player to look up.
     */
    private fun handOver(entry: WearPhoneResourceItem, delivered: File): PhoneFileOpenOutcome {
        val mime = entry.mimeType ?: return PhoneFileOpenOutcome.Unsupported
        val file = WearMediaFile(
            id = entry.token.hashCode().toLong(),
            name = entry.name,
            uri = Uri.fromFile(delivered),
            mimeType = mime,
            size = entry.sizeBytes ?: delivered.length(),
            dateModified = 0L
        )
        selectedMediaManager.selectFile(file = file, isNetworkSource = false)
        return PhoneFileOpenOutcome.Ready(fileId = file.id, mimeType = mime)
    }

    /** The token is a path, and a path is not a file name; the id keeps the copy unique per item. */
    private fun String.toCacheFileName(displayName: String): String = "${hashCode()}-$displayName"

    private fun load(parentToken: String?) {
        // S1898: a refusal belongs to the list it was raised on. The line is anchored to the screen
        // now instead of scrolling away with the list, so without this it would follow the user into
        // the next folder and name a file that folder does not contain.
        loadGeneration++
        _openOutcome.value = null
        _uiState.value = PhoneResourceUiState.Loading
        // Tokens are per folder, so keeping the previous page's pictures would only hold bitmaps
        // no cell can ask for again.
        inFlightThumbnails.clear()
        _thumbnails.value = emptyMap()
        val isFlat = BrowseCategoryCatalog.shapeForToken(categoryToken) == WearListShape.FLAT_MEDIA
        viewModelScope.launch {
            val outcome = phoneResourceClient.browse(parentToken, mediaType = mediaType, isFlat = isFlat)
            _uiState.value = when (outcome) {
                is PhoneResourceOutcome.Page -> {
                    decodeThumbnails(outcome.page.items)
                    outcome.page.items.toState(parentToken)
                }
                // S2130: one refusal is not a failure. The phone answered, correctly, that no
                // configured resource carries this category - so it gets its own state rather than
                // the Unavailable copy, which offers a Retry that would return the same answer.
                is PhoneResourceOutcome.Rejected -> if (outcome.status == NO_RESOURCE_FOR_TYPE) {
                    PhoneResourceUiState.NoResourceForType
                } else {
                    PhoneResourceUiState.Unavailable(outcome.status)
                }
                is PhoneResourceOutcome.PhoneNotPaired -> PhoneResourceUiState.NotPaired
                else -> {
                    Timber.d("Paired phone did not answer a browse request")
                    PhoneResourceUiState.Unavailable(null)
                }
            }
        }
    }

    /**
     * Decoding runs beside the list rather than ahead of it: a page of bitmap parses would
     * otherwise delay the folder appearing, and a cell shows its type icon meanwhile anyway.
     */
    private fun decodeThumbnails(items: List<WearPhoneResourceItem>) {
        // A folder left behind must not finish decoding and overwrite the folder now on screen with
        // pictures keyed to tokens no cell here asks for.
        decodeJob?.cancel()
        decodeJob = viewModelScope.launch(Dispatchers.Default) {
            _thumbnails.value = items.associate { it.token to it.toWearThumbnail() }
        }
    }

    /**
     * The title of the level the screen stands on: the folder's own name inside the walk, and the name
     * the opening chip gave the screen at the root, where no folder name exists.
     */
    private fun currentTitle(): ScreenTitle {
        val level = trail.lastOrNull()
        return if (level == null) {
            ScreenTitle.Resource(titleResFor(categoryToken))
        } else {
            ScreenTitle.Text(level.name)
        }
    }

    private fun List<WearPhoneResourceItem>.toState(parentToken: String?): PhoneResourceUiState {
        loadedItems = this
        loadedParentToken = parentToken
        return refinedState()
    }

    /**
     * S2136: how to read the refine keys off a phone-folder entry.
     *
     * [BrowseRefineKeys.dateModified] is left null on purpose - the wire model carries no date at
     * all, so ADR-5 drops the two date orders from this screen's dialog without a per-screen branch
     * anywhere in that dialog. A directory is its own content type before the mime type is
     * consulted, because the phone sends none for a folder.
     */
    private fun refineKeys(): BrowseRefineKeys<WearPhoneResourceItem> = BrowseRefineKeys(
        name = WearPhoneResourceItem::name,
        contentType = { item ->
            if (item.isDirectory) {
                WearContentType.FOLDER
            } else {
                contentTypeForMime(item.mimeType) ?: WearContentType.OTHER
            }
        },
        sizeBytes = WearPhoneResourceItem::sizeBytes
    )

    /** S2136: the content types present on the loaded page - the filter icon's own condition. */
    fun presentContentTypes(): List<WearContentType> =
        BrowseListProjection.presentTypes(loadedItems, refineKeys())

    /** S2136: five orders here, not seven - the date pair has no key to sort by (ADR-5). */
    fun availableSortOrders(): List<BrowseSortOrder> = refineKeys().availableSortOrders()

    fun setSearchQuery(query: String) = updateRefine { it.copy(searchQuery = query) }

    fun setContentTypes(types: Set<WearContentType>) = updateRefine { it.copy(contentTypes = types) }

    fun setSortOrder(order: BrowseSortOrder) = updateRefine { it.copy(sortOrder = order) }

    fun setShowRefineMenu(show: Boolean) = updateRefine { it.copy(showRefineMenu = show) }

    fun setSearchInputUnavailable(unavailable: Boolean) =
        updateRefine { it.copy(searchInputUnavailable = unavailable) }

    /**
     * S2136: every refine setter's one path - re-project, never re-request.
     *
     * Nothing here reaches [phoneResourceClient]: strategic goal 5 requires a refine choice to
     * apply without touching the source, and this screen's source is the phone over the Data Layer.
     */
    private fun updateRefine(transform: (BrowseRefineState) -> BrowseRefineState) {
        _refineState.value = transform(_refineState.value)
        // Only the states the projection itself produces may be re-projected. A refine choice made
        // before the first page landed has nothing to project yet, and re-projecting a state that
        // reports why there is no list - S2130's NoResourceForType, or an Unavailable - would replace
        // the reason with a bare "empty" derived from a page that never arrived.
        if (_uiState.value.isProjected()) {
            _uiState.value = refinedState()
        }
    }

    /** Whether [refinedState] is what produced this state, and so may recompute it. */
    private fun PhoneResourceUiState.isProjected(): Boolean =
        this is PhoneResourceUiState.Content ||
            this is PhoneResourceUiState.NoMatches ||
            this is PhoneResourceUiState.Empty

    private fun refinedState(): PhoneResourceUiState {
        if (loadedItems.isEmpty()) return PhoneResourceUiState.Empty
        val shown = BrowseListProjection.refine(loadedItems, refineKeys(), _refineState.value)
        return if (shown.isEmpty()) {
            PhoneResourceUiState.NoMatches
        } else {
            PhoneResourceUiState.Content(
                items = shown,
                parentToken = loadedParentToken,
                title = currentTitle()
            )
        }
    }
}
