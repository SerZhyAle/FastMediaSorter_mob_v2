package com.sza.fastmediasorter.wear.ui.home.helpers

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPortalLinks
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.PhoneCompanionRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenUrlOnPhoneRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** S4011: what the home catalog needs to know about the phone, in one emission. */
data class PhoneCompanionInputs(
    val state: PhoneCompanionState,
    val hasNetworkSources: Boolean
)

/**
 * S4011: the home screen's view of the paired phone - the inputs its rows follow and the one-time
 * offer to install FastMediaSorter there.
 *
 * One injected helper rather than four more view-model dependencies: the home view model sits just
 * under detekt's constructor ceiling, and these four only ever serve this one concern.
 */
class PhoneCompanionPromptManager @Inject constructor(
    private val companionRepository: PhoneCompanionRepository,
    // Lazy for S3368's reason: the network-source repository builds protocol stacks, and a flavor with
    // no Resources row never needs to know whether a source exists.
    private val networkSourceRepository: Lazy<NetworkSourceRepository>,
    private val preferencesRepository: WearPreferencesRepository,
    private val openUrlOnPhoneRepository: WearOpenUrlOnPhoneRepository,
    capabilities: WearRestrictedCapabilities
) {

    private val offersPhoneRows = capabilities.offersRemoteSources || capabilities.offersContentTransfer
    private val offersRemoteSources = capabilities.offersRemoteSources

    val inputs: Flow<PhoneCompanionInputs> = combine(
        companionRepository.state,
        if (offersRemoteSources) {
            networkSourceRepository.get().observeSources().map { it.isNotEmpty() }
        } else {
            flowOf(false)
        }
    ) { state, hasSources -> PhoneCompanionInputs(state, hasSources) }

    /**
     * True once per install at most: the first ABSENT answer after the welcome walk, until either
     * button is pressed. The welcome comes first because a dialog over it would compete with the
     * permission prompts the user is being walked through.
     */
    val showInstallOffer: Flow<Boolean> = combine(
        companionRepository.state,
        preferencesRepository.onboardingCompleted,
        preferencesRepository.phoneInstallOfferDismissed
    ) { state, onboarded, dismissed ->
        offersPhoneRows && onboarded && !dismissed && state == PhoneCompanionState.ABSENT
    }

    /** Asks the Data Layer again; a flavor with no phone-bound row never asks at all. */
    fun refresh() {
        if (offersPhoneRows) companionRepository.refresh()
    }

    suspend fun acceptInstallOffer(): WearOpenUrlOnPhoneOutcome {
        preferencesRepository.setPhoneInstallOfferDismissed(true)
        return openUrlOnPhoneRepository.openOnPhone(WearPortalLinks.PHONE_APP_STORE_URL)
    }

    suspend fun dismissInstallOffer() {
        preferencesRepository.setPhoneInstallOfferDismissed(true)
    }
}
