package com.sza.fastmediasorter.domain.networkmonitor

import com.sza.fastmediasorter.domain.model.HostPort

/**
 * S2488: domain seam onto the SFTP reachable-endpoint choice, so a use case can ask which address of
 * a companion resource answers right now without depending on the networking implementation.
 *
 * Lives beside [HostProbe] rather than in `domain/usecase`, where the older [HostReachabilityChecker]
 * sits: a new type there must be named `*UseCase` or `*Repository` (Rule 6, gate
 * `class-architecture-naming`), and this is neither - it is a contract this package already collects.
 */
interface ReachableEndpointProvider {
    /**
     * The candidate group for [host]:[port] in send order. Never empty. The first element is the
     * endpoint reachable now, or the requested pair when no candidate answers.
     */
    suspend fun orderedEndpoints(host: String, port: Int): List<HostPort>
}
