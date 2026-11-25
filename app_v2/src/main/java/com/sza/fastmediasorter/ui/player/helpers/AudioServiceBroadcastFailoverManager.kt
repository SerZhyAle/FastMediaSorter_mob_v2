package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4609: what the background audio service does for a broadcast the Streams screen has left to it. A reconnect and
 * a change of the default network both resolve the row's endpoint list again from the top (`DEVICE-EXCHANGE` 7.7),
 * so a link lost to a changed network moves to the relay or the tunnel instead of retrying a dead LAN address.
 * Owned by one service instance and driven from its main thread.
 */
class AudioServiceBroadcastFailoverManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolvePlayback: ResolveBroadcastPlaybackUseCase,
) {

    /** The answer of one resolution, for the played address it was asked about. */
    sealed interface Outcome {
        data class Playable(val url: String) : Outcome

        data class Refused(val channelTitle: String) : Outcome
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resolution: Job? = null
    private var swapUrl: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun isBroadcast(playedUrl: String?): Boolean =
        playedUrl != null && BroadcastPlaybackSessions.forUrl(playedUrl) != null

    fun isLeftToService(playedUrl: String?): Boolean =
        playedUrl != null && BroadcastPlaybackSessions.isLeftToService(playedUrl)

    fun channelTitleOf(playedUrl: String?): String? =
        playedUrl?.let { BroadcastPlaybackSessions.forUrl(it)?.source?.title }

    /**
     * Resolves the broadcast behind [playedUrl] from the top in [scope]; a resolution still in flight is dropped,
     * because only the newest network or reconnect answers what plays now.
     */
    fun reresolve(scope: CoroutineScope, playedUrl: String, onOutcome: (Outcome) -> Unit) {
        val source = BroadcastPlaybackSessions.forUrl(playedUrl)?.source ?: return
        resolution?.cancel()
        resolution = scope.launch {
            val outcome = when (val playback = resolvePlayback(source.url, source.broadcastEndpoints, source.id)) {
                is Playback.Playable -> {
                    BroadcastPlaybackSessions.register(playback.url, source)
                    BroadcastPlaybackSessions.markLeftToService(playback.url)
                    if (playback.url != playedUrl) swapUrl = playback.url
                    Outcome.Playable(playback.url)
                }
                else -> {
                    Timber.i("AudioServiceBroadcastFailoverManager: broadcast not playable - %s", playback)
                    Outcome.Refused(source.title)
                }
            }
            onOutcome(outcome)
        }
    }

    /** True once for the item this manager itself put in place, so the service keeps its reconnect ladder. */
    fun consumeSwap(playedUrl: String?): Boolean {
        val own = playedUrl != null && playedUrl == swapUrl
        if (own) swapUrl = null
        return own
    }

    /** Closes the loopback bridge a broadcast [playedUrl] plays through; any other address is left alone. */
    fun closeBridgeOf(playedUrl: String?) {
        if (playedUrl == null || !isBroadcast(playedUrl)) return
        resolvePlayback.closeBridgeOf(playedUrl)
    }

    /**
     * Calls [onChanged] on the main thread each time the default network becomes a different one. The first
     * network is the one already playing and is not a change. API 23 has no default-network callback, so the
     * `legacy` flavor relies on the reconnect alone.
     */
    fun startNetworkWatch(onChanged: () -> Unit) {
        if (networkCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onAvailable(network: Network) {
                mainHandler.post {
                    if (networkCallback !== this) return@post
                    val previous = current
                    current = network
                    if (previous != null && previous != network) onChanged()
                }
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    fun stopNetworkWatch() {
        val callback = networkCallback ?: return
        networkCallback = null
        context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
    }

    /** The user stopped: an answer landing later must not bring the sound back. */
    fun cancelResolution() {
        resolution?.cancel()
        resolution = null
    }

    fun release() {
        stopNetworkWatch()
        cancelResolution()
        swapUrl = null
    }
}
