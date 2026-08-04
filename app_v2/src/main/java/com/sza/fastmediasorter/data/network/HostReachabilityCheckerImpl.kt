package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.domain.usecase.HostReachabilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1025: reuses [SmbErrorClassifier.checkConnectivity] (protocol-agnostic TCP connect) for the
 * batch-transfer pre-flight probe. The connect blocks, so it runs on the IO dispatcher.
 *
 * S1320: the destination is resolved through [SftpEndpointResolver] before the probe. A companion
 * resource carries several addresses for the same server (LAN plus port-forward) and every transfer
 * handler already connects through that resolver, so probing the raw address parsed out of the
 * destination path aborted the whole batch whenever the stored address was the LAN one and the
 * phone was off the LAN - while the transfer itself would have connected over the other address.
 * A single-address resource resolves to itself, so fail-fast on a genuinely dead host is unchanged.
 */
class HostReachabilityCheckerImpl @Inject constructor(
    private val endpointResolver: SftpEndpointResolver,
) : HostReachabilityChecker {

    override suspend fun isReachable(host: String, port: Int, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            val endpoint = endpointResolver.resolve(host, port)
            if (endpoint.host != host || endpoint.port != port) {
                Timber.d("S1320: probe redirected $host:$port -> ${endpoint.host}:${endpoint.port}")
            }
            SmbErrorClassifier.checkConnectivity(endpoint.host, endpoint.port, timeoutMs)
        }
}
