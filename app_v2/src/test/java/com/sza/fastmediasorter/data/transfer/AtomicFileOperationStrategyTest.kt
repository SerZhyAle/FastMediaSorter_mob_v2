package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicFileOperationStrategyTest {

    private val progressCallback: ByteProgressCallback = mockk(relaxed = true)

    private lateinit var workingDir: File
    private lateinit var sourceFile: File
    private lateinit var destinationFile: File

    @Before
    fun setUp() {
        workingDir = Files.createTempDirectory("atomic-copy-test").toFile()
        sourceFile = File(workingDir, "source.bin").apply {
            writeText("atomic-copy-payload")
        }
        destinationFile = File(workingDir, "destination.bin")
    }

    @After
    fun tearDown() {
        workingDir.deleteRecursively()
    }

    @Test
    fun `test scaffold uses runTest and mockk`() = runTest {
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_CREATE_TEMP))

        val result = strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = destinationFile.absolutePath,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `success rename leaves destination exists and temp file gone`() = runTest {
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_CREATE_TEMP))
        val tempDestination = TempFileNamingStrategy.getTempPath(destinationFile.absolutePath)

        val result = strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = destinationFile.absolutePath,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(result.isSuccess)
        assertEquals(destinationFile.absolutePath, result.getOrNull())
        assertTrue("destination exists after success rename", destinationFile.exists())
        assertFalse(File(tempDestination).exists())
        assertEquals(sourceFile.readText(), destinationFile.readText())
    }

    @Test
    fun `CancellationException after partial write cleans temp and preserves cancellation cause`() = runTest {
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.CANCEL_AFTER_PARTIAL_WRITE))
        val tempDestination = TempFileNamingStrategy.getTempPath(destinationFile.absolutePath)

        val result = strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = destinationFile.absolutePath,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(File(tempDestination).exists())
        assertFalse(destinationFile.exists())
    }

    @Test
    fun `missing temp postcondition returns failure after delegate reported success`() = runTest {
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_WITHOUT_TEMP))
        val tempDestination = TempFileNamingStrategy.getTempPath(destinationFile.absolutePath)

        val result = strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = destinationFile.absolutePath,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Temp file not found after copy") == true)
        assertFalse(File(tempDestination).exists())
        assertFalse(destinationFile.exists())
    }

    private enum class FakeMode {
        SUCCESS_CREATE_TEMP,
        CANCEL_AFTER_PARTIAL_WRITE,
        SUCCESS_WITHOUT_TEMP
    }

    private class FakeDelegate(
        private val mode: FakeMode
    ) : FileOperationStrategy {

        override suspend fun copyFile(
            source: String,
            destination: String,
            overwrite: Boolean,
            progressCallback: ByteProgressCallback?
        ): Result<String> {
            val sourceFile = File(source)
            val destinationFile = File(destination)
            destinationFile.parentFile?.mkdirs()

            return when (mode) {
                FakeMode.SUCCESS_CREATE_TEMP -> {
                    sourceFile.copyTo(destinationFile, overwrite = true)
                    Result.success(destination)
                }

                FakeMode.CANCEL_AFTER_PARTIAL_WRITE -> {
                    destinationFile.writeText("partial")
                    throw CancellationException("partial write cancelled")
                }

                FakeMode.SUCCESS_WITHOUT_TEMP -> Result.success(destination)
            }
        }

        override suspend fun moveFile(source: String, destination: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("unused in local atomic tests"))

        override suspend fun deleteFile(path: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("unused in local atomic tests"))

        override suspend fun exists(path: String): Result<Boolean> =
            Result.success(File(path).exists())

        override suspend fun createDirectory(path: String): Result<Unit> {
            File(path).mkdirs()
            return Result.success(Unit)
        }

        override suspend fun writeFile(path: String, content: String): Result<Unit> {
            File(path).writeText(content)
            return Result.success(Unit)
        }

        override suspend fun readFile(path: String): Result<String> =
            Result.success(File(path).readText())

        override suspend fun listFiles(path: String): Result<List<String>> =
            Result.success(File(path).listFiles()?.map { it.absolutePath } ?: emptyList())

        override fun supportsProtocol(path: String): Boolean = true

        override fun getProtocolName(): String = "fake-local"
    }
}