package com.sza.fastmediasorter.domain.usecase

/**
 * S1025: single pre-flight TCP reachability probe for a network-transfer destination.
 * Abstracted so the batch use case can be unit-tested without opening real sockets, and so the
 * blocking connect stays behind the data layer.
 */
interface HostReachabilityChecker {
    /** True when a TCP socket to [host]:[port] connects within [timeoutMs]; false otherwise. */
    suspend fun isReachable(host: String, port: Int, timeoutMs: Int): Boolean
}
