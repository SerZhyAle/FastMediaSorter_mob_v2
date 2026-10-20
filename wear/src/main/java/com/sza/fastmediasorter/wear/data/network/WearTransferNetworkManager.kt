package com.sza.fastmediasorter.wear.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** How long a transfer waits for the watch's Wi-Fi or mobile network before naming its absence. */
private const val TRANSFER_NETWORK_TIMEOUT_MS = 10_000L

/** One registered request: [await] answers the granted network or null, [release] gives it back. */
internal interface PendingTransferNetwork {
    suspend fun await(timeoutMs: Long): Network?

    fun release()
}

/** The platform seam, so the release path can be driven in a unit test. */
internal fun interface TransferNetworkRequester {
    fun request(): PendingTransferNetwork
}

/**
 * S4227: holds the watch's own Wi-Fi or mobile network for exactly one clipboard transfer.
 *
 * On a watch the default route is often the Bluetooth proxy through the phone, and Wi-Fi and LTE come up only
 * while an app holds a request for them. The block receives the granted [Network] so the caller binds its
 * socket to it - a request alone does not move a connection off the proxy. The request is released on every
 * path, the caller's cancellation included, because a radio held after the transfer is paid in battery.
 */
@Singleton
class WearTransferNetworkManager internal constructor(
    private val requester: TransferNetworkRequester,
    private val timeoutMs: Long = TRANSFER_NETWORK_TIMEOUT_MS,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(ConnectivityTransferNetworkRequester(context))

    /** The block's result, or null when no Wi-Fi or mobile network came up in time - nothing ran then. */
    suspend fun <T> withTransferNetwork(block: suspend (Network) -> T): T? {
        val pending = requester.request()
        try {
            val network = pending.await(timeoutMs)
            if (network == null) {
                Timber.i("Clipboard transfer: no Wi-Fi or mobile network on the watch")
            }
            return network?.let { block(it) }
        } finally {
            pending.release()
        }
    }
}

private class ConnectivityTransferNetworkRequester(context: Context) : TransferNetworkRequester {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun request(): PendingTransferNetwork {
        val manager = connectivityManager ?: return NoNetwork
        val callback = AvailabilityCallback()
        manager.requestNetwork(transferRequest(), callback)
        return object : PendingTransferNetwork {
            override suspend fun await(timeoutMs: Long): Network? = withTimeoutOrNull(timeoutMs) { callback.await() }

            override fun release() = unregisterQuietly(manager, callback)
        }
    }

    // Either transport satisfies the request; contract decision В27.3 names Wi-Fi or LTE, never Bluetooth.
    private fun transferRequest(): NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private class AvailabilityCallback : ConnectivityManager.NetworkCallback() {
        private val available = CompletableDeferred<Network>()

        override fun onAvailable(network: Network) {
            available.complete(network)
        }

        suspend fun await(): Network = available.await()
    }

    private object NoNetwork : PendingTransferNetwork {
        override suspend fun await(timeoutMs: Long): Network? = null

        override fun release() = Unit
    }
}
