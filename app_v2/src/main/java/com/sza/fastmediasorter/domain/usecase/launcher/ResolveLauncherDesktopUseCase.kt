package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: turns stored desktop cells into renderable ones for a single orientation, resolving each
 * shortcut's label and icon. Mirrors `ResolveAppLaunchPanelTilesUseCase`: all resolution happens
 * here, off the main thread, and an unresolvable target degrades to a null visual instead of
 * throwing - the grid draws it as unavailable so the user can see and remove it.
 */
class ResolveLauncherDesktopUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
    private val radioControl: RadioControlContract,
) {

    /**
     * S1441: the radio states are combined in rather than read once, because a radio switched from the
     * system shade has to reach the tile too - the stored cell list alone re-emits only on a database write.
     * The resulting [LauncherCellUi] carries a different `iconRes`, which is what gets it past the binder's
     * own equality guard.
     */
    operator fun invoke(orientation: LauncherOrientation): Flow<List<LauncherCellUi>> =
        combine(
            desktopRepository.observeCells(orientation),
            radioControl.state(RadioKind.WIFI),
            radioControl.state(RadioKind.BLUETOOTH),
        ) { cells, wifi, bluetooth ->
            val radioStates = RadioStates(wifi, bluetooth)
            Timber.d("S1441: desktop re-resolve, wifi=%s, bluetooth=%s", wifi, bluetooth)
            cells.map { it.toUi(radioStates) }
        }.flowOn(Dispatchers.IO)

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
