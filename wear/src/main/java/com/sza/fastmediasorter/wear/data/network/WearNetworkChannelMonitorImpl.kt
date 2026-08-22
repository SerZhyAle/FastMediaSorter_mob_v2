package com.sza.fastmediasorter.wear.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the watch's default network link and republishes it as a domain value.
 *
 * No coroutine is opened: the platform delivers callbacks on its own thread and a [MutableStateFlow]
 * accepts a write from any of them, so a scope here would add a hop and nothing else.
 */
@Singleton
class WearNetworkChannelMonitorImpl @Inject constructor(
    @ApplicationContext context: Context
) : WearNetworkChannelMonitor {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val state = MutableStateFlow(WearNetworkChannel.NONE)

    override val channel: StateFlow<WearNetworkChannel> = state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            state.value = networkCapabilities.toChannel()
        }

        override fun onLost(network: Network) {
            state.value = WearNetworkChannel.NONE
        }
    }

    init {
        connectivityManager?.registerDefaultNetworkCallback(callback)
    }

    /**
     * Undoes [init]'s registration. Nothing calls it in the app today and that is deliberate: this is
     * a `SingletonComponent` binding, so the callback is meant to outlive every screen and the process
     * takes it down. It exists because a registration with no matching release is one a test cannot
     * undo and a future non-singleton owner could not either.
     */
    fun close() {
        connectivityManager?.unregisterNetworkCallback(callback)
    }

    /**
     * An absent estimate and a zero one are the same answer from the platform, and neither means
     * "a link with no bandwidth" - both become null so the policy asks a separate question about it.
     */
    private fun NetworkCapabilities.toChannel(): WearNetworkChannel = WearNetworkChannel(
        kind = transportKind(),
        downstreamKbps = linkDownstreamBandwidthKbps.takeIf { it > 0 },
        upstreamKbps = linkUpstreamBandwidthKbps.takeIf { it > 0 },
        isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    )

    private fun NetworkCapabilities.transportKind(): WearNetworkChannelKind = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WearNetworkChannelKind.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> WearNetworkChannelKind.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> WearNetworkChannelKind.BLUETOOTH
        else -> WearNetworkChannelKind.OTHER
    }
}
