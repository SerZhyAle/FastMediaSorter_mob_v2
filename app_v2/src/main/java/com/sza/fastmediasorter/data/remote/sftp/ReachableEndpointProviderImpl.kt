package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.networkmonitor.ReachableEndpointProvider
import javax.inject.Inject

/** S2488: thin delegate over [SftpEndpointResolver], holding no state of its own. */
class ReachableEndpointProviderImpl @Inject constructor(
    private val resolver: SftpEndpointResolver
) : ReachableEndpointProvider {

    override suspend fun orderedEndpoints(host: String, port: Int): List<HostPort> =
        resolver.orderedEndpoints(host, port)
}
