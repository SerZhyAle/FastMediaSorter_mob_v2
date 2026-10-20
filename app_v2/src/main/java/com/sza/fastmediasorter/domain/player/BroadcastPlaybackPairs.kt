package com.sza.fastmediasorter.domain.player

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * The pair each played address of a protected broadcast answers with (contract LIVE-BROADCAST item M), bound by
 * host and port when the endpoint is resolved and read by the player's data sources, which Hilt does not build.
 * Memory only: the password never travels in a played, logged or shown URL.
 */
object BroadcastPlaybackPairs {

    private val byAuthority = ConcurrentHashMap<String, BroadcastCredentialPair>()

    /** Binds [pair] to [url]'s host and port, or unbinds it when [pair] is null. */
    fun bind(url: String, pair: BroadcastCredentialPair?) {
        val authority = authorityOf(url) ?: return
        if (pair == null) byAuthority.remove(authority) else byAuthority[authority] = pair
    }

    fun forUrl(url: String): BroadcastCredentialPair? = authorityOf(url)?.let(byAuthority::get)

    /**
     * The `Authorization` value answering [challenge] for [url] at [pathAndQuery] - Digest for the phone, Basic for
     * the relay - or null when no pair is bound there or the challenge is one this code cannot answer.
     */
    fun answerFor(url: String, challenge: String?, method: String, pathAndQuery: String): String? {
        val pair = forUrl(url)
        if (pair == null || challenge == null) return null
        return BroadcastDigest.answer(challenge, method, pathAndQuery, pair)
            ?: BroadcastDigest.basic(pair).takeIf { challenge.trimStart().startsWith(SCHEME_BASIC, ignoreCase = true) }
    }

    /**
     * True when a 401 from [url] is a protected broadcast's: a pair is bound there, or the challenge names the
     * broadcast realm. An ordinary camera's 401 keeps its own error.
     */
    fun isProtected(url: String, challenge: String?): Boolean =
        forUrl(url) != null || challenge?.contains("realm=\"${BroadcastDigest.REALM}\"") == true

    /** True when [error], or a cause of it, is a protected broadcast this device holds no working pair for. */
    fun isPasswordRefusal(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(MAX_CAUSES).any {
        it is BroadcastPasswordRequiredException ||
            // Media3 reports a refused RTSP answer only as text; its exception type lives in a flavor source set.
            (it.javaClass.simpleName.contains(RTSP_MARKER) && it.message?.contains(RTSP_UNAUTHORIZED) == true)
    }

    /** [uri] with the bound pair as its userinfo, for the RTSP source only: Media3 reads RTSP credentials there. */
    fun withRtspUserInfo(uri: URI): URI {
        val pair = forUrl(uri.toString()) ?: return uri
        return URI(uri.scheme, "${pair.user}:${pair.password}", uri.host, uri.port, uri.path, uri.query, uri.fragment)
    }

    private fun authorityOf(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull()
        return uri?.host?.lowercase()?.let { host -> "$host:${uri.port}" }
    }

    private const val SCHEME_BASIC = "Basic"
    private const val RTSP_MARKER = "Rtsp"
    private const val RTSP_UNAUTHORIZED = "401"
    private const val MAX_CAUSES = 8
}

/** A protected broadcast refused the stream: no pair was stored for it, or the stored one no longer works. */
class BroadcastPasswordRequiredException(val refused: Boolean) :
    IOException(if (refused) "broadcast refused the stored password" else "broadcast is protected by a password")
