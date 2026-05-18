package com.sza.fastmediasorter.wear.data.network.sftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * SFTP connection test for Wear OS network sources.
 *
 * Currently a stub - SSHJ (the SFTP library) is not bundled as a direct Wear dependency.
 * Returns Result.failure with a descriptive message so the user knows testing is
 * unavailable but the source can still be saved and used for browsing.
 */
class SftpConnectionTest @Inject constructor() {

    suspend fun testSftp(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.w("SftpConnectionTest: test not supported on Wear OS for source '${source.name}' - returning stub failure")
        Result.failure(UnsupportedOperationException("SFTP connection test is not available on Wear OS"))
    }
}
