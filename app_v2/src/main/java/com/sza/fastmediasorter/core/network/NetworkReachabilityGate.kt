package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous reachability gate. Throws [NetworkConnectionLostException] before any
 * socket operation when the active network does not satisfy the requested transport
 * precondition, and [LocalNetworkPermissionDeniedException] when a local-network host
 * is addressed without the ACCESS_LOCAL_NETWORK grant. Used by remote data sources to
 * avoid wasting timeouts on attempts that are guaranteed to fail.
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
            // WifiRequiredException signals that the Wi-Fi gate fired before any socket attempt -
            // UIs should explain the Wi-Fi requirement, not a generic connection outage.
            throw WifiRequiredException()
        }
    }

    /**
     * Returns the refusal for a socket to [host] opened without the ACCESS_LOCAL_NETWORK grant, or
     * null when the grant is held or [host] is not a literal local-network address. Result-returning
     * callers hand the exception back as a failure instead of throwing it.
     *
     * Without the grant the OS stalls the connect until the socket timeout, which looks exactly like
     * a dead server to every caller that did not check the grant first.
     *
     * @param resourceLabel free-form diagnostic tag
     * @param host bare host of the endpoint about to be contacted, without a port
     */
    fun localNetworkRefusal(resourceLabel: String, host: String): LocalNetworkPermissionDeniedException? {
        if (analyzer.hasLocalNetworkPermission() || !analyzer.isLocalNetworkHost(host)) return null
        Timber.w("NetworkReachabilityGate: local-network permission missing for $resourceLabel")
        Timber.d("S4514: gate refused $resourceLabel before any socket attempt")
        return LocalNetworkPermissionDeniedException()
    }

    /**
     * Throwing form of [localNetworkRefusal] for callers whose failures are exceptions.
     *
     * @param resourceLabel free-form diagnostic tag
     * @param host bare host of the endpoint about to be contacted, without a port
     */
    fun requireLocalNetworkPermission(resourceLabel: String, host: String) {
        localNetworkRefusal(resourceLabel, host)?.let { throw it }
    }
}
