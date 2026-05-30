package com.sza.fastmediasorter.data.transfer.strategy

import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.transfer.FileExistsException
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [LocalOperationStrategy] file/dir operations on the plain filesystem via
 * TemporaryFolder: copy (incl. overwrite + source-missing + dest-exists), move-via-rename,
 * delete (non-shared path), createDirectory, deferred createTextFile, read/write, listFiles,
 * directory copy/rename/delete/info, supportsProtocol, and isSharedStoragePath classification.
 *
 * Shared-storage / MediaStore branches require android.os.Build + ContentResolver and are out of
 * JVM scope; tests deliberately use temp paths that route through the File.* branch.
 */
class LocalOperationStrategyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val stagingRegistry = mockk<LocalStagingRegistry>(relaxed = true)
    private lateinit var strategy: LocalOperationStrategy

    @Before
    fun setUp() {
        strategy = LocalOperationStrategy(context = mockk(relaxed = true), stagingRegistry = stagingRegistry)
    }

    @Test
    fun `copyFile copies content`() = runBlocking {
        val src = tempFolder.newFile("src.txt").apply { writeText("payload") }
        val dest = File(tempFolder.root, "out/dest.txt").absolutePath

        val result = strategy.copyFile(src.absolutePath, dest, overwrite = false, progressCallback = null)

        assertTrue(result.isSuccess)
        assertEquals("payload", File(dest).readText())
    }

    @Test
    fun `copyFile fails when source missing`() = runBlocking {
        val result = strategy.copyFile("/no/such/file", tempFolder.newFile("d.txt").absolutePath, false, null)
        assertTrue(result.isFailure)
    }

    @Test
    fun `copyFile fails with FileExistsException when dest exists and not overwriting`() = runBlocking {
        val src = tempFolder.newFile("s.txt").apply { writeText("a") }
        val dest = tempFolder.newFile("d.txt").apply { writeText("b") }

        val result = strategy.copyFile(src.absolutePath, dest.absolutePath, overwrite = false, null)

        assertTrue(result.exceptionOrNull() is FileExistsException)
    }

    @Test
    fun `copyFile overwrites when overwrite true`() = runBlocking {
        val src = tempFolder.newFile("s.txt").apply { writeText("new") }
        val dest = tempFolder.newFile("d.txt").apply { writeText("old") }

        val result = strategy.copyFile(src.absolutePath, dest.absolutePath, overwrite = true, null)

        assertTrue(result.isSuccess)
        assertEquals("new", dest.readText())
    }

    @Test
    fun `moveFile renames within same filesystem`() = runBlocking {
        val src = tempFolder.newFile("m.txt").apply { writeText("x") }
        val dest = File(tempFolder.root, "moved.txt")

        val result = strategy.moveFile(src.absolutePath, dest.absolutePath)

        assertTrue(result.isSuccess)
        assertFalse(src.exists())
        assertTrue(dest.exists())
    }

    @Test
    fun `moveFile fails when source missing`() = runBlocking {
        val result = strategy.moveFile("/no/src", File(tempFolder.root, "d").absolutePath)
        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteFile removes non-shared file`() = runBlocking {
        val file = tempFolder.newFile("del.txt")
        val result = strategy.deleteFile(file.absolutePath)
        assertTrue(result.isSuccess)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteFile is success when already gone`() = runBlocking {
        val result = strategy.deleteFile(File(tempFolder.root, "ghost.txt").absolutePath)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `exists reflects filesystem`() = runBlocking {
        val file = tempFolder.newFile("e.txt")
        assertTrue(strategy.exists(file.absolutePath).getOrNull() == true)
        assertTrue(strategy.exists(File(tempFolder.root, "nope").absolutePath).getOrNull() == false)
    }

    @Test
    fun `createDirectory makes nested dirs and is idempotent`() = runBlocking {
        val dir = File(tempFolder.root, "a/b/c").absolutePath
        assertTrue(strategy.createDirectory(dir).isSuccess)
        assertTrue(File(dir).isDirectory)
        // second call on existing dir still succeeds
        assertTrue(strategy.createDirectory(dir).isSuccess)
    }

    @Test
    fun `createTextFile registers staging and does not touch filesystem`() = runBlocking {
        val parent = tempFolder.newFolder("notes")
        val result = strategy.createTextFile(parent.absolutePath, "note.txt", "body", resourceId = 5L)

        assertTrue(result.isSuccess)
        assertEquals(File(parent, "note.txt").absolutePath, result.getOrNull())
        assertFalse(File(parent, "note.txt").exists()) // deferred - not written
        verify { stagingRegistry.register(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createTextFile fails on non-directory parent`() = runBlocking {
        val result = strategy.createTextFile("/no/dir", "n.txt", "c", 1L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `createTextFile fails when target exists`() = runBlocking {
        val parent = tempFolder.newFolder("p")
        File(parent, "exists.txt").writeText("x")
        val result = strategy.createTextFile(parent.absolutePath, "exists.txt", "c", 1L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `writeFile then readFile round-trips`() = runBlocking {
        val path = File(tempFolder.root, "rw.txt").absolutePath
        assertTrue(strategy.writeFile(path, "hello").isSuccess)
        assertEquals("hello", strategy.readFile(path).getOrNull())
    }

    @Test
    fun `readFile fails for missing file`() = runBlocking {
        assertTrue(strategy.readFile(File(tempFolder.root, "x").absolutePath).isFailure)
    }

    @Test
    fun `listFiles returns child paths`() = runBlocking {
        val dir = tempFolder.newFolder("list")
        File(dir, "1.txt").writeText("a")
        File(dir, "2.txt").writeText("b")

        val result = strategy.listFiles(dir.absolutePath)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `listFiles fails for non-directory`() = runBlocking {
        assertTrue(strategy.listFiles(File(tempFolder.root, "missing").absolutePath).isFailure)
    }

    @Test
    fun `supportsProtocol accepts local rejects network`() {
        assertTrue(strategy.supportsProtocol("/storage/file.jpg"))
        assertTrue(strategy.supportsProtocol("file:///storage/x"))
        assertFalse(strategy.supportsProtocol("smb://s/share"))
        assertFalse(strategy.supportsProtocol("sftp://h/p"))
        assertFalse(strategy.supportsProtocol("ftp://h/p"))
        assertEquals("local", strategy.getProtocolName())
    }

    @Test
    fun `deleteDirectory removes tree and counts items`() = runBlocking {
        val dir = tempFolder.newFolder("tree")
        File(dir, "a.txt").writeText("a")
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "b.txt").writeText("b")

        val result = strategy.deleteDirectory(dir.absolutePath, null)
        assertTrue(result.isSuccess)
        assertFalse(dir.exists())
        // 2 files + 1 subdir + the dir itself
        assertEquals(4, result.getOrThrow())
    }

    @Test
    fun `renameDirectory moves dir`() = runBlocking {
        val dir = tempFolder.newFolder("old")
        val newPath = File(tempFolder.root, "renamed").absolutePath
        val result = strategy.renameDirectory(dir.absolutePath, newPath)
        assertTrue(result.isSuccess)
        assertTrue(File(newPath).isDirectory)
    }

    @Test
    fun `renameDirectory fails when destination exists`() = runBlocking {
        val dir = tempFolder.newFolder("a")
        val other = tempFolder.newFolder("b")
        assertTrue(strategy.renameDirectory(dir.absolutePath, other.absolutePath).isFailure)
    }

    @Test
    fun `copyDirectory copies all files`() = runBlocking {
        val src = tempFolder.newFolder("csrc")
        File(src, "x.txt").writeText("x")
        val nested = File(src, "n").apply { mkdirs() }
        File(nested, "y.txt").writeText("y")
        val dest = File(tempFolder.root, "cdest").absolutePath

        val result = strategy.copyDirectory(src.absolutePath, dest, null)
        assertEquals(2, result.getOrThrow())
        assertTrue(File("$dest/x.txt").exists())
        assertTrue(File("$dest/n/y.txt").exists())
    }

    @Test
    fun `isDirectory distinguishes file from dir`() = runBlocking {
        val dir = tempFolder.newFolder("d")
        val file = tempFolder.newFile("f.txt")
        assertTrue(strategy.isDirectory(dir.absolutePath).getOrNull() == true)
        assertTrue(strategy.isDirectory(file.absolutePath).getOrNull() == false)
    }

    @Test
    fun `getDirectoryInfo aggregates size and child count`() = runBlocking {
        val dir = tempFolder.newFolder("info")
        File(dir, "a.txt").writeText("12345")
        File(dir, "b.txt").writeText("123")

        val info = strategy.getDirectoryInfo(dir.absolutePath).getOrThrow()
        assertEquals("info", info.name)
        assertEquals(2, info.childCount)
        assertEquals(8L, info.totalSize)
    }

    @Test
    fun `isSharedStoragePath classifies emulated paths`() {
        assertTrue(strategy.isSharedStoragePath("/storage/emulated/0/Pictures/x.jpg"))
        assertFalse(strategy.isSharedStoragePath("/storage/emulated/0/Android/data/pkg/x"))
        assertFalse(strategy.isSharedStoragePath("/data/local/tmp/x"))
    }
}
