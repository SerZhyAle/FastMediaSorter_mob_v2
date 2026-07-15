package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.domain.usecase.HostReachabilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1025: reuses [SmbErrorClassifier.checkConnectivity] (protocol-agnostic TCP connect) for the
 * batch-transfer pre-flight probe. The connect blocks, so it runs on the IO dispatcher.
 */
class HostReachabilityCheckerImpl @Inject constructor() : HostReachabilityChecker {

    override suspend fun isReachable(host: String, port: Int, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            SmbErrorClassifier.checkConnectivity(host, port, timeoutMs)
        }
}
