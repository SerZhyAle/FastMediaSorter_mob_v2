package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1761: imports the installed applications onto the launcher desktop grid.
 *
 * S2018 gives the import a destination of its own. Before it, every shortcut went through the
 * repository's "first free slot anywhere" placement, which on a densely filled desktop means the
 * gaps left inside the widgets and resources sections - so a bulk import scattered itself across
 * sections the user had arranged by hand, and whatever did not fit settled under the last header.
 * Everything now lands under one [LauncherCellCommand.SECTION_DESKTOP] header, in alphabetical
 * order, which is the arrangement the user can then redistribute from.
 */
class ImportSystemShortcutsUseCase @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val desktopRepository: LauncherDesktopRepository,
) {
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val apps = installedAppsRepository.observeApps().first()
            if (apps.isEmpty()) return@runCatching false

            val state = desktopRepository.state()
            val portraitCols =
                if (state.columnsPortrait > 0) state.columnsPortrait else DEFAULT_PORTRAIT_COLUMNS
            val landscapeCols =
                if (state.columnsLandscape > 0) state.columnsLandscape else DEFAULT_LANDSCAPE_COLUMNS

            // The cache is deliberately stored unordered - ordering is the consumer's business, and this
            // consumer never stated one, which is why the desktop filled up in enumeration order.
            val ordered = apps.sortedBy { it.label.lowercase() }
            val now = System.currentTimeMillis()
            Timber.d("S2018: importing %d apps into the desktop section", ordered.size)

            importInto(LauncherOrientation.PORTRAIT, portraitCols, ordered, now)
            importInto(LauncherOrientation.LANDSCAPE, landscapeCols, ordered, now)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to import installed applications")
            false
        }
    }

    /**
     * The two orientations are separate desktops with separate grids, so each needs its own header and
     * its own placement pass - a cell placed in one is not visible in the other.
     */
    private suspend fun importInto(
        orientation: LauncherOrientation,
        columns: Int,
        apps: List<InstalledApp>,
        now: Long,
    ) {
        val existing = desktopRepository.observeCells(orientation).first()
        val existingTargets = existing.map { it.target }.toSet()
        ensureDesktopSection(orientation, columns, existing, now)

        apps.forEach { app ->
            val target = LauncherCellCommand.App(app.packageName).encode()
            if (existingTargets.contains(target)) return@forEach
            desktopRepository.addCellInSection(
                cell = LauncherCell(
                    id = 0L,
                    orientation = orientation,
                    rowIndex = 0,
                    colIndex = 0,
                    spanW = 1,
                    spanH = 1,
                    kind = LauncherCellKind.SHORTCUT,
                    target = target,
                    labelOverride = app.label,
                    addedAt = now,
                ),
                columns = columns,
                sectionKey = LauncherCellCommand.SECTION_DESKTOP,
            )
        }
    }

    /**
     * Creates the destination header once, and only when the desktop has none.
     *
     * The key is a fixed literal rather than a minted one precisely so this check can find the header a
     * previous import wrote; a second run therefore refills the same section instead of stacking a
     * second "Desktop" caption on the desktop.
     *
     * [labelOverride] stays null so the caption resolves through [LauncherSectionCatalog][com.sza
     * .fastmediasorter.core.launcher.LauncherSectionCatalog] and follows the device language, which a
     * literal written in here at import time could not.
     */
    private suspend fun ensureDesktopSection(
        orientation: LauncherOrientation,
        columns: Int,
        existing: List<LauncherCell>,
        now: Long,
    ) {
        val sectionTarget = LauncherCellCommand.Section(LauncherCellCommand.SECTION_DESKTOP).encode()
        val alreadyThere = existing.any {
            it.kind == LauncherCellKind.SECTION && it.target == sectionTarget
        }
        if (alreadyThere) return

        desktopRepository.addCellInFirstFreeSlot(
            LauncherCell(
                id = 0L,
                orientation = orientation,
                rowIndex = 0,
                colIndex = 0,
                spanW = LauncherSectionMembership.HEADER_SPAN_W,
                spanH = 1,
                kind = LauncherCellKind.SECTION,
                target = sectionTarget,
                labelOverride = null,
                addedAt = now,
            ),
            columns,
        )
    }

    private companion object {
        const val DEFAULT_PORTRAIT_COLUMNS = 4
        const val DEFAULT_LANDSCAPE_COLUMNS = 6
    }
}
