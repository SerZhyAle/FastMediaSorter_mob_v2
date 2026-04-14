package com.sza.fastmediasorter.wear.data.network.ftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * FTP connection test for Wear OS network sources.
 *
 * Currently a stub — Apache Commons Net is not bundled as a direct Wear dependency.
 * Returns Result.failure with a descriptive message so the user knows testing is
 * unavailable but the source can still be saved and used for browsing.
 */
class FtpConnectionTest @Inject constructor() {

    suspend fun testFtp(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.w("FtpConnectionTest: test not supported on Wear OS for source '${source.name}' — returning stub failure")
        Result.failure(UnsupportedOperationException("FTP connection test is not available on Wear OS"))
    }
}
