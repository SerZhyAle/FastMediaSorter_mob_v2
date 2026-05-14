package com.sza.fastmediasorter.data.link

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0190 — Rewrites well-known equivalent URLs to a canonical form that both
 * yt-dlp and NewPipe's `YoutubeStreamLinkHandlerFactory` recognise reliably.
 *
 * Currently covers YouTube:
 *  - `music.youtube.com/watch?v=X`  → `www.youtube.com/watch?v=X`
 *  - `m.youtube.com/...`            → `www.youtube.com/...`
 *  - `youtube.com/shorts/<id>`      → `www.youtube.com/watch?v=<id>`
 *
 * Rationale:
 *  - NewPipe `getServiceByUrl` rejects bare `music.youtube.com` and `m.youtube.com`
 *    for `LinkType.STREAM` resolution.
 *  - Shorts URLs trigger YouTube's PoToken-strict player flow; normalising to the
 *    regular watch URL falls back to the standard player which works without a
 *    `PoTokenProvider` (S0198 will provide one for the remaining cases).
 *
 * Returns the URL unchanged when no rule applies or when parsing fails.
 */
@Singleton
class LinkUrlCanonicalizer @Inject constructor() {

    fun canonicalize(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val host = parsed.host.lowercase()

        val rewritten = when {
            host == YT_MUSIC_HOST -> rebuildHost(parsed, WWW_YOUTUBE_HOST)
            host == YT_MOBILE_HOST -> rebuildHost(parsed, WWW_YOUTUBE_HOST)
            (host == YOUTUBE_BARE_HOST || host == WWW_YOUTUBE_HOST) && isShortsPath(parsed) ->
                buildShortsWatchUrl(parsed)
            else -> return url
        }

        if (rewritten != url) {
            Timber.d("S0190: canonicalize %s -> %s", url.take(80), rewritten.take(80))
        }
        return rewritten
    }

    private fun isShortsPath(parsed: okhttp3.HttpUrl): Boolean {
        val segments = parsed.pathSegments
        return segments.size >= 2 && segments[0] == "shorts" && segments[1].isNotBlank()
    }

    private fun buildShortsWatchUrl(parsed: okhttp3.HttpUrl): String {
        val videoId = parsed.pathSegments[1]
        val builder = parsed.newBuilder()
            .host(WWW_YOUTUBE_HOST)
            // Replace path segments: shorts/<id> -> watch
            .removePathSegment(1)
            .setPathSegment(0, "watch")
            // Ensure v=<id> is present as the first query parameter.
            .setQueryParameter("v", videoId)
        return builder.build().toString()
    }

    private fun rebuildHost(parsed: okhttp3.HttpUrl, newHost: String): String {
        return parsed.newBuilder().host(newHost).build().toString()
    }

    private companion object {
        const val YT_MUSIC_HOST = "music.youtube.com"
        const val YT_MOBILE_HOST = "m.youtube.com"
        const val YOUTUBE_BARE_HOST = "youtube.com"
        const val WWW_YOUTUBE_HOST = "www.youtube.com"
    }
}
