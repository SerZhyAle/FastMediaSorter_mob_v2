package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [CleanupTrashFoldersUseCase].
 *
 * Cover the canonical `.trash/<ts>/` layout, the legacy `.trash_<ts>/` migration path,
 * TTL semantics, the "delete all" mode (`maxAgeMs = 0L`), and recursive descent.
 */
class CleanupTrashFoldersUseCaseTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val useCase = CleanupTrashFoldersUseCase()

    private fun File.mkdirsOrFail() {
        require(this.mkdirs() || this.isDirectory) { "Failed to create directory $this" }
    }

    private fun writeFile(dir: File, name: String, content: String = "x") {
        File(dir, name).writeText(content)
    }

    @Test
    fun `canonical snapshot older than TTL is deleted`() = runTest {
        val root = temp.newFolder("root")
        val container = File(TrashFolderContract.buildContainerPath(root.absolutePath))
        val oldTs = System.currentTimeMillis() - 10 * 60 * 1000L // 10 minutes ago
        val snapshot = File(TrashFolderContract.buildSnapshotPath(root.absolutePath, oldTs))
        snapshot.mkdirsOrFail()
        writeFile(snapshot, "deleted.txt")

        val deleted = useCase.cleanup(root)

        assertEquals(1, deleted)
        assertFalse(snapshot.exists())
        assertFalse(container.exists()) // empty container removed
    }

    @Test
    fun `canonical snapshot newer than TTL is kept`() = runTest {
        val root = temp.newFolder("root")
        val recentTs = System.currentTimeMillis() - 1_000L // 1 second ago
        val snapshot = File(TrashFolderContract.buildSnapshotPath(root.absolutePath, recentTs))
        snapshot.mkdirsOrFail()
        writeFile(snapshot, "deleted.txt")

        val deleted = useCase.cleanup(root)

        assertEquals(0, deleted)
        assertTrue(snapshot.exists())
    }

    @Test
    fun `legacy snapshot older than TTL is deleted as migration`() = runTest {
        val root = temp.newFolder("root")
        val oldTs = System.currentTimeMillis() - 10 * 60 * 1000L
        val legacyDir = File(root, ".trash_$oldTs")
        legacyDir.mkdirsOrFail()
        writeFile(legacyDir, "legacy.txt")

        val deleted = useCase.cleanup(root)

        assertEquals(1, deleted)
        assertFalse(legacyDir.exists())
    }

    @Test
    fun `maxAgeMs zero deletes canonical and legacy regardless of age`() = runTest {
        val root = temp.newFolder("root")
        val recentTs = System.currentTimeMillis() - 1_000L
        val canonicalSnapshot = File(TrashFolderContract.buildSnapshotPath(root.absolutePath, recentTs))
        canonicalSnapshot.mkdirsOrFail()
        writeFile(canonicalSnapshot, "fresh.txt")

        val legacyDir = File(root, ".trash_$recentTs")
        legacyDir.mkdirsOrFail()
        writeFile(legacyDir, "legacy.txt")

        val deleted = useCase.cleanup(root, maxAgeMs = 0L)

        assertEquals(2, deleted)
        assertFalse(canonicalSnapshot.exists())
        assertFalse(legacyDir.exists())
    }

    @Test
    fun `files outside trash directories are untouched`() = runTest {
        val root = temp.newFolder("root")
        val regular = File(root, "photo.jpg")
        regular.writeText("payload")
        val nestedDir = File(root, "album")
        nestedDir.mkdirsOrFail()
        writeFile(nestedDir, "another.jpg")

        useCase.cleanup(root, maxAgeMs = 0L)

        assertTrue(regular.exists())
        assertTrue(nestedDir.exists())
        assertTrue(File(nestedDir, "another.jpg").exists())
    }

    @Test
    fun `empty canonical container is removed after last snapshot is purged`() = runTest {
        val root = temp.newFolder("root")
        val container = File(TrashFolderContract.buildContainerPath(root.absolutePath))
        val oldTs = System.currentTimeMillis() - 10 * 60 * 1000L
        val snapshotA = File(TrashFolderContract.buildSnapshotPath(root.absolutePath, oldTs))
        val snapshotB = File(TrashFolderContract.buildSnapshotPath(root.absolutePath, oldTs - 1))
        snapshotA.mkdirsOrFail()
        snapshotB.mkdirsOrFail()
        writeFile(snapshotA, "a.txt")
        writeFile(snapshotB, "b.txt")

        val deleted = useCase.cleanup(root)

        assertEquals(2, deleted)
        assertFalse(container.exists())
    }

    @Test
    fun `recursive descent finds trash inside subdirectories`() = runTest {
        val root = temp.newFolder("root")
        val sub = File(root, "albums/2024")
        sub.mkdirsOrFail()
        val oldTs = System.currentTimeMillis() - 10 * 60 * 1000L
        val snapshot = File(TrashFolderContract.buildSnapshotPath(sub.absolutePath, oldTs))
        snapshot.mkdirsOrFail()
        writeFile(snapshot, "nested.txt")

        val deleted = useCase.cleanup(root)

        assertEquals(1, deleted)
        assertFalse(snapshot.exists())
    }
}
