package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes current network context to help produce meaningful error messages
 * and to drive synchronous reachability gates.
 *
 * Capabilities:
 * - Detect if any network transport is currently active
 * - Detect if the active connection is Wi-Fi (or ethernet)
 * - Detect if the active connection is cellular (3G/4G/5G)
 * - Detect if a host IP is a private (LAN-only) address
 * - Extract host from a resource path URI
 */
@Singleton
class NetworkContextAnalyzer @Inject constructor(
    @ApplicationContext context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Returns true if any network transport is currently active (Wi-Fi, cellular, ethernet, etc.).
     * Used by the universal no-network gate.
     */
    fun hasAnyNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Returns true if active transport is Wi-Fi or ethernet (i.e. a local-network-capable link).
     * Used by the SMB-specific Wi-Fi gate.
     */
    fun hasWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Returns true if the active network transport is cellular (3G/4G/5G).
     */
    fun isCellularNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    /**
     * Returns true if [host] is a private (RFC 1918) IPv4 address:
     * - 10.0.0.0/8
     * - 172.16.0.0/12
     * - 192.168.0.0/16
     *
     * Returns false for hostnames (FQDNs) or public IPs.
     */
    fun isPrivateIpAddress(host: String): Boolean {
        // Strip port if present (e.g., "192.168.1.1:445")
        val cleanHost = host.substringBefore(':')
        return try {
            val parts = cleanHost.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toInt() }
            when {
                octets[0] == 10 -> true
                octets[0] == 172 && octets[1] in 16..31 -> true
                octets[0] == 192 && octets[1] == 168 -> true
                else -> false
            }
        } catch (e: Exception) {
            Timber.v("NetworkContextAnalyzer: not a parseable IP: $host")
            false
        }
    }

    /**
     * Extracts the host from a resource path URI (e.g., "smb://192.168.1.10/share" → "192.168.1.10").
     * Returns the original path on parse failure.
     */
    fun extractHost(resourcePath: String): String {
        return try {
            URI(resourcePath).host ?: resourcePath
        } catch (e: Exception) {
            Timber.v("NetworkContextAnalyzer: failed to parse URI host from: $resourcePath")
            resourcePath
        }
    }
}
