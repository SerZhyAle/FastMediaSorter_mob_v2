package com.sza.fastmediasorter.ui.browse.transfer

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseFileTransferCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val requestStore: BrowseFileTransferRequestStore,
) {
    data class TransferWorkState(
        val workId: String? = null,
        val state: WorkInfo.State? = null,
        val request: BrowseFileTransferRequest? = null,
        val progress: BrowseFileTransferProgressSnapshot? = null,
        val terminalFallback: BrowseFileTransferTerminalEvent? = null,
    ) {
        val isActive: Boolean
            get() = state != null && !state.isFinished

        val isTerminal: Boolean
            get() = state?.isFinished == true
    }

    sealed interface EnqueueResult {
        data class Enqueued(val workId: String) : EnqueueResult
        data object ActiveAlreadyRunning : EnqueueResult
    }

    private val workManager = WorkManager.getInstance(context)

    // S1663: opened on first use, not in the constructor. This is a @Singleton that the graph builds
    // while MainActivity is being injected, so the constructor runs on the main thread during startup -
    // and opening a preferences file there measured a 258 ms StrictMode DiskReadViolation, the longest
    // in the whole cold start. The three readers below all belong to terminal-event bookkeeping, which
    // cannot happen before a transfer runs, so a session that never transfers a file now pays nothing.
    private val prefs by lazy {
        context.getSharedPreferences("browse_file_transfer", Context.MODE_PRIVATE)
    }
    private val _terminalEvents = MutableSharedFlow<BrowseFileTransferTerminalEvent>(
        replay = 1,
        extraBufferCapacity = 2,
    )
    val terminalEvents: SharedFlow<BrowseFileTransferTerminalEvent> = _terminalEvents.asSharedFlow()

    fun activeTransferFlow(): Flow<TransferWorkState> {
        // S1230: the stored request is immutable for the lifetime of one work id, yet this map
        // used to re-read the JSON store (file read + Gson parse) on EVERY progress emission,
        // and on the collector's main thread - one StrictMode DiskReadViolation per tick. Cache
        // the request per work id and run the map upstream on IO so the single real read (and
        // the progress decode) never touches the UI thread. Non-finished == ENQUEUED, RUNNING
        // or BLOCKED; finished == SUCCEEDED, FAILED or CANCELLED - same sets as before.
        var cachedWorkId: String? = null
        var cachedRequest: BrowseFileTransferRequest? = null
        return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val workInfo = infos.firstOrNull() ?: return@map TransferWorkState()
            val progress = BrowseFileTransferProgressCodec.decodeProgress(workInfo.progress)
            val workId = workInfo.id.toString()
            TransferWorkState(
                workId = workId,
                state = workInfo.state,
                request = if (workInfo.state.isFinished) {
                    null
                } else {
                    if (cachedWorkId != workId) {
                        cachedRequest = requestStore.readActiveRequest()
                        cachedWorkId = workId
                    }
                    cachedRequest
                },
                progress = progress,
                terminalFallback = if (workInfo.state.isFinished) {
                    BrowseFileTransferProgressCodec.decodeTerminalFallback(workInfo.outputData)
                } else {
                    null
                },
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun enqueueIfIdle(request: BrowseFileTransferRequest): EnqueueResult = withContext(Dispatchers.IO) {
        if (hasActiveTransfer()) return@withContext EnqueueResult.ActiveAlreadyRunning
        requestStore.clearTerminalEvent()
        clearHandledTerminal()
        clearTerminalReplay()
        requestStore.writeActiveRequest(request)
        val workRequest = OneTimeWorkRequestBuilder<com.sza.fastmediasorter.worker.BrowseFileTransferWorker>()
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
        Timber.i("BrowseFileTransferCoordinator: enqueued workId=%s", workRequest.id)
        EnqueueResult.Enqueued(workRequest.id.toString())
    }

    suspend fun hasActiveTransfer(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            workManager.getWorkInfosForUniqueWork(WORK_NAME).get().any { !it.state.isFinished }
        }.getOrDefault(false)
    }

    suspend fun cancelActiveTransfer() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun readActiveRequest(): BrowseFileTransferRequest? = requestStore.readActiveRequest()

    fun consumeStoredTerminalEvent(): BrowseFileTransferTerminalEvent? =
        requestStore.consumeTerminalEvent()?.toEvent()

    fun clearStoredTerminalEvent() {
        requestStore.clearTerminalEvent()
    }

    suspend fun publishTerminalEvent(event: BrowseFileTransferTerminalEvent) {
        _terminalEvents.emit(event)
    }

    fun isTerminalHandled(workId: String): Boolean =
        prefs.getString(KEY_LAST_HANDLED_WORK_ID, null) == workId

    fun markTerminalHandled(workId: String) {
        prefs.edit().putString(KEY_LAST_HANDLED_WORK_ID, workId).apply()
    }

    fun clearHandledTerminal() {
        prefs.edit().remove(KEY_LAST_HANDLED_WORK_ID).apply()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearTerminalReplay() {
        _terminalEvents.resetReplayCache()
    }

    companion object {
        const val WORK_NAME = "browse_file_transfer_interactive"
        private const val KEY_LAST_HANDLED_WORK_ID = "last_handled_work_id"
    }
}
