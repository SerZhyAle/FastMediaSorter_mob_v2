package com.sza.fastmediasorter.wear.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.sza.fastmediasorter.wear.domain.repository.BroadcastNetworkLease
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** How long a broadcast start waits for Wi-Fi before telling the owner there is none. */
private const val BROADCAST_NETWORK_TIMEOUT_MS = 8_000L

/**
 * The platform throws when a callback that was never registered - or was already unregistered - is
 * handed back. Neither is worth failing a teardown over, and both are reachable when a stop races a
 * start that timed out.
 */
private fun unregisterQuietly(manager: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
    try {
        manager.unregisterNetworkCallback(callback)
    } catch (e: IllegalArgumentException) {
        Timber.i(e, "The broadcast network callback was already unregistered")
    }
}

/** Undoes one wide-band request. Separated from the manager so the release path can be driven in a test. */
internal fun interface WideChannelHandle {
    fun release()
}

/** The platform seam: everything in this file that is not testable off-device lives behind it. */
internal fun interface WideChannelRequester {
    fun request(): WideChannelHandle
}

/** S2509's seam, kept separate because its answer carries an address and may legitimately be absent. */
internal fun interface BroadcastNetworkRequester {
    suspend fun acquire(): BroadcastNetworkLease?
}

/**
 * Asks for a wide-band transport while a stream plays and gives it back the moment the stream stops,
 * and - since S2509 - lends an addressed one to a broadcast for as long as that broadcast is on air.
 *
 * Concurrent holds of the block form are counted rather than stacked: two streams share one platform
 * request, and the request is released when the last of them finishes, never when the first does.
 *
 * The broadcast lease is deliberately not part of that count. It is owned by a foreground session
 * rather than by a block, its lifetime is the owner's decision rather than a coroutine's, and folding
 * it into the counter would let a stream that ended release the network out from under a live
 * broadcast.
 */
@Singleton
class StreamNetworkHoldManager internal constructor(
    private val requester: WideChannelRequester,
    private val broadcastRequester: BroadcastNetworkRequester = BroadcastNetworkRequester { null }
) : StreamNetworkHold {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        ConnectivityWideChannelRequester(context),
        ConnectivityBroadcastNetworkRequester(context)
    )

    private val lock = Any()

    private var holds = 0

    private var handle: WideChannelHandle? = null

    override suspend fun <T> withWideChannel(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            // Also the cancellation path: leaving the player must return the radio, and nothing
            // catches CancellationException here, so it keeps propagating after the release.
            releaseOne()
        }
    }

    override suspend fun acquireBroadcastNetwork(): BroadcastNetworkLease? = broadcastRequester.acquire()

    /**
     * The count rises only after the request succeeded. Incrementing first would strand the counter
     * above zero when `request()` throws - the caller never reaches the `finally` that would undo it -
     * and every later stream would then skip the request because a hold appeared to be open already.
     */
    private fun acquire() = synchronized(lock) {
        if (holds == 0) {
            handle = requester.request()
        }
        holds++
    }

    private fun releaseOne() = synchronized(lock) {
        if (--holds == 0) {
            handle?.release()
            handle = null
        }
    }
}

private class ConnectivityWideChannelRequester(context: Context) : WideChannelRequester {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun request(): WideChannelHandle {
        val callback = object : ConnectivityManager.NetworkCallback() {}
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager?.requestNetwork(request, callback)
        return WideChannelHandle { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}

/**
 * S2509 Phase 03: the half that was named "network hold" and held nothing usable.
 *
 * Before this, the request was made and the `Network` it produced was dropped on the floor, so the
 * server bound to whatever interface the platform happened to pick and reported an address the hold
 * did not necessarily cover. Here the callback is kept alive for the lease's whole life, and the
 * address is read from the held network's own [LinkProperties] rather than by walking every interface
 * on the device - so the address a listener is given and the network the watch is keeping up are the
 * same one by construction.
 *
 * A network that never arrives, or one with no routable IPv4 address, is answered as `null` rather
 * than waited on forever: strategic §5.1 makes an unreachable watch a refusal the owner must see.
 */
private class ConnectivityBroadcastNetworkRequester(context: Context) : BroadcastNetworkRequester {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun acquire(): BroadcastNetworkLease? {
        val manager = connectivityManager ?: return null
        val callback = AvailabilityCallback()
        manager.requestNetwork(broadcastRequest(), callback)
        val lease = withTimeoutOrNull(BROADCAST_NETWORK_TIMEOUT_MS) { callback.awaitLease(manager) }
        if (lease == null) {
            // The one path that must not leak the callback: a timeout and a network with no usable
            // address both land here, and neither produced a lease whose release() would undo it.
            unregisterQuietly(manager, callback)
            Timber.i("No usable Wi-Fi for a broadcast; refusing the start rather than serving nowhere")
        }
        return lease
    }

    private fun broadcastRequest(): NetworkRequest = NetworkRequest.Builder()
        // Wi-Fi and no cellular: a broadcast is served to listeners on the same local network, so a
        // metered link that satisfies "internet" would satisfy the request and serve nobody.
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    private class AvailabilityCallback : ConnectivityManager.NetworkCallback() {

        private val available = CompletableDeferred<Network>()

        override fun onAvailable(network: Network) {
            available.complete(network)
        }

        suspend fun awaitLease(manager: ConnectivityManager): BroadcastNetworkLease? {
            val network = available.await()
            val address = routableAddressOf(manager.getLinkProperties(network))
            return address?.let { found ->
                HeldNetwork(found) { unregisterQuietly(manager, this@AvailabilityCallback) }
            }
        }

        private fun routableAddressOf(properties: LinkProperties?): InetAddress? = properties
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
    }

    /**
     * Idempotent by the flag rather than by catching: [BroadcastNetworkLease.release] is owed on the
     * stop path and on every failed-start path, and those two can arrive for the same session.
     */
    private class HeldNetwork(
        override val address: InetAddress,
        private val onRelease: () -> Unit
    ) : BroadcastNetworkLease {

        private val released = AtomicBoolean(false)

        override fun release() {
            if (released.compareAndSet(false, true)) {
                onRelease()
            }
        }
    }
}
