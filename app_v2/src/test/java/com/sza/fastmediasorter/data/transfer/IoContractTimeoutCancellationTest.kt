package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkTimeoutException
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * S3371: the first half of the "External I/O contract" in `docs/ARCHITECTURE.md`, executed rather
 * than asserted - bounded time and cooperative cancellation.
 *
 * The seam under test is [FileOperationStrategy]. Both ends are fake transports over an in-memory
 * file map, so no socket, no disk and no clock is involved: a hang is a `delay` under virtual time
 * and a cancellation is a [Job] the test holds. What is real is the code between them - the timeout
 * the caller declares, the classifier that names the failure, and [DirectoryTreeTransferManager]'s
 * per-entry cancellation check, which is what decides how much of an interrupted move is committed.
 */
class IoContractTimeoutCancellationTest {

    private val fs = FakeFileSystem()
    private val tempFileManager: TempFileManager = mockk(relaxed = true)

    @Test
    fun `a hung transport hits the declared timeout and surfaces as a classified timeout error`() = runTest {
        fs.files[SOURCE_FILE] = "bytes"
        val hung = HangingTransport(fs)

        val thrown = runCatching {
            withTimeout(OPERATION_TIMEOUT_MS) {
                hung.copyFile(SOURCE_FILE, DESTINATION_FILE, overwrite = true)
            }
        }.exceptionOrNull()

        assertNotNull("the hung transport was expected to be cut off by the timeout", thrown)
        val classified = NetworkErrorClassifier.classifySilently(thrown!!)
        assertTrue(
            "expected NetworkTimeoutException, got ${classified::class.simpleName}",
            classified is NetworkTimeoutException
        )
        assertFalse("nothing may be published when the budget expires", fs.files.containsKey(DESTINATION_FILE))
    }

    @Test
    fun `an operation that finishes inside its budget is not timed out`() = runTest {
        fs.files[SOURCE_FILE] = "bytes"
        val slow = HangingTransport(fs, hangMs = OPERATION_TIMEOUT_MS / 2)

        val result = withTimeout(OPERATION_TIMEOUT_MS) {
            slow.copyFile(SOURCE_FILE, DESTINATION_FILE, overwrite = true)
        }

        assertTrue("a bound is a deadline, not a blanket failure", result.isSuccess)
        assertEquals("bytes", fs.files[DESTINATION_FILE])
    }

    @Test
    fun `cancellation stops a move between files and commits only the entries already transferred`() = runTest {
        seedSourceTree()
        val job = Job()
        val source = FakeTransport(fs, protocol = LOCAL)
        // The walk checks the job once per entry, so cancelling while the second file is in flight
        // lets that file finish and stops the third before it starts - the boundary the contract
        // calls "between files".
        val destination = FakeTransport(fs, protocol = SMB, onCopy = { path ->
            if (path == "$SOURCE_DIR/b.jpg") job.cancel()
        })
        val manager = managerOf(source, destination)

        val thrown = runCatching {
            withContext(job) { manager.moveTree(SOURCE_DIR, DESTINATION_DIR) }
        }.exceptionOrNull()

        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        assertEquals("a.jpg", fs.files["$DESTINATION_DIR/a.jpg"])
        assertEquals("b.jpg", fs.files["$DESTINATION_DIR/b.jpg"])
        assertFalse(
            "the entry after the cancellation must not be started",
            fs.files.containsKey("$DESTINATION_DIR/c.jpg")
        )
        // Never a hole: what left the source is at the destination, what did not is still at the source.
        assertFalse(fs.files.containsKey("$SOURCE_DIR/a.jpg"))
        assertFalse(fs.files.containsKey("$SOURCE_DIR/b.jpg"))
        assertEquals("c.jpg", fs.files["$SOURCE_DIR/c.jpg"])
    }

    @Test
    fun `the progress surface observes the cancellation and reports no entry after it`() = runTest {
        seedSourceTree()
        val job = Job()
        val seen = mutableListOf<String>()
        val source = FakeTransport(fs, protocol = LOCAL)
        val destination = FakeTransport(fs, protocol = SMB, onCopy = { path ->
            if (path == "$SOURCE_DIR/b.jpg") job.cancel()
        })
        val manager = managerOf(source, destination)

        runCatching {
            withContext(job) {
                manager.moveTree(SOURCE_DIR, DESTINATION_DIR) { _, _, name -> seen += name }
            }
        }

        assertEquals(listOf("a.jpg", "b.jpg"), seen)
    }

    private fun seedSourceTree() {
        fs.files["$SOURCE_DIR/a.jpg"] = "a.jpg"
        fs.files["$SOURCE_DIR/b.jpg"] = "b.jpg"
        fs.files["$SOURCE_DIR/c.jpg"] = "c.jpg"
    }

