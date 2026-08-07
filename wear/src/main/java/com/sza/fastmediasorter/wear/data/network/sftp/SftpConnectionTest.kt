package com.sza.fastmediasorter.wear.data.network.sftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * SFTP connection test for Wear OS network sources.
 *
 * Currently a stub: the connection test is not implemented on Wear. The SFTP library itself IS on
 * the Wear classpath (com.github.mwiede:jsch, wear/build.gradle.kts), so this is a wiring gap, not
 * a missing dependency - the earlier comment here claimed the opposite and named a library the
 * project retired. Returns Result.failure with a descriptive message so the user knows testing is
 * unavailable but the source can still be saved and used for browsing.
 */
class SftpConnectionTest @Inject constructor() {

    suspend fun testSftp(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.w("SftpConnectionTest: test not supported on Wear OS for source '${source.name}' - returning stub failure")
        Result.failure(UnsupportedOperationException("SFTP connection test is not available on Wear OS"))
    }
}
