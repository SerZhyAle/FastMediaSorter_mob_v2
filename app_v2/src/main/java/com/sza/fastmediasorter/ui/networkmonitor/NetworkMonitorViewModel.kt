package com.sza.fastmediasorter.ui.networkmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * S1433: whether the Monitor may be shown, and when it may not, which of the two reasons applies.
 *
 * The two refusals are separate states because only one of them the user can act on: a build without the
 * feature has no settings row to send them to, so offering one would be a dead end.
 */
enum class NetworkMonitorAvailability {

    /** Not decided yet - the settings read has not returned. */
    UNKNOWN,

    /** Present in this build and switched on. */
    AVAILABLE,

    /** Present in this build, switched off by the user. */
    DISABLED_IN_SETTINGS,

    /** Not part of this flavor at all. */
    UNSUPPORTED_IN_BUILD,
}

/**
 * S1433: answers whether the Monitor screen may render, for the Activity that hosts it.
 *
 * The gate lives here rather than in the Activity because the host would otherwise field-inject the settings
 * repository, which is domain access from a screen class - CLAUDE.md Rule 3, enforced by the `activity-logic`
 * ratchet. The older screen-programs still do it that way; that is baseline debt, not a pattern to copy.
 *
 * Re-checked continuously rather than once on entry: a panel tile, a pinned shortcut or a launcher cell can
 * outlive the setting that created it, and the setting can also be switched off while this screen is open.
 */
@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    contract: NetworkMonitorContract,
) : ViewModel() {

    private val availableInBuild: Boolean = contract.isAvailableInBuild

    val availability: StateFlow<NetworkMonitorAvailability> = settingsRepository.getSettings()
        .map { settings -> resolve(settings.enableNetworkMonitor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMonitorAvailability.UNKNOWN)

    private fun resolve(enabledInSettings: Boolean): NetworkMonitorAvailability = when {
        !availableInBuild -> NetworkMonitorAvailability.UNSUPPORTED_IN_BUILD
        enabledInSettings -> NetworkMonitorAvailability.AVAILABLE
        else -> NetworkMonitorAvailability.DISABLED_IN_SETTINGS
    }
}
