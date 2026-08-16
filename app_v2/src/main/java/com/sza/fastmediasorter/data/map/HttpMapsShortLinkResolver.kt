package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.core.network.applyTimeouts
import com.sza.fastmediasorter.domain.map.MapPoint
import com.sza.fastmediasorter.domain.map.MapsShortLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1585: resolves a Google Maps short link by reading the redirect it points at.
 *
 * The short link itself carries no coordinates - only the location it redirects to does, in its path
 * (`/maps/place/<lat>,<lon>/data=..`). Redirects are followed manually rather than by the connection
 * so the destination is read from the `Location` header without fetching the (large) map page body.
 *
 * Every failure returns null, including a timeout: the caller treats an unknown point as a reason to
 * degrade to showing the place, not as an error to report (strategic 6.1).
 */
@Singleton
class HttpMapsShortLinkResolver @Inject constructor() : MapsShortLinkResolver {

    override suspend fun resolve(link: String): MapPoint? {
        if (!isMapsShortLink(link)) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(RESOLVE_BUDGET_MS) { followRedirect(link)?.let(::pointFrom) }
        }
    }

    /**
     * Restricted to the known short-link hosts: a share can carry any URL at all, and resolution must
     * not turn the intake into a fetcher for arbitrary addresses.
     */
    private fun isMapsShortLink(link: String): Boolean {
        val host = runCatching { URL(link).host }.getOrNull().orEmpty().lowercase()
        return SHORT_LINK_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun followRedirect(link: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(link).openConnection() as HttpURLConnection).apply {
                requestMethod = HEAD_METHOD
                instanceFollowRedirects = false
                applyTimeouts()
            }
            connection.getHeaderField(LOCATION_HEADER)
        } catch (e: IOException) {
            Timber.i(e, "Launcher: could not resolve a shared map link")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Visible for tests: the only part of resolution verifiable without a network. */
    internal companion object {
        const val RESOLVE_BUDGET_MS = 5_000L
        const val HEAD_METHOD = "HEAD"
        const val LOCATION_HEADER = "Location"
        val SHORT_LINK_HOSTS = setOf("maps.app.goo.gl", "goo.gl")

        /**
         * Matches the first `<lat>,<lon>` pair anywhere in the resolved URL. Kept tolerant of the
         * surrounding shape because only the pair itself is contractual: Maps appends opaque `data=`
         * and tracking segments that change without notice (strategic 7, "format changes").
         */
        private val COORDINATE = Regex("(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")

        private const val LAT_GROUP = 1
        private const val LON_GROUP = 2
        private const val MAX_LATITUDE = 90.0
        private const val MAX_LONGITUDE = 180.0

        internal fun pointFrom(resolvedUrl: String): MapPoint? {
            val match = COORDINATE.find(resolvedUrl) ?: return null
            val latitude = match.groupValues[LAT_GROUP].toDoubleOrNull()
                ?.takeIf { it in -MAX_LATITUDE..MAX_LATITUDE }
            val longitude = match.groupValues[LON_GROUP].toDoubleOrNull()
                ?.takeIf { it in -MAX_LONGITUDE..MAX_LONGITUDE }
            return if (latitude == null || longitude == null) {
                null
            } else {
                MapPoint(latitude = latitude, longitude = longitude)
            }
        }
    }
}
