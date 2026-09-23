package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.data.network.exceptions.NetworkAccessDeniedException
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkFileNotFoundException
import com.sza.fastmediasorter.data.network.exceptions.NetworkHostKeyChangedException
import com.sza.fastmediasorter.data.network.exceptions.NetworkRateLimitException
import com.sza.fastmediasorter.data.network.exceptions.NetworkServerErrorException
import com.sza.fastmediasorter.data.network.exceptions.NetworkTimeoutException
import com.sza.fastmediasorter.data.network.exceptions.RetryPolicy
import com.sza.fastmediasorter.data.network.exceptions.withRetry
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * S3371: the second half of the "External I/O contract" in `docs/ARCHITECTURE.md`, executed rather
 * than asserted - error classification, retry safety and idempotency.
 *
 * The transport is fake, so every failure mode is produced on demand; the classifier, the retry
 * policy and the mutation journal are the real ones, because they are what the contract actually
 * promises. The replay loop in [resumeMovesFromJournal] is deliberately written out here: what it
 * proves is that a [Mutation.Move] entry carries enough to finish an interrupted move, and that
 * running it again over a converged state changes nothing.
 */
class IoContractErrorRetryTest {

    private val fs = FakeFileSystem()

    @Test
    fun `every failure mode of the transport lands in the network error taxonomy`() = runTest {
        val cases = listOf(
            SocketTimeoutException("read timed out") to NetworkTimeoutException::class.java,
            UnknownHostException("server.lan") to NetworkTimeoutException::class.java,
            ConnectException("Connection refused") to NetworkTimeoutException::class.java,
            SSLException("handshake failed") to NetworkAccessDeniedException::class.java,
            FileNotFoundException(SOURCE) to NetworkFileNotFoundException::class.java,
            IOException("STATUS_ACCESS_DENIED") to NetworkAccessDeniedException::class.java,
            IOException("STATUS_OBJECT_NAME_NOT_FOUND") to NetworkFileNotFoundException::class.java,
            IOException("HTTP 503: service unavailable") to NetworkServerErrorException::class.java,
            IOException("429 too many requests") to NetworkRateLimitException::class.java,
            IOException("Connection reset by peer") to NetworkConnectionLostException::class.java,
            IOException("Host key verification failed") to NetworkHostKeyChangedException::class.java,
        )

        for ((raw, expected) in cases) {
            val transport = FailingTransport(fs, raw)
            val failure = transport.copyFile(SOURCE, DESTINATION, overwrite = true).exceptionOrNull()
            assertNotNull("the fake transport was expected to fail on ${raw.message}", failure)

            val classified = NetworkErrorClassifier.classifySilently(failure!!)
            assertEquals("classification of ${raw.message}", expected, classified.javaClass)
        }
    }

    @Test
    fun `a transient classification is retried until it succeeds`() = runTest {
        fs.files[SOURCE] = CONTENT
        var attempts = 0
        val transport = FakeTransport(fs, protocol = SMB, onCopy = {
            attempts++
            if (attempts < ATTEMPTS_BEFORE_SUCCESS) throw SocketTimeoutException("read timed out")
        })

        val result = withRetry(RETRY_POLICY, tag = "io-contract") {
            transport.copyFile(SOURCE, DESTINATION, overwrite = true).getOrThrow()
        }

        assertEquals(ATTEMPTS_BEFORE_SUCCESS, attempts)
        assertEquals(DESTINATION, result)
    }

    @Test
    fun `a permanent classification is never repeated`() = runTest {
        var attempts = 0

        val thrown = runCatching {
            withRetry(RETRY_POLICY, tag = "io-contract") {
                attempts++
                FailingTransport(fs, SSLException("handshake failed"))
                    .copyFile(SOURCE, DESTINATION, overwrite = true)
                    .getOrThrow()
            }
        }.exceptionOrNull()

        assertEquals("an access denial cannot succeed by being asked again", 1, attempts)
        assertTrue("expected NetworkAccessDeniedException, got $thrown", thrown is NetworkAccessDeniedException)
    }

