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
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val VIEW_MODE_SUBSCRIPTION_MS = 5_000L

/** S1846: the chip that means "no filter" - the phone reads its absence the same way. */
private const val MEDIA_TYPE_ALL = "all"

/** S1846: where a phone file lands on the watch - a convenience copy the system may reclaim. */
private const val PHONE_FILE_CACHE_DIR = "phone-files"

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

/** What the paired-phone screen is showing right now. */
/**
 * S1846: the screen is titled by the chip that opened it, so a filtered list is not labelled "Phone"
 * like the unfiltered one. The labels are the chips' own strings rather than new keys - a second
 * wording for the same word is how two screens start disagreeing about what they show.
 */
@StringRes
private fun titleResFor(mediaType: String?): Int = when (mediaType) {
    "recents" -> R.string.wear_phone_recents
    "photos" -> R.string.wear_phone_images
    "videos" -> R.string.wear_phone_video
    "music" -> R.string.wear_phone_audio
    "documents" -> R.string.wear_phone_documents
    else -> R.string.phone_resource_title
}

/** One folder of the walked path: the token that reloads it, and the name the header titles it by. */
private data class FolderLevel(val token: String, val name: String)

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
     * No usable answer. [reason] is a protocol status or null when the phone never replied, and the
     * screen turns it into copy - the raw status never reaches the user.
     */
    data class Unavailable(val reason: WearPhoneResourceResponseStatus?) : PhoneResourceUiState
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
    @ApplicationContext context: Context,
    preferencesRepository: WearPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cacheDir: File = File(context.cacheDir, PHONE_FILE_CACHE_DIR).apply { mkdirs() }

    /**
     * S1846: the kind of file this screen was opened for, or null when it was opened unfiltered.
     *
     * Held for the screen's lifetime rather than passed once: it travels on every request of the
     * session - the first load, a step into a folder, Back and Retry alike - because dropping it
     * halfway would widen the list back to everything without the user asking.
     *
     * The unfiltered entrance registers no argument at all, and the "all files" chip sends the one
     * value that means the same thing; both arrive here as null.
     */
    val mediaType: String? = savedStateHandle
        .get<String>(WearRoutes.ARG_MEDIA_TYPE)
        ?.takeIf { it.isNotBlank() && it != MEDIA_TYPE_ALL }

    private val _uiState = MutableStateFlow<PhoneResourceUiState>(PhoneResourceUiState.Loading)
    val uiState: StateFlow<PhoneResourceUiState> = _uiState.asStateFlow()

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

    private val _thumbnails = MutableStateFlow<Map<String, WearThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<String, WearThumbnail>> = _thumbnails.asStateFlow()

    /**
     * The folders walked into, each beside the name it is titled by, so Back returns one level
     * instead of leaving the screen and the header can name the level without reading the token.
     */
    private val trail = ArrayDeque<FolderLevel>()

    private var decodeJob: Job? = null

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
     * The transfer lands in the cache directory: a phone file opened on the watch is a convenience
     * copy, not a download the user manages, so it must not survive as clutter the user has to find
     * and delete.
     */
    fun openFile(entry: WearPhoneResourceItem) {
        _openOutcome.value = PhoneFileOpenOutcome.Opening
        viewModelScope.launch {
            val destination = File(cacheDir, entry.token.toCacheFileName(entry.name))
            _openOutcome.value = when (val outcome = phoneResourceClient.open(entry.token, destination)) {
                is PhoneResourceOutcome.Transferred -> handOver(entry, outcome.file)
                is PhoneResourceOutcome.Rejected -> PhoneFileOpenOutcome.Failed(outcome.status)
                else -> PhoneFileOpenOutcome.Failed(null)
            }
        }
    }

    /** The screen calls this once it has acted on the outcome, so a rotation does not open twice. */
    fun consumeOpenOutcome() {
        _openOutcome.value = null
    }

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
        _uiState.value = PhoneResourceUiState.Loading
        // Tokens are per folder, so keeping the previous page's pictures would only hold bitmaps
        // no cell can ask for again.
        _thumbnails.value = emptyMap()
        val isFlat = mediaType != null && mediaType != MEDIA_TYPE_ALL
        viewModelScope.launch {
            Timber.d("S1869: PhoneResourceViewModel loaded flat list for mediaType: $mediaType")
            val outcome = phoneResourceClient.browse(parentToken, mediaType = mediaType, isFlat = isFlat)
            _uiState.value = when (outcome) {
                is PhoneResourceOutcome.Page -> {
                    decodeThumbnails(outcome.page.items)
                    outcome.page.items.toState(parentToken)
                }
                is PhoneResourceOutcome.Rejected -> PhoneResourceUiState.Unavailable(outcome.status)
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
        Timber.d("S1877: header depth=${trail.size} level=${level?.name}")
        return if (level == null) {
            ScreenTitle.Resource(titleResFor(mediaType))
        } else {
            ScreenTitle.Text(level.name)
        }
    }

    private fun List<WearPhoneResourceItem>.toState(parentToken: String?): PhoneResourceUiState =
        if (isEmpty()) {
            PhoneResourceUiState.Empty
        } else {
            PhoneResourceUiState.Content(items = this, parentToken = parentToken, title = currentTitle())
        }
}
