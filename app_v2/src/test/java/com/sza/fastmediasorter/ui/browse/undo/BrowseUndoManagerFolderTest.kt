package com.sza.fastmediasorter.ui.browse.undo

import android.content.Context
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S1326: the folder half of an undo record must reach the transfer layer, and a refusal from it must
 * leave the record alone. The refusal case is the one that matters - the difference between an undo the
 * user can retry once the running transfer finishes and an undo record silently thrown away.
 */
class BrowseUndoManagerFolderTest {

    // Relaxed: every getString returns a stub, because this test asserts what the manager DID, never
    // which message it showed.
    private val context = mockk<Context>(relaxed = true)
    private lateinit var callbacks: RecordingCallbacks
    private lateinit var manager: BrowseUndoManager

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class RecordingCallbacks(
        var enqueueResult: Boolean,
        var confirmResult: Boolean = true,
    ) : BrowseUndoManager.UndoCallbacks {
        var enqueueCalls = 0
        var lastTreePaths: List<String>? = null
        var lastDestinationParent: String? = null
        var confirmCalls = 0
        var deletedTrees: List<String>? = null

        override suspend fun addFilesToList(files: List<MediaFile>) = Unit
        override suspend fun reloadFileList() = Unit
        override fun createMediaFileFromFile(file: File): MediaFile = MediaFile(
            name = file.name,
            path = file.absolutePath,
            type = MediaType.IMAGE,
            size = 0L,
            createdDate = 0L,
        )
        override fun showMessage(message: String) = Unit
        override fun showUndoToast(operationType: String) = Unit
        override fun showError(message: String, details: String?, exception: Throwable?) = Unit
        override suspend fun renameViaFileOperation(currentPath: String, originalName: String): Boolean = true

        override suspend fun enqueueDirectoryUndoTransfer(
            treePaths: List<String>,
            destinationParent: String,
        ): Boolean {
            enqueueCalls++
            lastTreePaths = treePaths
            lastDestinationParent = destinationParent
            return enqueueResult
        }

        override suspend fun deleteDirectoryTrees(treePaths: List<String>): Int {
            deletedTrees = treePaths
            return treePaths.size
        }

        override suspend fun confirmDestructiveUndo(treeCount: Int): Boolean {
            confirmCalls++
            return confirmResult
        }
    }

    private fun buildManager(enqueueResult: Boolean): BrowseUndoManager {
        callbacks = RecordingCallbacks(enqueueResult)
        return BrowseUndoManager(context = context, callbacks = callbacks, statsSink = NoopStatsSink())
    }

    private class NoopStatsSink : StatsSink {
        override fun record(event: StatsEvent) = Unit
        override suspend fun flushNow() = Unit
    }

    private fun folderMoveRecord() = UndoOperation(
        type = FileOperationType.MOVE,
        sourceFiles = emptyList(),
        destinationFolder = "/dst",
        copiedFiles = null,
        sourceDirectories = listOf("/src/tree"),
        copiedDirectories = listOf("/dst/tree"),
    )

    @Test
    fun `folder move record enqueues a reverse transfer to the original parent`() = runTest {
        manager = buildManager(enqueueResult = true)
        manager.saveOperation(folderMoveRecord())

        manager.undoLastOperation()

        assertEquals(1, callbacks.enqueueCalls)
        assertEquals(listOf("/dst/tree"), callbacks.lastTreePaths)
        assertEquals("/src", callbacks.lastDestinationParent)
    }

    @Test
    fun `a refused enqueue keeps the undo record so the user can retry`() = runTest {
        manager = buildManager(enqueueResult = false)
        manager.saveOperation(folderMoveRecord())

        val performed = manager.undoLastOperation()

        assertEquals(false, performed)
        assertNotNull(manager.undoState.value.lastOperation)
    }

    @Test
    fun `an accepted enqueue clears the undo record`() = runTest {
        manager = buildManager(enqueueResult = true)
        manager.saveOperation(folderMoveRecord())

        val performed = manager.undoLastOperation()

        assertTrue(performed)
        assertNull(manager.undoState.value.lastOperation)
    }

    private fun folderCopyRecord() = UndoOperation(
        type = FileOperationType.COPY,
        sourceFiles = emptyList(),
        destinationFolder = "/dst",
        copiedFiles = null,
        sourceDirectories = listOf("/src/tree"),
        copiedDirectories = listOf("/dst/tree"),
    )

    @Test
    fun `a confirmed copy undo deletes the copied trees`() = runTest {
        manager = buildManager(enqueueResult = true)
        callbacks.confirmResult = true
        manager.saveOperation(folderCopyRecord())

        val performed = manager.undoLastOperation()

        assertTrue(performed)
        assertEquals(1, callbacks.confirmCalls)
        assertEquals(listOf("/dst/tree"), callbacks.deletedTrees)
        assertNull(manager.undoState.value.lastOperation)
    }

