package com.sza.fastmediasorter.domain.player

import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveLiveBroadcastSourcesUseCase

/** A broadcast row being played: the stored row and, when it plays through an exchange server, its broadcast id. */
class BroadcastPlaybackSession(val source: StreamSourceEntity, val broadcastId: String?) {
    /** S4607, item BJ: the entry whose directory alone may end this channel; null reads the whole account. */
    val entryKey: String? get() = source.exchangeEntryId?.takeIf { it.isNotEmpty() }

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
        if (endedInService?.id == source.id) endedInService = null
        byPlayedUrl.remove(playedUrl)
        byPlayedUrl[playedUrl] = BroadcastPlaybackSession(source, broadcastIdOf(source))
    }

    @Synchronized
    fun forUrl(url: String): BroadcastPlaybackSession? = byPlayedUrl[url]

    @Synchronized
    fun clear(playedUrl: String) {
        byPlayedUrl.remove(playedUrl)
        if (leftToService == playedUrl) leftToService = null
    }

    /**
     * S4609: the played address the Streams screen left to the background audio service. While the screen holds
     * a broadcast it owns its ends and its password question, so the service acts on a broadcast only once the
     * screen let go of it.
     */
    private var leftToService: String? = null

    @Synchronized
    fun markLeftToService(playedUrl: String) {
        leftToService = playedUrl
    }

    @Synchronized
    fun clearLeftToService() {
        leftToService = null
    }

    @Synchronized
    fun isLeftToService(playedUrl: String): Boolean = playedUrl == leftToService && byPlayedUrl[playedUrl] != null

    /**
     * LIVE-BROADCAST item T: the row of a broadcast the background service ended with no screen holding it. Service
     * and Streams screen share this process, so the screen reads it when it opens and shows the ended dialog; it lives
     * only until then, and a new start of the same row drops it, so no dialog lands over a broadcast playing again.
     */
    private var endedInService: StreamSourceEntity? = null

    @Synchronized
    fun markEndedInService(source: StreamSourceEntity) {
        endedInService = source
    }

    @Synchronized
    fun consumeEndedInService(): StreamSourceEntity? = endedInService.also { endedInService = null }

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
