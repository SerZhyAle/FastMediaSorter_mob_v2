package com.sza.fastmediasorter.ui.player.fileops

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PlayerFileOperationQueue(
    private val scope: CoroutineScope,
    private val fileOperationUseCase: FileOperationUseCase,
    private val settingsRepository: SettingsRepository,
) {
    private data class PermissionGate(
        val op: PlayerFileOperation,
        val deferred: CompletableDeferred<Boolean>,
    )

    private val lock = Any()
    private val pendingOps = ArrayDeque<PlayerFileOperation>()
    private var currentOp: PlayerFileOperation? = null
    private var workerJob: Job? = null
    private var acceptingNewOps = true
    private var permissionGate: PermissionGate? = null

    private val _events = MutableSharedFlow<PlayerFileOperationEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )

    // SharedFlow keeps queue events lifecycle-friendly without a long-lived callback reference.
    val events: SharedFlow<PlayerFileOperationEvent> = _events.asSharedFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun enqueue(op: PlayerFileOperation) {
        var shouldStartWorker = false
        synchronized(lock) {
            if (!acceptingNewOps) {
                Timber.w("PlayerFileOperationQueue: dropping %s after shutdown", op.id)
                return
            }
            pendingOps.addLast(op)
            updatePendingCountLocked()
            shouldStartWorker = workerJob?.isActive != true
        }
        _events.tryEmit(PlayerFileOperationEvent.Enqueued(op))
        if (shouldStartWorker) {
            startWorker()
        }
    }

    fun resumeAfterPermission(granted: Boolean, op: PlayerFileOperation) {
        val gate = synchronized(lock) {
            val activeGate = permissionGate
            if (activeGate == null || activeGate.op.id != op.id) {
                Timber.w(
                    "PlayerFileOperationQueue: no matching permission gate for %s (granted=%s)",
                    op.id,
                    granted,
                )
                return
            }
            permissionGate = null
            activeGate.deferred
        }
        gate.complete(granted)
    }

    fun snapshotPending(): List<PlayerFileOperation> {
        synchronized(lock) {
            return buildList {
                currentOp?.let(::add)
                addAll(pendingOps)
            }
        }
    }

    /** Pending operations are session-only and intentionally discarded on shutdown/process death. */
    fun shutdown() {
        val gateToRelease: CompletableDeferred<Boolean>?
        val workerToCancel: Job?
        synchronized(lock) {
            acceptingNewOps = false
            pendingOps.clear()
            gateToRelease = permissionGate?.deferred
            permissionGate = null
            workerToCancel = if (currentOp == null) workerJob else null
            if (currentOp == null) {
                workerJob = null
            }
            updatePendingCountLocked()
        }
        gateToRelease?.complete(false)
        workerToCancel?.cancel()
    }

    private fun startWorker() {
        synchronized(lock) {
            if (workerJob?.isActive == true) {
                return
            }
            workerJob = scope.launch {
                try {
                    consumeQueue()
                } finally {
                    synchronized(lock) {
                        if (workerJob === this.coroutineContext[Job]) {
                            workerJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun consumeQueue() {
        while (currentCoroutineContext().isActive) {
            val op = synchronized(lock) {
                if (currentOp != null) {
                    currentOp
                } else {
                    val next = pendingOps.removeFirstOrNull() ?: return
                    currentOp = next
                    updatePendingCountLocked()
                    next
                }
            } ?: return

            processOperation(op)

            val shouldEmitDrained = synchronized(lock) {
                currentOp = null
                updatePendingCountLocked()
                pendingOps.isEmpty()
            }
            if (shouldEmitDrained) {
                _events.emit(PlayerFileOperationEvent.Drained)
            }

            val shouldStop = synchronized(lock) {
                !acceptingNewOps && pendingOps.isEmpty() && currentOp == null
            }
            if (shouldStop) {
                return
            }
        }
    }

    private suspend fun processOperation(op: PlayerFileOperation) {
        while (currentCoroutineContext().isActive) {
            _events.emit(PlayerFileOperationEvent.Started(op))

            try {
                val settings = settingsRepository.getSettings().first()
                val result = withContext(Dispatchers.IO) {
                    fileOperationUseCase.execute(op.toDomainFileOperation(settings))
                }

                when (result) {
                    is FileOperationResult.Success -> {
                        _events.emit(PlayerFileOperationEvent.Succeeded(op, result.processedCount))
                        return
                    }

                    is FileOperationResult.PartialSuccess -> {
                        _events.emit(PlayerFileOperationEvent.Succeeded(op, result.processedCount))
                        return
                    }

                    is FileOperationResult.Failure -> {
                        _events.emit(
                            PlayerFileOperationEvent.Failed(
                                op = op,
                                message = result.error,
                                retryable = true,
                            )
                        )
                        return
                    }

                    is FileOperationResult.AuthenticationRequired -> {
                        _events.emit(
                            PlayerFileOperationEvent.AuthRequired(
                                op = op,
                                provider = result.provider,
                                message = result.message,
                            )
                        )
                        return
                    }

                    is FileOperationResult.PermissionRequired -> {
                        val gate = CompletableDeferred<Boolean>()
                        synchronized(lock) {
                            permissionGate = PermissionGate(op = op, deferred = gate)
                        }
                        _events.emit(
                            PlayerFileOperationEvent.PermissionRequired(
                                op = op,
                                pendingIntent = result.pendingIntent,
                            )
                        )
                        if (!gate.await()) {
                            _events.emit(
                                PlayerFileOperationEvent.Failed(
                                    op = op,
                                    message = "permission_denied",
                                    retryable = true,
                                )
                            )
                            return
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    throw cancelled
                }
                _events.emit(
                    PlayerFileOperationEvent.Failed(
                        op = op,
                        message = cancelled.message ?: "cancelled",
                        retryable = false,
                    )
                )
                return
            } catch (error: Exception) {
                Timber.e(error, "PlayerFileOperationQueue: queued op failed for %s", op.sourcePath)
                _events.emit(
                    PlayerFileOperationEvent.Failed(
                        op = op,
                        message = error.message ?: "unexpected_error",
                        retryable = true,
                    )
                )
                return
            }
        }
    }

    private fun updatePendingCountLocked() {
        _pendingCount.value = pendingOps.size + if (currentOp != null) 1 else 0
    }
}