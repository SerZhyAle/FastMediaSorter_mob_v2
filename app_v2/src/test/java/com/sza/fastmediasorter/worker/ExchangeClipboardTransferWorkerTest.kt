package com.sza.fastmediasorter.worker

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFiles
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardPlacer
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.domain.usecase.exchange.GetClipboardFromServerUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.SendClipboardToServerUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the staging copy of a send whose source cannot state its size: a copy that fails part-way must
 * leave no orphan in cache/outgoing, and a cancelled send must stop before EOF and remove its partial
 * output - a replaced send is already free to run while the old one is still staging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeClipboardTransferWorkerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a staging copy that fails part-way leaves no partial outgoing file`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://exchange/test/clip")
        val staged = File(temp.newFolder("outgoing"), "outgoing-staged.part")
        shadowOf(context.contentResolver).registerInputStream(uri, FailingAfterChunks(CHUNKS_BEFORE_GATE))
        val worker = transferWorker(context, uri, staged)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            ExchangeClipboardTransferWorker.OUTCOME_NOT_SENT,
            (result as ListenableWorker.Result.Success)
                .outputData.getString(ExchangeClipboardTransferWorker.KEY_OUTCOME),
        )
        assertFalse("a failed staging must not leave its partial copy behind", staged.exists())
    }

    @Test
    fun `a cancelled send stops staging before EOF and removes its partial output`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://exchange/test/clip")
        val staged = File(temp.newFolder("outgoing"), "outgoing-staged.part")
        val reached = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val source = GatedStream(CHUNKS_BEFORE_GATE, reached, gate)
        shadowOf(context.contentResolver).registerInputStream(uri, source)
        val worker = transferWorker(context, uri, staged)
        var completion: Throwable? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                worker.doWork()
            } catch (failure: Throwable) {
                completion = failure
            }
        }

        assertTrue("staging never reached the gate", reached.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        job.cancel()
        gate.countDown()
        job.join()

        assertTrue("cancellation must propagate out of doWork", completion is CancellationException)
        assertEquals("the cancelled staging must stop before EOF", CHUNKS_BEFORE_GATE, source.reads)
        assertFalse("the cancelled staging must remove its partial output", staged.exists())
    }

    private fun transferWorker(context: Context, uri: Uri, staged: File): ExchangeClipboardTransferWorker {
        val files = mockk<ExchangeClipboardFiles> {
            every { newOutgoingFile(any()) } returns staged
        }
        val params = mockk<WorkerParameters> {
            every { id } returns UUID.randomUUID()
            every { inputData } returns sendInputData(uri)
            every { foregroundUpdater } returns mockk<ForegroundUpdater> {
                every { setForegroundAsync(any(), any(), any()) } returns Futures.immediateFuture(null)
            }
            every { progressUpdater } returns mockk<ProgressUpdater> {
                every { updateProgress(any(), any(), any()) } returns Futures.immediateFuture(null)
            }
        }
        return ExchangeClipboardTransferWorker(
            context = context,
            params = params,
            sendClipboard = mockk<SendClipboardToServerUseCase>(relaxed = true),
            getClipboard = mockk<GetClipboardFromServerUseCase>(relaxed = true),
            files = files,
            placer = mockk<ExchangeClipboardPlacer>(relaxed = true),
            quantityFormatter = mockk<QuantityFormatter>(relaxed = true),
            unitSystem = mockk<UnitSystemProvider>(relaxed = true),
            ioDispatcher = Dispatchers.IO,
        )
    }

    private fun sendInputData(uri: Uri): Data = workDataOf(
        ExchangeClipboardTransferWorker.KEY_DIRECTION to ExchangeClipboardTransferWorker.DIRECTION_SEND,
        ExchangeClipboardTransferWorker.KEY_KIND to ExchangeClipboardKind.FILE.wire,
        ExchangeClipboardTransferWorker.KEY_URI to uri.toString(),
        ExchangeClipboardTransferWorker.KEY_MIME to "application/octet-stream",
    )

    private companion object {
        const val CHUNKS_BEFORE_GATE = 3
        const val LATCH_TIMEOUT_SECONDS = 5L
    }
}

/** Serves single-byte chunks, then breaks: the copy must not survive a failed staging. */
private class FailingAfterChunks(private val chunksBeforeFailure: Int) : InputStream() {

    private var chunksServed = 0

    override fun read(): Int {
        chunksServed += 1
        if (chunksServed > chunksBeforeFailure) throw IOException("staging broke mid-stream")
        return 'x'.code
    }
}

/**
 * Serves chunks, then blocks on [gate]: the test cancels the send while the read is stuck, so the
 * worker meets a cancelled job at the next chunk boundary and must drop the partial copy.
 */
private class GatedStream(
    private val chunksBeforeGate: Int,
    private val reached: CountDownLatch,
    private val gate: CountDownLatch,
) : InputStream() {

    var reads = 0
        private set

    override fun read(): Int = throw IOException("the staging loop reads in chunks only")

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (reads >= chunksBeforeGate) {
            reached.countDown()
            try {
                gate.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return -1
        }
        reads += 1
        buffer[off] = 'x'.code.toByte()
        return 1
    }
}
