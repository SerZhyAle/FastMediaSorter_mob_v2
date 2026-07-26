package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * S1009: check whether an ad-hoc local folder (plain path or SAF tree Uri) is writable, so the
 * scheduled-op picker can reject a non-writable receiver and force a read-only sender to COPY. Bounded
 * by a timeout - a slow SAF probe resolves to "not writable" rather than hanging the dialog.
 */
class CheckLocalFolderWritableUseCase @Inject constructor(
    private val localMediaScanner: LocalMediaScanner,
) {
    suspend operator fun invoke(folderPath: String): Boolean = try {
        withTimeout(TimeUnit.SECONDS.toMillis(WRITE_CHECK_TIMEOUT_SECONDS)) {
            localMediaScanner.isWritable(folderPath, credentialsId = null)
        }
    } catch (_: TimeoutCancellationException) {
        false
    }

    private companion object {
        const val WRITE_CHECK_TIMEOUT_SECONDS = 5L
    }
}
