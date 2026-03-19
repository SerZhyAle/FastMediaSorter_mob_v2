package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network state changes and notifies registered callbacks.
 * 
 * Detects WiFi reconnections and IP changes to allow automatic
 * recovery of network connections (SMB, FTP, SFTP).
 * 
 * Usage:
 * ```
 * networkStateMonitor.registerCallback(object : NetworkStateMonitor.NetworkChangeCallback {
 *     override fun onNetworkChanged() {
 *         // Handle network reconnect (e.g., invalidate connections)
 *     }
 *     override fun onNetworkLost() {
 *         // Handle network loss (e.g., close connections)
 *     }
 * })
 * ```
 */
@Singleton
class NetworkStateMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Callbacks for network changes
    private val callbacks = mutableListOf<NetworkChangeCallback>()
    
    // Current network state (network handle + interface name for stability)
    @Volatile
    private var lastNetworkId: String? = null
    
    @Volatile
    private var isMonitoring = false
    
    /**
     * Callback interface for network state changes.
     */
    interface NetworkChangeCallback {
        /**
         * Called when network changes (WiFi reconnect, IP change, etc.)
         * Existing network connections may be invalid.
         */
        fun onNetworkChanged()
        
        /**
         * Called when network is lost (WiFi disconnected, airplane mode, etc.)
         * All network connections are invalid.
         */
        fun onNetworkLost()
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("NetworkStateMonitor: Network available - ${network.networkHandle}")
            handleNetworkChange(network)
        }
        
        override fun onLost(network: Network) {
            Timber.w("NetworkStateMonitor: Network lost - ${network.networkHandle}")
            lastNetworkId = null
            notifyNetworkLost()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            // Network capabilities changed (e.g., WiFi strength, connection type)
            // Check if this represents a meaningful network change
            handleNetworkChange(network)
        }
        
        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            // IP address or DNS changed - this is a common WiFi reconnection scenario
            Timber.d("NetworkStateMonitor: Link properties changed - ${linkProperties.interfaceName}")
            handleNetworkChange(network)
        }
    }
    
    /**
     * Register callback to receive network state change notifications.
     * Thread-safe.
     */
    fun registerCallback(callback: NetworkChangeCallback) {
        synchronized(callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback)
                Timber.d("NetworkStateMonitor: Registered callback (total: ${callbacks.size})")
            }
        }
    }
    
    /**
     * Unregister previously registered callback.
     * Thread-safe.
     */
    fun unregisterCallback(callback: NetworkChangeCallback) {
        synchronized(callbacks) {
            callbacks.remove(callback)
            Timber.d("NetworkStateMonitor: Unregistered callback (remaining: ${callbacks.size})")
        }
    }
    
    /**
     * Handle network change event.
     * Compares current network ID with previous to detect actual changes.
     */
    private fun handleNetworkChange(network: Network) {
        val currentNetworkId = getNetworkId(network)
        
        if (lastNetworkId != null && lastNetworkId != currentNetworkId) {
            Timber.w("NetworkStateMonitor: Network changed: $lastNetworkId → $currentNetworkId")
            notifyNetworkChanged()
        } else if (lastNetworkId == null) {
            Timber.i("NetworkStateMonitor: Network established: $currentNetworkId")
        }
        
        lastNetworkId = currentNetworkId
    }
    
    /**
     * Generate unique network identifier from network handle and interface name.
     * This combination is stable across minor network changes but detects WiFi reconnections.
     */
    private fun getNetworkId(network: Network): String {
        val linkProperties = connectivityManager.getLinkProperties(network)
        return "${network.networkHandle}_${linkProperties?.interfaceName}"
    }
    
    /**
     * Notify all callbacks that network has changed.
     */
    private fun notifyNetworkChanged() {
        synchronized(callbacks) {
            Timber.d("NetworkStateMonitor: Notifying ${callbacks.size} callbacks of network change")
            callbacks.forEach { callback ->
                try {
                    callback.onNetworkChanged()
                } catch (e: Exception) {
                    Timber.e(e, "NetworkStateMonitor: Error in network change callback")
                }
            }
        }
    }
    
    /**
     * Notify all callbacks that network is lost.
     */
    private fun notifyNetworkLost() {
        synchronized(callbacks) {
            Timber.d("NetworkStateMonitor: Notifying ${callbacks.size} callbacks of network loss")
            callbacks.forEach { callback ->
                try {
                    callback.onNetworkLost()
                } catch (e: Exception) {
                    Timber.e(e, "NetworkStateMonitor: Error in network lost callback")
                }
            }
        }
    }
    
    /**
     * Start monitoring network state changes.
     * Should be called in Application.onCreate().
     */
    fun start() {
        if (isMonitoring) {
            Timber.w("NetworkStateMonitor: Already monitoring")
            return
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isMonitoring = true
            Timber.i("NetworkStateMonitor: Started monitoring network state")
            
            // Get initial network state
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                lastNetworkId = getNetworkId(activeNetwork)
                Timber.d("NetworkStateMonitor: Initial network: $lastNetworkId")
            }
        } catch (e: Exception) {
            Timber.e(e, "NetworkStateMonitor: Failed to start monitoring")
        }
    }
    
    /**
     * Stop monitoring network state changes.
     * Should be called in Application.onTerminate() or when monitoring is no longer needed.
     */
    fun stop() {
        if (!isMonitoring) {
            Timber.w("NetworkStateMonitor: Not currently monitoring")
            return
        }
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isMonitoring = false
            lastNetworkId = null
            Timber.i("NetworkStateMonitor: Stopped monitoring network state")
        } catch (e: Exception) {
            Timber.e(e, "NetworkStateMonitor: Failed to stop monitoring")
        }
    }
}
