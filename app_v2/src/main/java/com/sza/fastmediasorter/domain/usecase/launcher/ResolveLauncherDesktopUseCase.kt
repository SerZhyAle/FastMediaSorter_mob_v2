package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.launcher.LiveContactDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: turns stored desktop cells into renderable ones for a single orientation, resolving each
 * shortcut's label and icon. Mirrors `ResolveAppLaunchPanelTilesUseCase`: all resolution happens
 * here, off the main thread, and an unresolvable target degrades to a null visual instead of
 * throwing. The disabled Network Monitor is the deliberate exception: its shortcut is hidden
 * everywhere until the user re-enables that opt-in feature.
 */
class ResolveLauncherDesktopUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
    private val radioControl: RadioControlContract,
    private val liveContactDataSource: LiveContactDataSource,
    private val settingsRepository: SettingsRepository,
    private val networkMonitorContract: NetworkMonitorContract,
) {

    /**
     * S1441: the radio states are combined in rather than read once, because a radio switched from the
     * system shade has to reach the tile too - the stored cell list alone re-emits only on a database write.
     * The resulting [LauncherCellUi] carries a different `iconRes`, which is what gets it past the binder's
     * own equality guard.
     *
     * S1206: the address-book signal is combined in for the same reason - a contact renamed in the system
     * writes nothing of ours, so without this input a pinned cell would keep the caption it was pinned with
     * until some unrelated desktop edit happened to rebuild the list.
     */
    operator fun invoke(orientation: LauncherOrientation): Flow<List<LauncherCellUi>> =
        combine(
            desktopRepository.observeCells(orientation),
            radioControl.state(RadioKind.WIFI),
            radioControl.state(RadioKind.BLUETOOTH),
            liveContactDataSource.changes(),
            settingsRepository.getSettings()
                .map { it.enableNetworkMonitor }
                .distinctUntilChanged(),
        ) { cells, wifi, bluetooth, _, networkMonitorEnabled ->
            val radioStates = RadioStates(wifi, bluetooth)
            cells.filterNot { it.hidesWithDisabledNetworkMonitor(networkMonitorEnabled) }
                .map { it.toUi(radioStates) }
        }.flowOn(Dispatchers.IO)

    private fun LauncherCell.hidesWithDisabledNetworkMonitor(networkMonitorEnabled: Boolean): Boolean {
        val command = LauncherCellCommand.decode(target) as? LauncherCellCommand.Feature
        return command?.routeKey == InternalRouteCatalog.KEY_NETWORK_MONITOR &&
            (!networkMonitorContract.isAvailableInBuild || !networkMonitorEnabled)
    }

    private suspend fun LauncherCell.toUi(radioStates: RadioStates): LauncherCellUi {
        // A gadget carries no command, and an undecodable target has none left; both render as an
        // unavailable cell, which is what a null command produces below without a branch of its own.
        val command = if (kind == LauncherCellKind.GADGET) null else LauncherCellCommand.decode(target)
        val resolved = command?.let { resolveVisual(it, radioStates) }
        // A user-set caption always wins over the resolved one (app-launch panel precedent).
        val visual = resolved?.let { base ->
            labelOverride?.let { base.withLabel(it) } ?: base
        }
        return LauncherCellUi(
            cell = this,
            visual = visual,
            modeBadge = (command as? LauncherCellCommand.Resource)?.mode,
        )
    }
}
