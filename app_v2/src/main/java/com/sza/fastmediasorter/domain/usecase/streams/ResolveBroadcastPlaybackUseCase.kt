package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe.ExchangeAnswer
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastLoopbackBridges
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * S4160: the URL a player opens for a stream row. A row imported from a descriptor carries its endpoint list,
 * tried in the producer's order - LAN, then `RELAY`, then `TUNNEL` - starting at the top on every call, which is
 * what a reconnect must do (`DEVICE-EXCHANGE` 7.7). An exchange endpoint of any server plays with no entry for
 * it: trust is the endpoint's `certFingerprint`, else the platform chain, and checking is never turned off
 * (item AF, item D). A row without a list plays its `url` exactly as before.
 */
class ResolveBroadcastPlaybackUseCase @Inject constructor(
    private val probe: BroadcastEndpointProbe,
    private val bridges: BroadcastLoopbackBridges,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    sealed interface Playback {
        data class Playable(val url: String) : Playback

        /** A server answered with a certificate neither the descriptor's pin nor the platform accepts. */
        data class TrustRefused(val server: String) : Playback

        /** Nothing in the list answered: the broadcast has ended or is out of reach. */
        data object Unreachable : Playback
    }

    /** [url] and [endpoints] are the stored row's `url` and `broadcastEndpoints` columns. */
    suspend operator fun invoke(url: String, endpoints: String?): Playback {
        if (endpoints == null) return Playback.Playable(url)
        Timber.d("S4160: resolving the endpoint list of a broadcast channel")
        return withContext(ioDispatcher) { resolve(url, endpoints) }
    }

    private fun resolve(url: String, endpoints: String): Playback {
        var refusedBy: String? = null
        for (endpoint in BroadcastExchangeEndpoints.playOrder(endpoints, url)) {
            when (val outcome = attempt(endpoint)) {
                is Playback.Playable -> return outcome
                is Playback.TrustRefused -> refusedBy = refusedBy ?: outcome.server
                Playback.Unreachable -> Unit
            }
        }
        return refusedBy?.let(Playback::TrustRefused) ?: Playback.Unreachable
    }

    private fun attempt(endpoint: BroadcastExchangeEndpoint): Playback = when (endpoint) {
        is BroadcastExchangeEndpoint.Lan ->
            if (probe.lanAnswers(endpoint.url)) Playback.Playable(endpoint.url) else Playback.Unreachable
        is BroadcastExchangeEndpoint.Relay -> exchange(endpoint.server, endpoint.certFingerprint) {
            // A player cannot pin, so a pinned relay plays through the loopback proxy; an unpinned one passed the
            // platform chain just now and the player checks that chain itself.
            if (endpoint.certFingerprint == null) {
                endpoint.url
            } else {
                BroadcastExchangeEndpoints.loopbackRelayUrl(endpoint, bridges.relayPort(endpoint))
            }
        }
        is BroadcastExchangeEndpoint.Tunnel -> exchange(endpoint.server, endpoint.certFingerprint) {
            BroadcastExchangeEndpoints.rewriteInner(endpoint.inner, bridges.tunnelPort(endpoint))
        }
    }

    private fun exchange(server: ExchangeServer, pin: String?, playUrl: () -> String?): Playback =
        when (probe.exchangeAnswers(server, pin)) {
            ExchangeAnswer.TRUST_REFUSED -> Playback.TrustRefused(server.label)
            ExchangeAnswer.UNREACHABLE -> Playback.Unreachable
            ExchangeAnswer.TRUSTED -> bridged(server.label, playUrl)
        }

    private fun bridged(server: String, playUrl: () -> String?): Playback = try {
        playUrl()?.let(Playback::Playable) ?: Playback.Unreachable
    } catch (e: IOException) {
        Timber.w("ResolveBroadcastPlaybackUseCase: no loopback bridge for %s - %s", server, e.javaClass.simpleName)
        Playback.Unreachable
    }

    companion object {
        /** The exchange server a stored endpoint list plays through, as `host[:port]`; null for a LAN-only list. */
        fun exchangeServerLabel(endpoints: String?): String? =
            endpoints?.let(BroadcastExchangeEndpoints::exchangeServer)?.label
    }
}
