package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
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
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_CREATE_TEMP), FakeClassifier(forceNonPublic = true))

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
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_CREATE_TEMP), FakeClassifier(forceNonPublic = true))
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
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.CANCEL_AFTER_PARTIAL_WRITE), FakeClassifier(forceNonPublic = true))
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
        val strategy = AtomicFileOperationStrategy(FakeDelegate(FakeMode.SUCCESS_WITHOUT_TEMP), FakeClassifier(forceNonPublic = true))
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

        // S0231: createTextFile stub keeps the FakeDelegate compilable against the
        // current FileOperationStrategy contract; unused in atomic tests.
        override suspend fun createTextFile(
            parentPath: String,
            fileName: String,
            content: String,
            resourceId: Long
        ): Result<String> = Result.failure(UnsupportedOperationException("unused in atomic tests"))

        override suspend fun readFile(path: String): Result<String> =
            Result.success(File(path).readText())

        override suspend fun listFiles(path: String): Result<List<String>> =
            Result.success(File(path).listFiles()?.map { it.absolutePath } ?: emptyList())

        override fun supportsProtocol(path: String): Boolean = true

        override fun getProtocolName(): String = "fake-local"
    }

    /**
     * S0231: deterministic classifier substitute for tests.
     * When [forceNonPublic] is true, every call returns NonPublic (existing scaffolding
     * relies on the temp_copy + rename path). When false, returns PublicCollection to
     * exercise the public-collection short-circuit branch.
     */
    private class FakeClassifier(
        private val forceNonPublic: Boolean
    ) : LocalDestinationClassifier() {
        var classifyCallCount: Int = 0
        var lastClassifiedPath: String? = null

        override fun classify(absolutePath: String): LocalDestinationCategory {
            classifyCallCount += 1
            lastClassifiedPath = absolutePath
            val displayName = File(absolutePath).name
            return if (forceNonPublic) {
                LocalDestinationCategory.NonPublic(
                    absolutePath = absolutePath,
                    displayName = displayName,
                    mimeType = "application/octet-stream"
                )
            } else {
                LocalDestinationCategory.PublicCollection(
                    collection = LocalDestinationCategory.PublicCollection.Kind.AUDIO,
                    relativePath = "Music/",
                    displayName = displayName,
                    mimeType = "audio/mpeg"
                )
            }
        }
    }

    // ------------------------------------------------------------------ S0231

    @Test
    fun `S0231 public collection destination bypasses temp_copy and delegates raw`() = runTest {
        val classifier = FakeClassifier(forceNonPublic = false)
        val delegate = RecordingDelegate()
        val strategy = AtomicFileOperationStrategy(delegate, classifier)

        val publicDestination = "/storage/emulated/0/Music/song.mp3"
        val result = strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = publicDestination,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(result.isSuccess)
        assertEquals(publicDestination, delegate.lastDestination)
        assertFalse(
            "delegate was called with .temp_copy suffix on public collection",
            delegate.lastDestination?.endsWith(".temp_copy") == true
        )
    }

    @Test
    fun `S0231 non-public destination still uses temp_copy + rename pattern`() = runTest {
        val classifier = FakeClassifier(forceNonPublic = true)
        val delegate = RecordingDelegate()
        val strategy = AtomicFileOperationStrategy(delegate, classifier)

        val nonPublicDestination = "/storage/emulated/0/SomeApp/file.bin"
        strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = nonPublicDestination,
            overwrite = true,
            progressCallback = progressCallback
        )

        assertTrue(
            "delegate was not called with .temp_copy suffix for non-public destination",
            delegate.lastDestination?.endsWith(".temp_copy") == true
        )
    }

    @Test
    fun `S0231 enableAtomic false bypasses classifier entirely`() = runTest {
        val classifier = FakeClassifier(forceNonPublic = false)
        val delegate = RecordingDelegate()
        val strategy = AtomicFileOperationStrategy(delegate, classifier, enableAtomic = false)

        strategy.copyFile(
            source = sourceFile.absolutePath,
            destination = "/storage/emulated/0/Music/song.mp3",
            overwrite = true,
            progressCallback = progressCallback
        )

        assertEquals(0, classifier.classifyCallCount)
    }

    /**
     * S0231: records the destination passed to copyFile for assertion-driven tests.
     * Always succeeds; does not actually copy bytes.
     */
    private class RecordingDelegate : FileOperationStrategy {
        var lastDestination: String? = null

        override suspend fun copyFile(
            source: String,
            destination: String,
            overwrite: Boolean,
            progressCallback: ByteProgressCallback?
        ): Result<String> {
            lastDestination = destination
            return Result.success(destination)
        }

        override suspend fun moveFile(source: String, destination: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun exists(path: String): Result<Boolean> = Result.success(false)
        override suspend fun createDirectory(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun createTextFile(
            parentPath: String,
            fileName: String,
            content: String,
            resourceId: Long
        ): Result<String> = Result.failure(UnsupportedOperationException("unused in atomic tests"))
        override suspend fun writeFile(path: String, content: String): Result<Unit> = Result.success(Unit)
        override suspend fun readFile(path: String): Result<String> = Result.success("")
        override suspend fun listFiles(path: String): Result<List<String>> = Result.success(emptyList())
        override fun supportsProtocol(path: String): Boolean = true
        override fun getProtocolName(): String = "recording-fake"
    }
}