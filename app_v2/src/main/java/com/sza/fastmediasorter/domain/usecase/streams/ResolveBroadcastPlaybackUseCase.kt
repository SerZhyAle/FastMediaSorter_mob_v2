package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe.ExchangeAnswer
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastLoopbackBridges
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeServer
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackPairs
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
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
    private val passwords: BroadcastPasswordStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    sealed interface Playback {
        data class Playable(val url: String) : Playback

        /** A server answered with a certificate neither the descriptor's pin nor the platform accepts. */
        data class TrustRefused(val server: String) : Playback

        /** Nothing in the list answered: the broadcast has ended or is out of reach. */
        data object Unreachable : Playback
    }

    /**
     * [url] and [endpoints] are the stored row's `url` and `broadcastEndpoints` columns; [streamId] names the row
     * whose stored broadcast pair the played address answers a protected broadcast's challenge with (item M).
     */
    suspend operator fun invoke(url: String, endpoints: String?, streamId: String? = null): Playback {
        Timber.d("S4258: resolve broadcast playback, stored pair=%b", streamId != null)
        if (endpoints == null) return Playback.Playable(url)
        val pair = streamId?.let { passwords.get(it) }
            ?.let { stored -> stored.password?.let { BroadcastCredentialPair(stored.user, it) } }
        return withContext(ioDispatcher) { resolve(url, endpoints, pair) }
    }

    private fun resolve(url: String, endpoints: String, pair: BroadcastCredentialPair?): Playback {
        var refusedBy: String? = null
        for (endpoint in BroadcastExchangeEndpoints.playOrder(endpoints, url)) {
            // The relay checks the pair on its own address, whichever proxy the player reaches it through.
            if (endpoint is BroadcastExchangeEndpoint.Relay) BroadcastPlaybackPairs.bind(endpoint.url, pair)
            when (val outcome = attempt(endpoint)) {
                is Playback.Playable -> return outcome.also { BroadcastPlaybackPairs.bind(it.url, pair) }
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
            relayUrl(endpoint)
        }
        is BroadcastExchangeEndpoint.Tunnel -> exchange(endpoint.server, endpoint.certFingerprint) {
            BroadcastExchangeEndpoints.rewriteInner(endpoint.inner, bridges.tunnelPort(endpoint))
        }
    }

    // A sealed relay is no stream for any player, so it always plays through the opening proxy (27 item DL). A player
    // cannot pin either, so a pinned clear relay plays through the pinning proxy; an unpinned one passed the
    // platform chain just now and the player checks that chain itself.
    private fun relayUrl(endpoint: BroadcastExchangeEndpoint.Relay): String? {
        val groupKey = endpoint.groupKey?.let(E2eBytes::fromB64url)
        return when {
            groupKey != null ->
                BroadcastExchangeEndpoints.loopbackRelayUrl(endpoint, bridges.sealedRelayPort(endpoint, groupKey))
            endpoint.certFingerprint == null -> endpoint.url
            else -> BroadcastExchangeEndpoints.loopbackRelayUrl(endpoint, bridges.relayPort(endpoint))
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
