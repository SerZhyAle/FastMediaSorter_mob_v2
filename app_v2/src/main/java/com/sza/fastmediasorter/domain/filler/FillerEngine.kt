package com.sza.fastmediasorter.domain.filler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A target must be validated as a writable directory on a mounted local volume by the adapter. */
data class FillerTarget(val location: String, val volumeId: String)

data class FillerSession(val id: String, val target: FillerTarget)

data class FillerFile(val id: String)

/** A target with the names a person recognises it by. */
data class FillerPlace(val target: FillerTarget, val volumeName: String, val folderName: String)

/** Turns a picked folder address into a target; null means it is not on a local mounted volume. */
interface FillerTargetResolver {
    suspend fun grant(location: String): Boolean
    suspend fun place(location: String): FillerPlace?
}

/** Space left untouched so the system and other apps keep working while the volume is full. */
const val FILLER_RESERVE_BYTES = 1L shl 30

/** Null free space means measurement failed and must never be treated as a full volume. */
interface FillerStorage {
    suspend fun validate(target: FillerTarget): Boolean
    suspend fun availableBytes(target: FillerTarget): Long?
    suspend fun create(session: FillerSession, index: Int): FillerFile
    suspend fun append(file: FillerFile, count: Int)

    /** Delete only verified session-owned files, including safely recoverable pending creates. */
    suspend fun cleanup(session: FillerSession): Boolean
}

/** Implementations persist a pending creation before the storage adapter creates a file. */
interface FillerJournal {
    suspend fun pendingSessions(): List<FillerSession>
    suspend fun begin(target: FillerTarget): FillerSession
    suspend fun prepareCreate(session: FillerSession, index: Int)
    suspend fun recordCreated(session: FillerSession, index: Int, file: FillerFile)
    suspend fun finish(session: FillerSession)
}

sealed interface FillerResult {
    data object Busy : FillerResult
    data object InvalidTarget : FillerResult
    data object MeasurementUnavailable : FillerResult
    data object InsufficientSpace : FillerResult
    data object PendingCleanup : FillerResult
    data class Finished(val writtenBytes: Long, val cleanupComplete: Boolean) : FillerResult
    data class Failed(val cleanupComplete: Boolean) : FillerResult
}

/** Coordinates the write and cleanup protocol; storage and journal own all persistent state. */
class FillerEngine @Inject constructor(
    private val storage: FillerStorage,
    private val journal: FillerJournal,
) {
    suspend fun run(
        target: FillerTarget,
        reserveBytes: Long,
        onProgress: (Long) -> Unit = {},
    ): FillerResult {
        if (!operationLock.tryLock()) return FillerResult.Busy
        try {
            return if (journal.pendingSessions().isNotEmpty()) {
                FillerResult.PendingCleanup
            } else if (reserveBytes <= 0 || !storage.validate(target)) {
                FillerResult.InvalidTarget
            } else {
                val available = storage.availableBytes(target)
                when {
                    available == null -> FillerResult.MeasurementUnavailable
                    available <= reserveBytes -> FillerResult.InsufficientSpace
                    else -> execute(target, reserveBytes, onProgress)
                }
            }
        } finally {
            operationLock.unlock()
        }
    }

    private suspend fun execute(
        target: FillerTarget,
        reserveBytes: Long,
        onProgress: (Long) -> Unit,
    ): FillerResult {
        val session = journal.begin(target)
        var written = 0L
        var failed = false
        var cancelled: CancellationException? = null
        try {
            written = writeFiles(session, reserveBytes, onProgress)
        } catch (exception: CancellationException) {
            cancelled = exception
        } catch (_: Exception) {
            failed = true
        }
        val cleaned = cleanup(session)
        cancelled?.let { throw it }
        return if (failed) FillerResult.Failed(cleaned) else FillerResult.Finished(written, cleaned)
    }

    suspend fun pendingSessions(): List<FillerSession> = journal.pendingSessions()

    suspend fun retryCleanup(session: FillerSession): Boolean {
        if (!operationLock.tryLock()) return false
        try {
            return if (session in journal.pendingSessions()) cleanup(session) else false
        } finally {
            operationLock.unlock()
        }
    }

    private suspend fun cleanup(session: FillerSession): Boolean = withContext(NonCancellable) {
        try {
            if (storage.cleanup(session)) {
                journal.finish(session)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
    private suspend fun writeFiles(
        session: FillerSession,
        reserveBytes: Long,
        onProgress: (Long) -> Unit,
    ): Long {
        var total = 0L
        var index = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val available = storage.availableBytes(session.target)
                ?: error("Storage measurement unavailable")
            if (available <= reserveBytes) return total
            journal.prepareCreate(session, index)
            val file = storage.create(session, index)
            journal.recordCreated(session, index, file)
            var fileBytes = 0L
            while (fileBytes < FILE_BYTES) {
                currentCoroutineContext().ensureActive()
                val current = storage.availableBytes(session.target)
                    ?: error("Storage measurement unavailable")
                val remaining = current - reserveBytes
                if (remaining <= 0) return total
                val count = minOf(remaining, FILE_BYTES - fileBytes, BUFFER_BYTES.toLong()).toInt()
                storage.append(file, count)
                fileBytes += count
                total += count
                onProgress(total)
            }
            index++
        }
    }

    private companion object {
        val operationLock = Mutex()
        const val BUFFER_BYTES = 1024 * 1024
        const val FILE_BYTES = 100L * 1024 * 1024
    }
}
