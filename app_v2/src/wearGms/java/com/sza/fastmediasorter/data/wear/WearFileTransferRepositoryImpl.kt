package com.sza.fastmediasorter.data.wear

import android.content.Context
import android.webkit.MimeTypeMap
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WearFileTransferItem
import com.sza.fastmediasorter.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.model.WearFileTransferState
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1861: GMS-backed owner of the phone -> watch transfer queue (`wearGms` source set).
 *
 * Runs on the injected application scope, so a send outlives the screen that started it. Sends are
 * serialised by [sendMutex] because the Data Layer opens one channel per path and two concurrent
 * opens on the same path race for the same watch-side handler.
 *
 * The size ceiling and the pre-send refusal live in phase 2 of the tactical plan, together with the
 * receiving half that has to enforce them; what is settled here is who owns the transfer.
 */
@Singleton
class WearFileTransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val gson: Gson
) : WearFileTransferRepository {

    private val transferState = MutableStateFlow(WearFileTransferState())
    override val transfers: StateFlow<WearFileTransferState> = transferState.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val sendMutex = Mutex()

    override fun enqueue(sourcePath: String, displayName: String): String {
        val id = UUID.randomUUID().toString()
        val item = WearFileTransferItem(id = id, sourcePath = sourcePath, displayName = displayName)
        transferState.update { state -> state.copy(items = state.items + item) }

        val job = applicationScope.launch(ioDispatcher) {
            // File.length() is a filesystem stat, and enqueue is called from the UI thread, so the
            // size is measured here rather than while building the queue entry.
            val totalBytes = File(sourcePath).length()
            updateItem(id) { queued -> queued.copy(totalBytes = totalBytes) }
            // The ceiling is judged before the bridge is touched: the user is told a file will not
            // fit instead of watching it fail somewhere between two devices (strategic spec 3.1.3).
            if (totalBytes > WEAR_FILE_TRANSFER_MAX_BYTES) {
                Timber.i("Refusing %s for the watch: %d bytes over the ceiling", displayName, totalBytes)
                finish(id, WearFileTransferOutcome.TOO_LARGE)
            } else {
                sendMutex.withLock { runTransfer(item.copy(totalBytes = totalBytes)) }
            }
        }
        // Registered before the completion hook: a transfer that ends inside launch - an unreachable
        // watch resolves in microseconds - would otherwise have its hook run first and leave the
        // finished job in the map for the life of the process.
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id) }
        return id
    }

    override fun cancel(transferId: String) {
        // Marked before the job is cancelled: cancellation unwinds runTransfer without reaching its
        // outcome write, so the queue would otherwise keep showing the entry as still running.
        updateItem(transferId) { item ->
            if (item.outcome.isTerminal) item else item.copy(outcome = WearFileTransferOutcome.CANCELLED)
        }
        jobs.remove(transferId)?.cancel()
    }

    override fun clearFinished() {
        transferState.update { state -> state.copy(items = state.items.filterNot { it.outcome.isTerminal }) }
    }

    private suspend fun runTransfer(item: WearFileTransferItem) {
        val nodeId = firstConnectedNodeId()
        val outcome = if (nodeId == null) {
            WearFileTransferOutcome.WATCH_UNREACHABLE
        } else {
            copyToWatch(item, nodeId)
        }
        finish(item.id, outcome)
    }

    // A channel open and a byte copy fail through GMS ApiException, IOException and RemoteException
    // alike, and every one of them ends this transfer the same way; cancellation is rethrown first.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun copyToWatch(item: WearFileTransferItem, nodeId: String): WearFileTransferOutcome {
        val channelClient = Wearable.getChannelClient(context)
        // The watch names the received file from the trailing path segment; the announcement that
        // precedes it carries the size, which the trailing segment has no room for.
        val path = "${WearDataLayerPaths.FILE_TRANSFER}/${item.displayName}"
        val channel = try {
            announce(nodeId, item)
            withTimeout(CHANNEL_TIMEOUT_MS) { channelClient.openChannel(nodeId, path).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to open the watch file transfer channel")
            return WearFileTransferOutcome.FAILED
        }

        return try {
            updateItem(item.id) { it.copy(outcome = WearFileTransferOutcome.RUNNING) }
            channelClient.getOutputStream(channel).await().use { output ->
                File(item.sourcePath).inputStream().use { input -> pump(item.id, input, output) }
            }
            WearFileTransferOutcome.SUCCEEDED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A watch that walks away raises IOException on the copy, not cancellation: the
            // application scope is a never-cancelled SupervisorJob.
            Timber.w(e, "Watch file transfer interrupted")
            WearFileTransferOutcome.FAILED
        } finally {
            withContext(NonCancellable) {
                runCatching { channelClient.close(channel).await() }
                    .onFailure { Timber.w(it, "Failed to close the watch file transfer channel") }
            }
        }
    }

    private suspend fun pump(id: String, input: InputStream, output: OutputStream) {
        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
        var sent = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            output.write(buffer, 0, read)
            sent += read
            updateItem(id) { it.copy(transferredBytes = sent) }
            read = input.read(buffer)
        }
        output.flush()
    }

    /**
     * Tells the watch what is coming before the channel opens, so an oversized or unwanted file is
     * refused there without a single byte crossing the bridge.
     */
    private suspend fun announce(nodeId: String, item: WearFileTransferItem) {
        val metadata = WearFileTransferMetadata(
            name = item.displayName,
            size = item.totalBytes,
            mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(File(item.sourcePath).extension.lowercase())
        )
        withTimeout(MESSAGE_TIMEOUT_MS) {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.FILE_TRANSFER_META,
                    gson.toJson(metadata).toByteArray(Charsets.UTF_8)
                )
                .await()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun firstConnectedNodeId(): String? = try {
        withTimeout(MESSAGE_TIMEOUT_MS) {
            Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.id
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Expected on an unpaired or powered-off watch, which the queue reports as WATCH_UNREACHABLE.
        Timber.i(e, "No paired watch reachable for the file transfer")
        null
    }

    private fun finish(id: String, outcome: WearFileTransferOutcome) {
        updateItem(id) { item -> if (item.outcome.isTerminal) item else item.copy(outcome = outcome) }
    }

    private fun updateItem(id: String, transform: (WearFileTransferItem) -> WearFileTransferItem) {
        transferState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
        }
    }

    private companion object {
        /** One progress emission per chunk, so the buffer size also sets how often the UI redraws. */
        const val TRANSFER_BUFFER_BYTES = 64 * 1024

        /** The bridge answers a message within this window or the watch is treated as gone. */
        const val MESSAGE_TIMEOUT_MS = 10_000L

        /** A channel open is given longer: GMS may have to wake the watch app first. */
        const val CHANNEL_TIMEOUT_MS = 30_000L
    }
}
