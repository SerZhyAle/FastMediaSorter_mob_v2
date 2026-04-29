package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous reachability gate. Throws [NetworkConnectionLostException] before any
 * socket operation when the active network does not satisfy the requested transport
 * precondition. Used by remote data sources to avoid wasting timeouts on attempts
 * that are guaranteed to fail.
 */
@Singleton
class NetworkReachabilityGate @Inject constructor(
    private val analyzer: NetworkContextAnalyzer
) {

    /**
     * Universal no-network gate. Throws [NetworkConnectionLostException] if no
     * transport (Wi-Fi, cellular, ethernet) is currently active.
     *
     * @param resourceLabel free-form diagnostic tag (e.g. "SMB", "FTP", "Cloud-GDrive")
     */
    fun requireAnyNetwork(resourceLabel: String) {
        if (!analyzer.hasAnyNetwork()) {
            Timber.w("NetworkReachabilityGate: no-network for $resourceLabel")
            throw NetworkConnectionLostException()
        }
    }

    /**
     * SMB-specific Wi-Fi gate. Throws [NetworkConnectionLostException] if no
     * Wi-Fi (or ethernet) transport is active. Delegates to [requireAnyNetwork]
     * first so a complete absence of network produces the same generic outcome.
     *
     * @param resourceLabel free-form diagnostic tag
     */
    fun requireWifi(resourceLabel: String) {
        requireAnyNetwork(resourceLabel)
        if (!analyzer.hasWifi()) {
            Timber.w("NetworkReachabilityGate: no-wifi for $resourceLabel")
            throw NetworkConnectionLostException()
        }
    }
}
