package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.ResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Callbacks for network changes
    private val callbacks = mutableListOf<NetworkChangeCallback>()

    // Raw last-seen network id (updated on every transition, before debounce settles).
    @Volatile
    private var lastNetworkId: String? = null

    // The network id consumers were last notified about. A raw transition only fires a callback once
    // the transport settles on a genuinely different id than this; a wlan<->cellular round-trip that
    // returns here within the settle window is suppressed (S1040).
    @Volatile
    private var lastNotifiedNetworkId: String? = null

    private val debounceLock = Any()
    private var settleJob: Job? = null

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
            cancelSettleEvaluation()
            lastNetworkId = null
            lastNotifiedNetworkId = null
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
     * Handle network change event. Only genuine raw transitions (a different network id than the
     * one last seen) schedule a debounced evaluation; capability/link ticks on the same network are
     * ignored. A transition back to the already-notified network cancels a pending evaluation, so a
     * transient wlan<->cellular round-trip never reaches consumers (S1040).
     */
    private fun handleNetworkChange(network: Network) {
        val currentNetworkId = getNetworkId(network)
        val previousRaw = lastNetworkId

        if (previousRaw == null) {
            Timber.i("NetworkStateMonitor: Network established: $currentNetworkId")
            lastNetworkId = currentNetworkId
            lastNotifiedNetworkId = currentNetworkId
            return
        }
        if (currentNetworkId == previousRaw) return

        lastNetworkId = currentNetworkId
        Timber.d("NetworkStateMonitor: Raw transition $previousRaw → $currentNetworkId (settle ${SETTLE_WINDOW_MS}ms)")

        if (currentNetworkId == lastNotifiedNetworkId) {
            cancelSettleEvaluation()
        } else {
            scheduleSettleEvaluation(currentNetworkId)
        }
    }

    /**
     * After a raw transition, wait [SETTLE_WINDOW_MS] for the transport to stabilise, then notify
     * consumers only if the settled network still differs from the one they know about. Rapid flaps
     * cancel and reschedule this, so only the trailing stable state is acted on.
     */
    private fun scheduleSettleEvaluation(latestNetworkId: String) {
        synchronized(debounceLock) {
            settleJob?.cancel()
            settleJob = appScope.launch {
                delay(SETTLE_WINDOW_MS)
                val settledId = getActiveNetworkId() ?: latestNetworkId
                val previous = lastNotifiedNetworkId
                if (settledId == previous) {
                    Timber.d("NetworkStateMonitor: Flap settled back on $settledId - no invalidation")
                    return@launch
                }
                lastNotifiedNetworkId = settledId
                Timber.w("NetworkStateMonitor: Network changed (settled): $previous → $settledId")
                notifyNetworkChanged()
            }
        }
    }

    private fun cancelSettleEvaluation() {
        synchronized(debounceLock) {
            settleJob?.cancel()
            settleJob = null
        }
    }

    private fun getActiveNetworkId(): String? =
        connectivityManager.activeNetwork?.let { getNetworkId(it) }
    
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
     * Synchronous snapshot: the active network can reach the public internet.
     * Used as the cheap pre-check before attempting a CLOUD upload.
     */
    fun isInternetAvailable(): Boolean = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    /**
     * Synchronous snapshot: the active network is a local LAN transport (Wi-Fi or Ethernet).
     * A local-network resource (SMB/SFTP/FTP) is unreachable without one regardless of cellular.
     */
    fun isLocalNetworkAvailable(): Boolean =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    /**
     * Whether a resource of [type] can plausibly be reached right now. LOCAL is always reachable;
     * SMB/SFTP/FTP need a LAN transport; CLOUD needs internet. The save flows use this for the
     * pre-write fallback decision; a runtime write failure is still caught separately as a safety net.
     */
    fun canReach(type: ResourceType): Boolean = when (type) {
        ResourceType.LOCAL -> true
        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> isLocalNetworkAvailable()
        ResourceType.CLOUD -> isInternetAvailable()
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> isInternetAvailable()
    }

    private fun activeCapabilities(): NetworkCapabilities? =
        connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

    private fun hasCapability(capability: Int): Boolean =
        activeCapabilities()?.hasCapability(capability) == true

    private fun hasTransport(transport: Int): Boolean =
        activeCapabilities()?.hasTransport(transport) == true

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
            
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                lastNetworkId = getNetworkId(activeNetwork)
                lastNotifiedNetworkId = lastNetworkId
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
            cancelSettleEvaluation()
            isMonitoring = false
            lastNetworkId = null
            lastNotifiedNetworkId = null
            Timber.i("NetworkStateMonitor: Stopped monitoring network state")
        } catch (e: Exception) {
            Timber.e(e, "NetworkStateMonitor: Failed to stop monitoring")
        }
    }

    companion object {
        // Settle window before a network transition is acted on. Sized to outlast the observed
        // 1-3s wlan<->cellular round-trip flaps so a transport that returns to the same network is
        // never surfaced as a change (S1040).
        private const val SETTLE_WINDOW_MS = 4_000L
    }
}
