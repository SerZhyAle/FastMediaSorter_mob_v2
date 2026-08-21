package com.sza.fastmediasorter.wear.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Undoes one wide-band request. Separated from the manager so the release path can be driven in a test. */
internal fun interface WideChannelHandle {
    fun release()
}

/** The platform seam: everything in this file that is not testable off-device lives behind it. */
internal fun interface WideChannelRequester {
    fun request(): WideChannelHandle
}

/**
 * Asks for a wide-band transport while a stream plays and gives it back the moment the stream stops.
 *
 * Concurrent holds are counted rather than stacked: two streams share one platform request, and the
 * request is released when the last of them finishes, never when the first does.
 */
@Singleton
class StreamNetworkHoldManager internal constructor(
    private val requester: WideChannelRequester
) : StreamNetworkHold {

    @Inject
    constructor(@ApplicationContext context: Context) : this(ConnectivityWideChannelRequester(context))

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

    /**
     * The count rises only after the request succeeded. Incrementing first would strand the counter
     * above zero when `request()` throws - the caller never reaches the `finally` that would undo it -
     * and every later stream would then skip the request because a hold appeared to be open already.
     */
    private fun acquire() = synchronized(lock) {
        if (holds == 0) {
            handle = requester.request()
            Timber.d("S1728: wide channel requested")
        }
        holds++
    }

    private fun releaseOne() = synchronized(lock) {
        if (--holds == 0) {
            handle?.release()
            Timber.d("S1728: wide channel released")
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
