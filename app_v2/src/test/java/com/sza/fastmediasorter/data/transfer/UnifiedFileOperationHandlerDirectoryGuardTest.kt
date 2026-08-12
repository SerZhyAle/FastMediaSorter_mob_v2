package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.domain.transfer.ProgressTracker
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1325: the pre-flight refusals for whole-tree operations. Each case must fail before the strategy
 * is touched - a refusal that reaches the strategy has already started creating the destination.
 */
class UnifiedFileOperationHandlerDirectoryGuardTest {

    private val localProvider: LocalTransferProvider = mockk(relaxed = true)
    private val tempFileManager: TempFileManager = mockk(relaxed = true)
    private val progressTracker: ProgressTracker = mockk(relaxed = true)
    private val errorHandler: FileOperationErrorHandler = mockk(relaxed = true)
    private val treeTransferManager: DirectoryTreeTransferManager = mockk(relaxed = true)

    private val localStrategy: FileOperationStrategy = mockk()

    private lateinit var handler: UnifiedFileOperationHandler

    @Before
    fun setup() {
        // S1378: the space pre-flight asks the strategy how big the source tree is. Reporting
        // "cannot measure" keeps it on its proceed branch, so these guard assertions stay about
        // routing and nesting rules rather than capacity.
        coEvery { localStrategy.getDirectoryInfo(any()) } returns
            Result.failure(UnsupportedOperationException("not measured in this test"))
        handler = UnifiedFileOperationHandler(
            localProvider = localProvider,
            tempFileManager = tempFileManager,
            progressTracker = progressTracker,
            errorHandler = errorHandler,
            operationStrategies = mapOf("local" to localStrategy),
            remoteSourceGate = mockk<RemoteSourceAvailabilityGate> {
                every { isEnabled(any<RemoteSourceId>()) } returns true
                every { anyCloudEnabled() } returns true
            },
            directoryTreeTransferManager = treeTransferManager,
            // S1378: an unmeasurable destination is the "proceed" branch, so the fit check stays out
            // of the way of these assertions, which are about routing and guards, not capacity.
            getDestinationFreeSpace = mockk {
                coEvery { this@mockk(any()) } returns null
            },
        )
    }

    private fun refusalOf(result: Result<Int>): DirectoryOperationRefusal {
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected DirectoryOperationRefusal, got $error", error is DirectoryOperationRefusal)
        return error as DirectoryOperationRefusal
    }

    @Test
    fun `copy into own subdirectory is refused`() = runTest {
        val refusal = refusalOf(
            handler.executeCopyDirectory("/storage/Trip", "/storage/Trip/Backup"),
        )

        assertEquals(DirectoryOperationRefusal.Reason.DESTINATION_INSIDE_SOURCE, refusal.reason)
        coVerify(exactly = 0) { localStrategy.copyDirectory(any(), any(), any()) }
    }

    @Test
    fun `copy into the source itself is refused`() = runTest {
        val refusal = refusalOf(
            handler.executeCopyDirectory("/storage/Trip", "/storage/Trip"),
        )

        assertEquals(DirectoryOperationRefusal.Reason.DESTINATION_INSIDE_SOURCE, refusal.reason)
    }

    @Test
    fun `sibling with a longer name is not treated as nested`() = runTest {
        coEvery {
            localStrategy.copyDirectory("/storage/Trip", "/storage/TripArchive/Trip", any())
        } returns Result.success(7)

        val result = handler.executeCopyDirectory("/storage/Trip", "/storage/TripArchive")

        assertTrue(result.isSuccess)
        assertEquals(7, result.getOrThrow())
    }

    @Test
    fun `move into the current parent is refused`() = runTest {
        val refusal = refusalOf(
            handler.executeMoveDirectory("/storage/Photos/Trip", "/storage/Photos"),
        )

        assertEquals(DirectoryOperationRefusal.Reason.SAME_LOCATION, refusal.reason)
        coVerify(exactly = 0) { localStrategy.moveDirectory(any(), any(), any()) }
    }

    @Test
    fun `copy into the current parent is refused as self-copy`() = runTest {
        val refusal = refusalOf(
            handler.executeCopyDirectory("/storage/Photos/Trip", "/storage/Photos"),
        )

        assertEquals(DirectoryOperationRefusal.Reason.SAME_LOCATION, refusal.reason)
        coVerify(exactly = 0) { localStrategy.copyDirectory(any(), any(), any()) }
    }

    /**
     * S1378 replaced the assertion this test used to make. The document-tree refusal existed only
     * because no implementation addressed such a destination; ADR-3 requires it to be lifted in the
     * same change that supplies one, so the guard must now let the operation through - and route it
     * to the cross-protocol transfer, never to the local strategy that cannot address `content:`.
     */
    @Test
    fun `document tree destination is no longer refused and routes to the cross-protocol transfer`() = runTest {
        val destinationParent = "content://com.android.externalstorage/tree/primary"
        coEvery {
            treeTransferManager.copyTree("/storage/Trip", "$destinationParent/Trip", any())
        } returns Result.success(4)

        val result = handler.executeCopyDirectory("/storage/Trip", destinationParent)

        assertTrue("the refusal was lifted together with SafOperationStrategy", result.isSuccess)
        assertEquals(4, result.getOrThrow())
        coVerify(exactly = 0) { localStrategy.copyDirectory(any(), any(), any()) }
    }
}
