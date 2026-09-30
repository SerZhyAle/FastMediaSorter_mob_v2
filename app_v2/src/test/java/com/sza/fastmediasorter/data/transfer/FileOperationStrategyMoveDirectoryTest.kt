package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.transfer.strategy.PartialDirectoryTransferException
import com.sza.fastmediasorter.data.transfer.strategy.directoryCopyVerdict
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S3939: the default [FileOperationStrategy.moveDirectory] deletes the source on a successful
 * copy, so a copy that lost a file must reach it as a failure and leave the source in place.
 */
class FileOperationStrategyMoveDirectoryTest {

    @Test
    fun `move keeps the source when one file of the copy failed`() = runTest {
        val strategy = FakeTreeStrategy(files = listOf("a.jpg", "b.jpg", "c.jpg"), failing = setOf("b.jpg"))

        val result = strategy.moveDirectory("fake://src", "fake://dst")

        assertTrue(result.exceptionOrNull() is PartialDirectoryTransferException)
        assertFalse("source must survive a partial copy", strategy.deletedDirectories.contains("fake://src"))
    }

    @Test
    fun `move deletes the source when every file was copied`() = runTest {
        val strategy = FakeTreeStrategy(files = listOf("a.jpg", "b.jpg"), failing = emptySet())

        val result = strategy.moveDirectory("fake://src", "fake://dst")

        assertEquals(2, result.getOrNull())
        assertEquals(listOf("fake://src"), strategy.deletedDirectories)
    }

    /** Copies file by file the way the remote strategies do, then reports through the shared verdict. */
    private class FakeTreeStrategy(
        private val files: List<String>,
        private val failing: Set<String>,
    ) : FileOperationStrategy {

        val deletedDirectories = mutableListOf<String>()

        override suspend fun copyDirectory(
            source: String,
            destination: String,
            progressCallback: ((Int, Int, String) -> Unit)?,
        ): Result<Int> {
            var copied = 0
            var firstFailure: Throwable? = null
            for (name in files) {
                copyFile("$source/$name", "$destination/$name", overwrite = true)
                    .onSuccess { copied++ }
                    .onFailure { if (firstFailure == null) firstFailure = it }
            }
            return directoryCopyVerdict(copied, files.size, firstFailure)
        }

        override suspend fun deleteDirectory(
            path: String,
            progressCallback: ((Int, Int, String) -> Unit)?,
        ): Result<Int> {
            deletedDirectories += path
            return Result.success(files.size)
        }

        override suspend fun copyFile(
            source: String,
            destination: String,
            overwrite: Boolean,
            progressCallback: ByteProgressCallback?,
        ): Result<String> =
            if (failing.contains(source.substringAfterLast('/'))) {
                Result.failure(IOException("copy refused: $source"))
            } else {
                Result.success(destination)
            }

        override suspend fun moveFile(source: String, destination: String): Result<Unit> = unused()
        override suspend fun createTextFile(
            parentPath: String,
            fileName: String,
            content: String,
            resourceId: Long,
        ): Result<String> = unused()
        override suspend fun deleteFile(path: String): Result<Unit> = unused()
        override suspend fun exists(path: String): Result<Boolean> = unused()
        override suspend fun createDirectory(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun writeFile(path: String, content: String): Result<Unit> = unused()
        override suspend fun readFile(path: String): Result<String> = unused()
        override suspend fun listFiles(path: String): Result<List<String>> = unused()
        override fun supportsProtocol(path: String): Boolean = path.startsWith("fake://")
        override fun getProtocolName(): String = "fake"

        private fun <T> unused(): Result<T> = Result.failure(UnsupportedOperationException("not used by this test"))
    }
}
