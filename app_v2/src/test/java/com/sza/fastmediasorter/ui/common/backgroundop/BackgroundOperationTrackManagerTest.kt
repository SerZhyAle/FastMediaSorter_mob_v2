package com.sza.fastmediasorter.ui.common.backgroundop

import androidx.work.WorkInfo
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferProgressSnapshot
import com.sza.fastmediasorter.ui.browse.transfer.transferBytePercentOrNull
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundOperationTrackManagerTest {

    @Test
    fun `inactive work maps to Hidden`() = runTest {
        val state = BrowseFileTransferCoordinator.TransferWorkState(
            workId = "w",
            state = WorkInfo.State.SUCCEEDED,
        )

        assertEquals(BackgroundOperationBarState.Hidden, managerFor(state).barState().first())
    }

    @Test
    fun `active work without a snapshot maps to Indeterminate`() = runTest {
        val state = BrowseFileTransferCoordinator.TransferWorkState(
            workId = "w",
            state = WorkInfo.State.RUNNING,
            progress = null,
        )

        assertEquals(BackgroundOperationBarState.Indeterminate, managerFor(state).barState().first())
    }

    @Test
    fun `active work with an unknown total maps to Indeterminate`() = runTest {
        val state = BrowseFileTransferCoordinator.TransferWorkState(
            workId = "w",
            state = WorkInfo.State.RUNNING,
            progress = snapshot(completedOperationBytes = 512L, totalOperationBytes = 0L),
        )

        assertEquals(BackgroundOperationBarState.Indeterminate, managerFor(state).barState().first())
    }

    @Test
    fun `active work with a known total maps to the same percent the dialog reads`() = runTest {
        val completed = 250L
        val total = 1000L
        val state = BrowseFileTransferCoordinator.TransferWorkState(
            workId = "w",
            state = WorkInfo.State.RUNNING,
            progress = snapshot(completedOperationBytes = completed, totalOperationBytes = total),
        )

        // Asserted against the formula itself, never a literal: the point of the test is that the
        // bar and the progress dialog cannot drift apart, not that the percent happens to be 25.
        val expected = transferBytePercentOrNull(
            completedOperationBytes = completed,
            totalOperationBytes = total,
        )
        assertEquals(
            BackgroundOperationBarState.Determinate(checkNotNull(expected)),
            managerFor(state).barState().first(),
        )
    }

    private fun managerFor(
        state: BrowseFileTransferCoordinator.TransferWorkState,
    ): BackgroundOperationTrackManager {
        val coordinator = mockk<BrowseFileTransferCoordinator>()
        every { coordinator.activeTransferFlow() } returns flowOf(state)
        return BackgroundOperationTrackManager(coordinator)
    }

    private fun snapshot(
        completedOperationBytes: Long,
        totalOperationBytes: Long,
    ): BrowseFileTransferProgressSnapshot = BrowseFileTransferProgressSnapshot(
        operationType = FileOperationType.COPY,
        totalFiles = 4,
        currentIndex = 1,
        currentFile = "a.jpg",
        bytesTransferred = completedOperationBytes,
        totalBytes = totalOperationBytes,
        speedBytesPerSecond = 0L,
        completedOperationBytes = completedOperationBytes,
        totalOperationBytes = totalOperationBytes,
    )
}
