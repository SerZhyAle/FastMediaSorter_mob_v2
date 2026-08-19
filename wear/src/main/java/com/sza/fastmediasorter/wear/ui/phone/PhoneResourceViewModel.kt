package com.sza.fastmediasorter.wear.ui.phone

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
sealed interface PhoneResourceUiState {

    data object Loading : PhoneResourceUiState

    data class Content(
        val items: List<WearPhoneResourceItem>,
        val parentToken: String?
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
    preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhoneResourceUiState>(PhoneResourceUiState.Loading)
    val uiState: StateFlow<PhoneResourceUiState> = _uiState.asStateFlow()

    /** The same stored view the general file browser reads, so both lists change together. */
    val fileListViewMode: StateFlow<WearViewMode> = preferencesRepository.fileListViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    private val _thumbnails = MutableStateFlow<Map<String, WearThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<String, WearThumbnail>> = _thumbnails.asStateFlow()

    /** Tokens of the folders walked into, so Back returns one level instead of leaving the screen. */
    private val trail = ArrayDeque<String>()

    private var decodeJob: Job? = null

    init {
        load(parentToken = null)
    }

    fun openFolder(token: String) {
        trail.addLast(token)
        load(token)
    }

    /** Returns false when the screen is already at the root and Back should leave it. */
    fun navigateUp(): Boolean {
        val leaving = trail.removeLastOrNull() == null
        load(trail.lastOrNull())
        return !leaving
    }

    fun retry() {
        load(trail.lastOrNull())
    }

    private fun load(parentToken: String?) {
        Timber.d("S1697: watch opening phone resource, parent ${parentToken ?: "root"}")
        _uiState.value = PhoneResourceUiState.Loading
        // Tokens are per folder, so keeping the previous page's pictures would only hold bitmaps
        // no cell can ask for again.
        _thumbnails.value = emptyMap()
        viewModelScope.launch {
            _uiState.value = when (val outcome = phoneResourceClient.browse(parentToken)) {
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

    private fun List<WearPhoneResourceItem>.toState(parentToken: String?): PhoneResourceUiState =
        if (isEmpty()) {
            PhoneResourceUiState.Empty
        } else {
            PhoneResourceUiState.Content(items = this, parentToken = parentToken)
        }
}
