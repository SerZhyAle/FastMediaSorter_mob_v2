package com.sza.fastmediasorter.data.youtube

import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.model.youtube.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2032: the keyless YouTube lookup, strategic §6.5.
 *
 * Two public sources, neither needing a key nor a registration - the same constraint that chose the
 * keyless weather provider: the channel page carries its own canonical ID, and the channel's public
 * feed carries its title and its latest upload. Strategic ADR-4 rules out stream extraction, so nothing
 * here reads a media URL.
 *
 * Failures never escape: an unreachable host, a refusal and a malformed body all answer null, because
 * the cell shows one message for all three (strategic §11 criterion 7).
 */
@Singleton
class YouTubeFeedChannelProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * A bare channel ID skips the page fetch; anything else is fetched as a page and read for its
     * canonical ID. The feed is then fetched in both cases, which is what makes an unresolvable channel
     * answer null instead of placing a cell that can never fill (strategic §6.1).
     */
    suspend fun resolveChannel(query: String): YouTubeChannel? = withContext(Dispatchers.IO) {
        val channelId = channelIdFor(query) ?: return@withContext null
        val feed = fetch(feedUrl(channelId), "channel feed") ?: return@withContext null
        val title = parseFeedChannelTitle(feed) ?: return@withContext null
        YouTubeChannel(channelId = channelId, title = title)
    }

    suspend fun latestVideo(channelId: String): YouTubeVideo? = withContext(Dispatchers.IO) {
        val feed = fetch(feedUrl(channelId), "channel feed") ?: return@withContext null
        parseFeedLatestVideo(feed)
    }

    private fun channelIdFor(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return directChannelId(trimmed)
            ?: fetch(channelPageUrl(trimmed), "channel page")?.let(::parseChannelId)
    }

    /** Returns the response body, or null when the host is unreachable or refuses the request. */
    private fun fetch(url: String, what: String): String? = try {
        okHttpClient.newCall(Request.Builder().url(url).header(HEADER_ACCEPT_LANGUAGE, ACCEPT_LANGUAGE).build())
            .execute()
            .use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.takeIf { it.isNotBlank() }
                } else {
                    Timber.i("YouTube %s unavailable (HTTP %d)", what, response.code)
                    null
                }
            }
    } catch (e: IOException) {
        Timber.i(e, "YouTube %s unreachable", what)
        null
    }

    private companion object {
        const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"

        /** English forces one page shape; the title displayed comes from the feed, not from this page. */
        const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
    }
}

// -- Pure helpers, top-level so a test reaches them with a fixture string and no network. --

internal const val YOUTUBE_FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id="
internal const val YOUTUBE_CHANNEL_URL = "https://www.youtube.com/"
internal const val YOUTUBE_CHANNEL_ID_PREFIX = "UC"
internal const val YOUTUBE_CHANNEL_ID_LENGTH = 24

internal fun feedUrl(channelId: String): String = YOUTUBE_FEED_URL + channelId

/**
 * A typed address is reduced to its last path segment, so `youtube.com/@name`, `@name` and a full URL
 * with query parameters all reach the same page.
 */
internal fun channelPageUrl(query: String): String {
    val stripped = query.substringBefore('?').substringBefore('#').trimEnd('/')
    val tail = stripped.substringAfterLast('/')
    return YOUTUBE_CHANNEL_URL + tail
}

/** A query that already IS a channel ID, which costs no request at all. */
internal fun directChannelId(query: String): String? {
    val tail = query.substringBefore('?').trimEnd('/').substringAfterLast('/')
    val looksLikeId = tail.startsWith(YOUTUBE_CHANNEL_ID_PREFIX) && tail.length == YOUTUBE_CHANNEL_ID_LENGTH
    return tail.takeIf { looksLikeId }
}

/**
 * The canonical ID out of a channel page.
 *
 * Two spellings are read rather than one: the page embeds it as `"channelId"` in its initial data and
 * as `"externalId"` in its metadata block, and which of them appears first has changed between page
 * revisions - reading only one turns a working channel into "not found" on the next revision.
 */
internal fun parseChannelId(html: String): String? =
    firstQuotedValue(html, "\"channelId\":\"") ?: firstQuotedValue(html, "\"externalId\":\"")

/** The feed's own `<title>`, which sits before the first `<entry>` and names the channel. */
internal fun parseFeedChannelTitle(xml: String): String? {
    val head = xml.substringBefore("<entry")
    return tagText(head, "title")
}

internal fun parseFeedLatestVideo(xml: String): YouTubeVideo? {
    val entryStart = xml.indexOf("<entry")
    if (entryStart < 0) return null
    val entry = xml.substring(entryStart).substringBefore("</entry>")
    return tagText(entry, "yt:videoId")?.let { videoId ->
        YouTubeVideo(
            videoId = videoId,
            title = tagText(entry, "title").orEmpty(),
            thumbnailUrl = attributeValue(entry, "<media:thumbnail", "url") ?: defaultThumbnail(videoId),
        )
    }
}

/** The cover the feed always has for a video, used when the entry carries no explicit thumbnail. */
internal fun defaultThumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

private fun firstQuotedValue(source: String, marker: String): String? {
    val start = source.indexOf(marker)
    if (start < 0) return null
    val value = source.substring(start + marker.length).substringBefore('"')
    return value.takeIf { it.isNotBlank() }
}

private fun tagText(source: String, tag: String): String? {
    val open = source.indexOf("<$tag")
    val textStart = if (open < 0) -1 else source.indexOf('>', open)
    if (textStart < 0) return null
    val text = source.substring(textStart + 1).substringBefore("</$tag>")
    return text.trim().unescapeXml().takeIf { it.isNotBlank() }
}

private fun attributeValue(source: String, tag: String, attribute: String): String? {
    val open = source.indexOf(tag)
    if (open < 0) return null
    val element = source.substring(open).substringBefore('>')
    return firstQuotedValue(element, "$attribute=\"")
}

/** Only the five predefined entities: a feed title carries no others, and a full parser is not owed here. */
private fun String.unescapeXml(): String = replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
