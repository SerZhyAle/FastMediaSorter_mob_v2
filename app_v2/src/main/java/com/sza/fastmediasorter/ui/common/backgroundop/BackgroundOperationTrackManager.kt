package com.sza.fastmediasorter.ui.common.backgroundop

import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.transferBytePercentOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped view of "is background work running, and how far along".
 *
 * Subscribes to the existing transfer work state rather than querying the work scheduler again, so
 * showing the bar cannot raise the publish rate (S1398 §11.6). Reads the same nullable percent form
 * the progress dialog reads, so the two can never disagree while the total is still unknown.
 */
@Singleton
class BackgroundOperationTrackManager @Inject constructor(
    private val browseTransferCoordinator: BrowseFileTransferCoordinator,
) {

    fun barState(): Flow<BackgroundOperationBarState> =
        browseTransferCoordinator.activeTransferFlow()
            .map { toBarState(it) }
            .distinctUntilChanged()

    private fun toBarState(
        state: BrowseFileTransferCoordinator.TransferWorkState,
    ): BackgroundOperationBarState = when {
        !state.isActive -> BackgroundOperationBarState.Hidden

        // Both "no snapshot yet" and "snapshot without a known total" collapse to the same answer:
        // there is no share to draw, so the bar runs indeterminate exactly while the dialog hides
        // its percent.
        else ->
            state.progress
                ?.let {
                    transferBytePercentOrNull(
                        completedOperationBytes = it.completedOperationBytes,
                        totalOperationBytes = it.totalOperationBytes,
                    )
                }
                ?.let { BackgroundOperationBarState.Determinate(it) }
                ?: BackgroundOperationBarState.Indeterminate
    }
}
