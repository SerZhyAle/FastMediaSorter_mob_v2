package com.sza.fastmediasorter.ui.launcher.signal.source

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalKind
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1421 ADR-1: the file copy or move running in the background right now.
 *
 * `BrowseFileTransferCoordinator` is a singleton whose `activeTransferFlow()` is already the Browse screen's
 * source of truth, so the strip observes the same state rather than tracking the worker itself.
 */
class FileTransferLauncherSignalSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: BrowseFileTransferCoordinator,
) : LauncherSignalSource {

    /**
     * Written on the collector's coroutine and read from the main thread in [open], so it is `@Volatile` and
     * a single reference rather than a pair of fields: two independent writes could be observed half-applied,
     * and a bare `Long` is not guaranteed to be written atomically at all.
     */
    @Volatile
    private var lastActiveRequest: BrowseFileTransferRequest? = null

    override fun observe(): Flow<List<LauncherSignal>> = coordinator.activeTransferFlow().map { state ->
        if (!state.isActive) {
            lastActiveRequest = null
            return@map emptyList()
        }
        state.request?.let { lastActiveRequest = it }
        listOf(
            LauncherSignal(
                id = SIGNAL_ID,
                kind = LauncherSignalKind.FILE_TRANSFER,
                iconRes = R.drawable.ic_copy,
                label = context.getString(R.string.launcher_signal_transfer),
                detail = state.progress?.let { "${it.currentIndex}/${it.totalFiles}" },
            ),
        )
    }

    /**
     * Opens the Browse screen the transfer was started from and asks it to reattach, which is the same
     * hand-off `BrowseFileOperationsManager` performs when that screen is entered while a transfer runs -
     * so the tap lands on the running operation's own progress, not on a second view of it.
     */
    override fun open(signal: LauncherSignal): Intent? {
        val request = lastActiveRequest
        if (signal.id != SIGNAL_ID || request == null) {
            return null
        }
        return BrowseActivity.createIntent(
            context = context,
            resourceId = request.sourceResourceId,
            initialFolderPath = request.currentBrowsePath,
        ).apply {
            putExtra(BrowseActivity.EXTRA_REATTACH_TRANSFER, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private companion object {
        const val SIGNAL_ID = "file-transfer"
    }
}