    @Test
    fun `a changed host key is a security event and is never retried`() = runTest {
        var attempts = 0

        val thrown = runCatching {
            withRetry(RETRY_POLICY, tag = "io-contract") {
                attempts++
                FailingTransport(fs, IOException("Host key verification failed"))
                    .copyFile(SOURCE, DESTINATION, overwrite = true)
                    .getOrThrow()
            }
        }.exceptionOrNull()

        assertEquals(1, attempts)
        assertTrue("expected NetworkHostKeyChangedException, got $thrown", thrown is NetworkHostKeyChangedException)
    }

    @Test
    fun `replaying an interrupted move through the journal neither duplicates nor loses the file`() = runTest {
        val journal: MutationJournal = InMemoryMutationJournal()
        val transport = FakeTransport(fs, protocol = LOCAL)
        fs.files[SOURCE] = CONTENT

        // The interrupted state: the copy landed and was journalled, then the process died before
        // the source could be removed, so the same bytes exist under both names.
        transport.copyFile(SOURCE, DESTINATION, overwrite = true).getOrThrow()
        journal.record(
            Mutation.Move(
                resourceId = RESOURCE_ID,
                srcResourceId = RESOURCE_ID,
                dstResourceId = RESOURCE_ID,
                oldCanonicalPath = SOURCE,
                newCanonicalPath = DESTINATION,
                opId = OP_ID,
                timestampMs = 1L,
            )
        )
        assertEquals(CONTENT, fs.files[SOURCE])
        assertEquals(CONTENT, fs.files[DESTINATION])

        // Twice on purpose: the second pass is what makes this idempotence rather than recovery.
        resumeMovesFromJournal(journal, transport)
        resumeMovesFromJournal(journal, transport)

        assertEquals(CONTENT, fs.files[DESTINATION])
        assertFalse("the source must be gone once the move converged", fs.files.containsKey(SOURCE))
        assertEquals("exactly one copy survives the replay", 1, fs.files.size)
        assertTrue(journal.pendingFor(RESOURCE_ID, journal.lastAppliedSeq(RESOURCE_ID)).isEmpty())
    }

    /** The resume shape a [Mutation.Move] entry is written for: finish the copy, then drop the source. */
    private suspend fun resumeMovesFromJournal(journal: MutationJournal, transport: FileOperationStrategy) {
        val pending = journal.pendingFor(RESOURCE_ID, journal.lastAppliedSeq(RESOURCE_ID))
        val applied = mutableListOf<String>()
        for (entry in pending) {
            val move = entry.mutation as? Mutation.Move ?: continue
            if (!fs.files.containsKey(move.newCanonicalPath)) {
                transport.copyFile(move.oldCanonicalPath, move.newCanonicalPath, overwrite = true).getOrThrow()
            }
            transport.deleteFile(move.oldCanonicalPath)
            applied += move.opId
        }
        journal.markApplied(RESOURCE_ID, applied)
    }

    private companion object {
        const val LOCAL = "local"
        const val SMB = "smb"
        const val SOURCE = "/storage/Trip/a.jpg"
        const val DESTINATION = "/storage/Sorted/a.jpg"
        const val CONTENT = "bytes"
        const val RESOURCE_ID = 7L
        const val OP_ID = "op-3371"
        const val ATTEMPTS_BEFORE_SUCCESS = 3
        val RETRY_POLICY = RetryPolicy(maxAttempts = 4, initialDelayMs = 10L, maxDelayMs = 40L)
    }
}

/** A transport whose every copy fails the same way, so one failure mode can be judged at a time. */
private class FailingTransport(
    fs: FakeFileSystem,
    private val error: Throwable,
) : FakeTransport(fs, protocol = "smb") {

    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?,
    ): Result<String> = Result.failure(error)
}
