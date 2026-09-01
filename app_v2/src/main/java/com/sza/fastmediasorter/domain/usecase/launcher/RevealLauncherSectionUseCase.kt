package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherSectionVisibilityRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * S2061: opens the section a blindly placed cell landed in, when that section was folded shut.
 */
class RevealLauncherSectionUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
    private val visibility: LauncherSectionVisibilityRepository,
) {

    suspend operator fun invoke(cellId: Long, orientation: LauncherOrientation): Boolean {
        val cells = withTimeoutOrNull(REVEAL_TIMEOUT_MS) {
            desktopRepository.observeCells(orientation).first { list -> list.any { it.id == cellId } }
        }.orEmpty()

        val placed = cells.firstOrNull { it.id == cellId }
        val owner = placed?.let { cell ->
            // S2317: membership is decided per screen. Ranking every screen's cells by row and column
            // would let a header on an earlier screen own a cell it is not even drawn beside.
            val sameScreen = cells.filter { it.screenIndex == cell.screenIndex }
            LauncherSectionMembership.ownerOf(cell, LauncherSectionMembership.sectionsInOrder(sameScreen))
        }

        if (owner != null && visibility.isCollapsed(orientation, owner.screenIndex, owner.target)) {
            Timber.d("S2061: Revealing section %s for placed cell %d", owner.target, cellId)
            visibility.reveal(orientation, owner.screenIndex, owner.target)
            return true
        }

        return false
    }

    private companion object {
        /** Bounded timeout waiting for the desktop stream to reflect the newly inserted cell. */
        const val REVEAL_TIMEOUT_MS = 2_000L
    }
}
