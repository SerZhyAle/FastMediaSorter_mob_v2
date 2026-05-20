package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.cloud.TransferProgress
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * S0266 — Adapts the cloud provider's `(TransferProgress) -> Unit` channel into the project's
 * [ByteProgressCallback] contract. Computes the transfer speed locally (cloud clients do not
 * expose it) and throttles by [ByteProgressCallback.PROGRESS_REPORT_INTERVAL] so the UI is not
 * flooded.
 *
 * Returns null when [callback] is null so callers can wire the result straight into the cloud
 * client signature without a wrapping if-check.
 */
fun adaptCloudProgress(
    callback: ByteProgressCallback?,
    scope: CoroutineScope,
): ((TransferProgress) -> Unit)? {
    if (callback == null) return null
    val startTimeMs = System.currentTimeMillis()
    val lastEmittedBytes = AtomicLong(0L)
    return { progress: TransferProgress ->
        val previous = lastEmittedBytes.get()
        if (progress.bytesTransferred - previous >= ByteProgressCallback.PROGRESS_REPORT_INTERVAL) {
            if (lastEmittedBytes.compareAndSet(previous, progress.bytesTransferred)) {
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                val speed = if (elapsedMs > 0L) progress.bytesTransferred * 1000L / elapsedMs else 0L
                scope.launch {
                    callback.onProgress(progress.bytesTransferred, progress.totalBytes, speed)
                }
            }
        }
    }
}
