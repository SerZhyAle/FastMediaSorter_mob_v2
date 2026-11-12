package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.filler.FILLER_RESERVE_BYTES
import com.sza.fastmediasorter.domain.filler.FillerEngine
import com.sza.fastmediasorter.domain.filler.FillerPlace
import com.sza.fastmediasorter.domain.filler.FillerResult
import com.sza.fastmediasorter.domain.filler.FillerStorage
import com.sza.fastmediasorter.domain.filler.FillerTargetResolver
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import timber.log.Timber
import javax.inject.Inject

/**
 * S4522: one scheduled FILL_FREE_SPACE run. The storage filler owns the volume checks, the reserve, the
 * journal and the cleanup; this class only turns its verdict into a schedule result, so the scheduled and
 * the interactive run cannot drift apart in what they write or delete.
 */
class RunScheduledFillUseCase @Inject constructor(
    private val engine: FillerEngine,
    private val resolver: FillerTargetResolver,
    private val storage: FillerStorage,
) {
    /** The place a schedule may fill: on a mounted local volume and writable now, otherwise null. */
    suspend fun usablePlace(location: String): FillerPlace? =
        resolver.place(location)?.takeIf { storage.validate(it.target) }

    /** The run's result; a cancellation is rethrown after the engine has deleted what it wrote. */
    suspend operator fun invoke(operationId: Long, source: MediaResource): ScheduledExecutionResult {
        Timber.d("S4522: scheduled fill run started")
        val place = resolver.place(source.path)
        return when {
            place == null -> error(
                operationId,
                ScheduledRunReason.FILL_NOT_LOCAL,
                "'${source.name}' is not on a local storage device",
            )
            else -> {
                cleanEarlierSessions()
                val result = engine.run(place.target, FILLER_RESERVE_BYTES)
                Timber.i("ScheduledOp[$operationId] fill finished: ${result::class.simpleName}")
                resultOf(operationId, source, result)
            }
        }
    }

    private fun resultOf(operationId: Long, source: MediaResource, result: FillerResult): ScheduledExecutionResult =
        when (result) {
            FillerResult.Busy ->
                skipped(operationId, ScheduledRunReason.FILL_BUSY, "Storage filler is already running")
            FillerResult.InsufficientSpace ->
                skipped(operationId, ScheduledRunReason.FILL_NO_FREE_SPACE, "No free space above the reserve")
            FillerResult.InvalidTarget -> error(
                operationId,
                ScheduledRunReason.FILL_STORAGE_UNAVAILABLE,
                "'${source.name}' is not connected or not writable",
            )
            FillerResult.MeasurementUnavailable ->
                error(operationId, ScheduledRunReason.FILL_MEASUREMENT_UNAVAILABLE, "Free space could not be measured")
            FillerResult.PendingCleanup ->
                error(operationId, ScheduledRunReason.FILL_CLEANUP_PENDING, CLEANUP_PENDING_TEXT)
            is FillerResult.Finished -> if (result.cleanupComplete) {
                ScheduledExecutionResult(operationId, 0, emptyList())
            } else {
                error(operationId, ScheduledRunReason.FILL_CLEANUP_PENDING, CLEANUP_PENDING_TEXT)
            }
            is FillerResult.Failed -> if (result.cleanupComplete) {
                error(operationId, ScheduledRunReason.FILL_WRITE_FAILED, "Writing failed; temporary files deleted")
            } else {
                error(operationId, ScheduledRunReason.FILL_CLEANUP_PENDING, CLEANUP_PENDING_TEXT)
            }
        }

    /**
     * A run interrupted earlier - a removed card, a killed process - leaves a journaled session the engine
     * refuses to start over. Retrying its cleanup here lets the next slot recover on its own once the
     * storage is back. The verdict is left to the engine's own run: a session that stays is its
     * PendingCleanup, and a session that is the interactive screen's live run is its Busy.
     */
    private suspend fun cleanEarlierSessions() {
        engine.pendingSessions().forEach { engine.retryCleanup(it) }
    }

    private fun error(operationId: Long, reason: ScheduledRunReason, text: String) =
        ScheduledExecutionResult(operationId, 0, listOf(text), reason = reason)

    private fun skipped(operationId: Long, reason: ScheduledRunReason, text: String) =
        ScheduledExecutionResult(operationId, 0, emptyList(), skippedReason = text, reason = reason)

    private companion object {
        const val CLEANUP_PENDING_TEXT = "Temporary files from a fill are still on the storage"
    }
}
