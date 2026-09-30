package com.sza.fastmediasorter.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPortalLinks
import com.sza.fastmediasorter.wear.domain.repository.PhoneCompanionRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenUrlOnPhoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * S4011: the About row that installs FastMediaSorter on the paired phone.
 *
 * Its own view model rather than two more dependencies of `SettingsViewModel`, which already sits one
 * short of detekt's constructor ceiling; this row reads nothing else that model holds.
 */
@HiltViewModel
class PhoneAppInstallViewModel @Inject constructor(
    private val companionRepository: PhoneCompanionRepository,
    private val openUrlOnPhoneRepository: WearOpenUrlOnPhoneRepository
) : ViewModel() {

    init {
        companionRepository.refresh()
    }

    /** Shown only while a phone is connected without the app - elsewhere the link has nowhere to land. */
    val isOfferAvailable: StateFlow<Boolean> = companionRepository.state
        .map { it == PhoneCompanionState.ABSENT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), false)

    private val _outcome = MutableStateFlow<WearOpenUrlOnPhoneOutcome?>(null)
    val outcome: StateFlow<WearOpenUrlOnPhoneOutcome?> = _outcome.asStateFlow()

    private var busy = false

    fun installOnPhone() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            _outcome.value = openUrlOnPhoneRepository.openOnPhone(WearPortalLinks.PHONE_APP_STORE_URL)
            busy = false
        }
    }
}
