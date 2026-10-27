package com.sza.fastmediasorter.domain.player

import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveLiveBroadcastSourcesUseCase

/** A broadcast row being played: the stored row and, when it plays through an exchange server, its broadcast id. */
class BroadcastPlaybackSession(val source: StreamSourceEntity, val broadcastId: String?) {
    // The row carries its endpoint list, and a sealed endpoint carries the key that opens the broadcast.
    override fun toString(): String = "BroadcastPlaybackSession(id=${source.id})"
}

/**
 * The broadcast row behind each played address (LIVE-BROADCAST item T). The player is handed only the address the
 * endpoint resolution answered with - often a loopback bridge - so it learns here that it plays a broadcast and
 * which row to resolve again from the top. Memory only, like [BroadcastPlaybackPairs]: the endpoint list never
 * travels in an Intent.
 */
object BroadcastPlaybackSessions {

    private const val MAX_SESSIONS = 8

    private val byPlayedUrl = object : LinkedHashMap<String, BroadcastPlaybackSession>(MAX_SESSIONS, LOAD_FACTOR) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BroadcastPlaybackSession>) =
            size > MAX_SESSIONS
    }

    /** Records that [playedUrl] plays the stored row [source]. */
    @Synchronized
    fun register(playedUrl: String, source: StreamSourceEntity) {
        byPlayedUrl.remove(playedUrl)
        byPlayedUrl[playedUrl] = BroadcastPlaybackSession(source, broadcastIdOf(source))
    }

    @Synchronized
    fun forUrl(url: String): BroadcastPlaybackSession? = byPlayedUrl[url]

    @Synchronized
    fun clear(playedUrl: String) {
        byPlayedUrl.remove(playedUrl)
    }

    private fun broadcastIdOf(source: StreamSourceEntity): String? =
        BroadcastExchangeEndpoints.playOrder(source.broadcastEndpoints, source.url).firstNotNullOfOrNull {
            when (it) {
                is BroadcastExchangeEndpoint.Relay -> it.broadcastId
                is BroadcastExchangeEndpoint.Tunnel -> it.broadcastId
                is BroadcastExchangeEndpoint.Lan -> null
            }
        } ?: source.id.takeIf { it.startsWith(ObserveLiveBroadcastSourcesUseCase.ID_PREFIX) }
            ?.removePrefix(ObserveLiveBroadcastSourcesUseCase.ID_PREFIX)
            ?.takeIf { it.isNotEmpty() }
}

private const val LOAD_FACTOR = 0.75f
