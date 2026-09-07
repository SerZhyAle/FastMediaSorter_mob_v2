package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellOrigin
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Mirrors genuine package install and removal events into automatically managed launcher cells. */
class SyncInstalledAppShortcutUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val launcherMode: LauncherModeContract,
    private val roleManager: LauncherRoleManager,
) {
    private val syncMutex = Mutex()

    enum class Change {
        INSTALLED,
        REMOVED,
    }

    suspend operator fun invoke(packageName: String, change: Change) = withContext(Dispatchers.IO) {
        if (!launcherMode.isAvailableInBuild || !roleManager.isHomeRoleHeld()) return@withContext
        syncMutex.withLock {
            when (change) {
                Change.INSTALLED -> addShortcuts(packageName)
                Change.REMOVED -> removeShortcuts(packageName)
            }
        }
    }

    private suspend fun addShortcuts(packageName: String) {
        val state = desktop.state()
        LauncherOrientation.entries.forEach { orientation ->
            val columns = when (orientation) {
                LauncherOrientation.PORTRAIT -> state.columnsPortrait
                LauncherOrientation.LANDSCAPE -> state.columnsLandscape
            }
            if (columns < MIN_COLUMNS) return@forEach
            val target = LauncherCellCommand.App(packageName).encode()
            val existing = desktop.observeCells(orientation).first()
            if (existing.any { it.target == target }) return@forEach
            desktop.addCellInFirstFreeSlot(
                cell = LauncherCell(
                    id = NEW_CELL_ID,
                    orientation = orientation,
                    rowIndex = FIRST_ROW,
                    colIndex = FIRST_COLUMN,
                    spanW = SHORTCUT_SPAN,
                    spanH = SHORTCUT_SPAN,
                    kind = LauncherCellKind.SHORTCUT,
                    target = target,
                    labelOverride = null,
                    addedAt = System.currentTimeMillis(),
                    screenIndex = FIRST_SCREEN,
                    origin = LauncherCellOrigin.AUTO_INSTALL,
                ),
                columns = columns,
            )
        }
    }

    private suspend fun removeShortcuts(packageName: String) {
        val target = LauncherCellCommand.App(packageName).encode()
        LauncherOrientation.entries.forEach { orientation ->
            desktop.observeCells(orientation)
                .first()
                .filter { it.target == target && it.origin == LauncherCellOrigin.AUTO_INSTALL }
                .forEach { desktop.removeCell(it.id) }
        }
    }

    private companion object {
        const val MIN_COLUMNS = 1
        const val NEW_CELL_ID = 0L
        const val FIRST_ROW = 0
        const val FIRST_COLUMN = 0
        const val FIRST_SCREEN = 0
        const val SHORTCUT_SPAN = 1
    }
}