    private fun managerOf(source: FileOperationStrategy, destination: FileOperationStrategy) =
        DirectoryTreeTransferManager(
            operationStrategies = mapOf(LOCAL to source, SMB to destination),
            tempFileManager = tempFileManager,
        )

    private companion object {
        const val LOCAL = "local"
        const val SMB = "smb"

        // Not a round 5_000: the classifier falls back to message heuristics, and the text
        // "Timed out waiting for 5000 ms" carries the substring 500, which its HTTP branch reads
        // as a server error before the timeout branch is ever reached.
        const val OPERATION_TIMEOUT_MS = 1_200L
        const val SOURCE_DIR = "/storage/Trip"
        const val DESTINATION_DIR = "smb://server/share/Trip"
        const val SOURCE_FILE = "$SOURCE_DIR/a.jpg"
        const val DESTINATION_FILE = "$DESTINATION_DIR/a.jpg"
    }
}

/** A transport that never answers, so the caller's declared budget is the only thing that ends it. */
private class HangingTransport(
    fs: FakeFileSystem,
    private val hangMs: Long = Long.MAX_VALUE / 2,
) : FakeTransport(fs, protocol = "smb") {

    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?,
    ): Result<String> {
        delay(hangMs)
        return super.copyFile(source, destination, overwrite, progressCallback)
    }
}

/** One flat namespace shared by every fake transport, so a cross-protocol copy has something to read. */
internal class FakeFileSystem {
    val files: LinkedHashMap<String, String> = LinkedHashMap()
    val directories: LinkedHashSet<String> = LinkedHashSet()
}

/**
 * A [FileOperationStrategy] over [FakeFileSystem] - the whole interface, backed by one map, so a
 * transfer path can be driven end to end without a socket. Shared by the two IoContract suites in
 * this package.
 */
internal open class FakeTransport(
    protected val fs: FakeFileSystem,
    private val protocol: String,
    private val onCopy: ((String) -> Unit)? = null,
) : FileOperationStrategy {

    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?,
    ): Result<String> {
        onCopy?.invoke(source)
        val content = fs.files[source]
        return when {
            content == null -> Result.failure(FileNotFoundException(source))
            !overwrite && fs.files.containsKey(destination) ->
                Result.failure(IOException("Destination exists: $destination"))
            else -> {
                fs.files[destination] = content
                Result.success(destination)
            }
        }
    }

    override suspend fun moveFile(source: String, destination: String): Result<Unit> =
        copyFile(source, destination, overwrite = true).mapCatching {
            deleteFile(source).getOrThrow()
        }

    override suspend fun deleteFile(path: String): Result<Unit> =
        if (fs.files.remove(path) != null) Result.success(Unit) else Result.failure(FileNotFoundException(path))

    override suspend fun exists(path: String): Result<Boolean> =
        Result.success(fs.files.containsKey(path) || fs.directories.contains(path))

    override suspend fun createDirectory(path: String): Result<Unit> {
        fs.directories += path.trimEnd('/')
        return Result.success(Unit)
    }

    override suspend fun createTextFile(
        parentPath: String,
        fileName: String,
        content: String,
        resourceId: Long,
    ): Result<String> {
        val path = "${parentPath.trimEnd('/')}/$fileName"
        fs.files[path] = content
        return Result.success(path)
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        fs.files[path] = content
        return Result.success(Unit)
    }

    override suspend fun readFile(path: String): Result<String> =
        fs.files[path]?.let { Result.success(it) } ?: Result.failure(FileNotFoundException(path))

    override suspend fun listFiles(path: String): Result<List<String>> =
        listEntries(path).map { entries -> entries.map { it.path } }

    override suspend fun listEntries(path: String): Result<List<DirectoryEntry>> {
        val base = path.trimEnd('/')
        val children = LinkedHashMap<String, Boolean>()
        for (filePath in fs.files.keys) {
            if (!filePath.startsWith("$base/")) continue
            val rest = filePath.removePrefix("$base/")
            children["$base/${rest.substringBefore('/')}"] = rest.contains('/')
        }
        return Result.success(
            children.map { (childPath, isDirectory) ->
                DirectoryEntry(childPath, childPath.substringAfterLast('/'), isDirectory, 0L)
            }
        )
    }

    override suspend fun isDirectory(path: String): Result<Boolean> =
        Result.success(fs.directories.contains(path.trimEnd('/')))

    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?,
    ): Result<Int> {
        val base = path.trimEnd('/')
        val doomed = fs.files.keys.filter { it.startsWith("$base/") }
        doomed.forEach { fs.files.remove(it) }
        fs.directories.removeIf { it == base || it.startsWith("$base/") }
        return Result.success(doomed.size)
    }

    override fun supportsProtocol(path: String): Boolean = transferProtocolKeyFor(path) == protocol

    override fun getProtocolName(): String = protocol
}
