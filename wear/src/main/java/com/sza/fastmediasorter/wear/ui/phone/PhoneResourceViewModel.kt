package com.sza.fastmediasorter.wear.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
    private val phoneResourceClient: PhoneResourceClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhoneResourceUiState>(PhoneResourceUiState.Loading)
    val uiState: StateFlow<PhoneResourceUiState> = _uiState.asStateFlow()

    /** Tokens of the folders walked into, so Back returns one level instead of leaving the screen. */
    private val trail = ArrayDeque<String>()

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
        viewModelScope.launch {
            _uiState.value = when (val outcome = phoneResourceClient.browse(parentToken)) {
                is PhoneResourceOutcome.Page -> outcome.page.items.toState(parentToken)
                is PhoneResourceOutcome.Rejected -> PhoneResourceUiState.Unavailable(outcome.status)
                else -> {
                    Timber.d("Paired phone did not answer a browse request")
                    PhoneResourceUiState.Unavailable(null)
                }
            }
        }
    }

    private fun List<WearPhoneResourceItem>.toState(parentToken: String?): PhoneResourceUiState =
        if (isEmpty()) {
            PhoneResourceUiState.Empty
        } else {
            PhoneResourceUiState.Content(items = this, parentToken = parentToken)
        }
}