    @Test
    fun `a declined copy undo deletes nothing and keeps the record`() = runTest {
        manager = buildManager(enqueueResult = true)
        callbacks.confirmResult = false
        manager.saveOperation(folderCopyRecord())

        val performed = manager.undoLastOperation()

        assertEquals(false, performed)
        assertEquals(1, callbacks.confirmCalls)
        assertNull("a declined confirmation must delete nothing", callbacks.deletedTrees)
        assertNotNull(manager.undoState.value.lastOperation)
    }

    @Test
    fun `a file-only record never reaches the directory transfer`() = runTest {
        manager = buildManager(enqueueResult = true)
        manager.saveOperation(
            UndoOperation(
                type = FileOperationType.MOVE,
                sourceFiles = listOf("/src/a.jpg"),
                destinationFolder = "/dst",
                copiedFiles = listOf("/dst/a.jpg"),
            ),
        )

        manager.undoLastOperation()

        assertEquals(0, callbacks.enqueueCalls)
    }

    // A record carrying files AND folders is what BrowseFileTransferWorker really writes when the user
    // selects both. The folder branch used to return before the file branch ran, so the files stayed put
    // while the record was cleared - the user had nothing left to press.

    private fun mixedRecord(type: FileOperationType, movedFile: File, originalFile: File) = UndoOperation(
        type = type,
        sourceFiles = listOf(originalFile.absolutePath),
        destinationFolder = movedFile.parent,
        copiedFiles = listOf(movedFile.absolutePath),
        sourceDirectories = listOf("/src/tree"),
        copiedDirectories = listOf("/dst/tree"),
    )

    @Test
    fun `a mixed move record reverses the folder half and the file half`() = runTest {
        manager = buildManager(enqueueResult = true)
        val movedFile = tempFolder.newFile("moved.jpg")
        val originalFile = File(tempFolder.root, "original.jpg")
        manager.saveOperation(mixedRecord(FileOperationType.MOVE, movedFile, originalFile))

        val performed = manager.undoLastOperation()

        assertTrue(performed)
        assertEquals(1, callbacks.enqueueCalls)
        assertTrue("the file half must run alongside the folder half", originalFile.exists())
        assertFalse(movedFile.exists())
    }

    @Test
    fun `a refused enqueue in a mixed record leaves the files untouched`() = runTest {
        manager = buildManager(enqueueResult = false)
        val movedFile = tempFolder.newFile("moved.jpg")
        val originalFile = File(tempFolder.root, "original.jpg")
        manager.saveOperation(mixedRecord(FileOperationType.MOVE, movedFile, originalFile))

        val performed = manager.undoLastOperation()

        assertFalse(performed)
        assertTrue("a refused folder undo must not half-apply the file half", movedFile.exists())
        assertFalse(originalFile.exists())
        assertNotNull(manager.undoState.value.lastOperation)
    }

    @Test
    fun `a confirmed mixed copy undo removes both the copied files and the copied trees`() = runTest {
        manager = buildManager(enqueueResult = true)
        callbacks.confirmResult = true
        val copiedFile = tempFolder.newFile("copy.jpg")
        manager.saveOperation(mixedRecord(FileOperationType.COPY, copiedFile, File(tempFolder.root, "src.jpg")))

        val performed = manager.undoLastOperation()

        assertTrue(performed)
        assertEquals(listOf("/dst/tree"), callbacks.deletedTrees)
        assertFalse("the copied file half must be removed too", copiedFile.exists())
    }

    @Test
    fun `a declined mixed copy undo leaves the copied files in place`() = runTest {
        manager = buildManager(enqueueResult = true)
        callbacks.confirmResult = false
        val copiedFile = tempFolder.newFile("copy.jpg")
        manager.saveOperation(mixedRecord(FileOperationType.COPY, copiedFile, File(tempFolder.root, "src.jpg")))

        val performed = manager.undoLastOperation()

        assertFalse(performed)
        assertNull(callbacks.deletedTrees)
        assertTrue("declining the folder delete must not delete the files either", copiedFile.exists())
        assertNotNull(manager.undoState.value.lastOperation)
    }

    @Test
    fun `a record replayed with an old completion time is already expired`() = runTest {
        manager = buildManager(enqueueResult = true)
        val oneMinuteAgo = System.currentTimeMillis() - 60_000L

        manager.saveOperation(folderMoveRecord().copy(timestamp = oneMinuteAgo))

        assertFalse("re-stamping the window would offer a stale destructive undo", manager.isUndoAvailable())
    }
}
