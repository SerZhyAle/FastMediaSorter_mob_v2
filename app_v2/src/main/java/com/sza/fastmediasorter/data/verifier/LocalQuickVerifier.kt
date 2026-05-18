package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 - local filesystem probe.
 *
 * The cheapest of the four - `File.exists()` is a stat() syscall, no network, no
 * connection budget. No throttling needed (`ProtocolLimits.LOCAL` is a no-op anyway).
 *
 * Files Touched: only this file. Tested-via: BrowseActivity.onResume() → Reconciler
 * → QuickVerifierDispatcher.probe(resourceId, …) for LOCAL resources.
 */
@Singleton
class LocalQuickVerifier @Inject constructor() : QuickVerifier {

    override suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            try {
                paths.filter { !File(it).exists() }
            } catch (e: Exception) {
                Timber.w(e, "QuickVerifier(LOCAL): probe error for resource=%d, returning no-op", resourceId)
                emptyList()
            }
        }
}
