package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * Asks Instagram's web endpoints for the media of one `/p/` post, because a signed-in post page
 * carries no media of its own. The media-info call is tried first and the GraphQL post query
 * second: Instagram can answer one with a login page while still serving the other.
 *
 * A trace that ends on `/accounts/login/` for both calls, with a session in the jar, means the
 * stored session itself is not accepted.
 */
internal class InstagramPostMediaFetcher(
    private val httpClient: OkHttpClient,
    private val sniffer: StructuredMediaSniffer,
) {

    suspend fun fetch(pageUrl: String): List<HtmlMediaCandidate> {
        val shortcode = extractShortcode(pageUrl) ?: return emptyList()
        return requestJson(mediaInfoUrl(shortcode), pageUrl, LABEL_MEDIA_INFO)
            ?.let { sniffer.sniffInstagramApiResponse(it, pageUrl) }
            .orEmpty()
            .ifEmpty {
                requestJson(graphqlUrl(shortcode), pageUrl, LABEL_GRAPHQL)
                    ?.let { sniffer.sniffInstagramGraphqlResponse(it, pageUrl) }
                    .orEmpty()
            }
    }

    private suspend fun requestJson(url: HttpUrl, pageUrl: String, label: String): String? = try {
        withContext(Dispatchers.IO) {
            // The web session is honoured only for an XHR-shaped call on the web origin:
            // a bare GET is treated as signed-out and redirected to /accounts/login/.
            val csrfToken = httpClient.cookieJar.loadForRequest(url)
                .firstOrNull { it.name == CSRF_COOKIE }?.value
            val hasCsrf = !csrfToken.isNullOrBlank()
            Timber.d("S4515: instagram media request label=$label csrf=$hasCsrf")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .header("Origin", WEB_ORIGIN)
                .header("Referer", pageUrl)
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .header("x-asbd-id", ASBD_ID)
                .header("x-ig-app-id", APP_ID)
                .header("x-ig-www-claim", "0")
                .header("x-requested-with", "XMLHttpRequest")
                .apply { if (hasCsrf) header("x-csrftoken", csrfToken.orEmpty()) }
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val isJsonAnswer = response.isSuccessful && body.trimStart().startsWith("{")
                if (!isJsonAnswer) {
                    LinkDownloadTrace.verbose(
                        "$label refused status=${response.code}" +
                            " finalPath=${response.request.url.encodedPath}" +
                            " type=${response.header("Content-Type")}" +
                            " csrf=$hasCsrf body=${bodyPrefix(body)}",
                    )
                }
                body.takeIf { isJsonAnswer }
            }
        }
    } catch (io: IOException) {
        Timber.w(io, "%s fetch failed for %s", label, pageUrl)
        null
    }

    private fun bodyPrefix(body: String): String =
        body.take(BODY_PREFIX_CHARS).replace(WHITESPACE, " ")

    private fun extractShortcode(url: String): String? =
        SHORTCODE_IN_URL.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    private fun mediaInfoUrl(shortcode: String): HttpUrl =
        "$WEB_ORIGIN/api/v1/media/${shortcodeToMediaId(shortcode)}/info/".toHttpUrl()

    private fun graphqlUrl(shortcode: String): HttpUrl =
        "$WEB_ORIGIN/graphql/query/".toHttpUrl().newBuilder()
            .addQueryParameter("doc_id", POST_QUERY_DOC_ID)
            .addQueryParameter(
                "variables",
                """{"shortcode":"$shortcode","fetch_tagged_user_count":null,""" +
                    """"hoisted_comment_id":null,"hoisted_reply_id":null}""",
            )
            .build()

    // Unknown characters contribute 0 so malformed input does not crash.
    private fun shortcodeToMediaId(shortcode: String): Long =
        shortcode.fold(0L) { id, ch ->
            id * SHORTCODE_ALPHABET.length + SHORTCODE_ALPHABET.indexOf(ch).coerceAtLeast(0)
        }

    private companion object {
        const val WEB_ORIGIN = "https://www.instagram.com"
        const val CSRF_COOKIE = "csrftoken"

        // The media-info endpoint answers 401 without the web app id, even with valid session cookies.
        const val APP_ID = "936619743392459"
        const val ASBD_ID = "129477"

        // Instagram's persisted web query for one post; it has no public contract and may be rotated.
        const val POST_QUERY_DOC_ID = "8845758582119845"
        const val SHORTCODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        const val LABEL_MEDIA_INFO = "ig-api"
        const val LABEL_GRAPHQL = "ig-graphql"
        const val BODY_PREFIX_CHARS = 120

        val SHORTCODE_IN_URL = Regex("/p/([A-Za-z0-9_-]+)")
        val WHITESPACE = Regex("\\s+")
    }
}
