package com.sza.fastmediasorter.ui.launcher.share

import com.sza.fastmediasorter.ui.share.UrlInTextDetector

/**
 * S1585: reports the shape of the text another map application shares.
 *
 * The order is by descending precision. An explicit coordinate pair names the point outright. A Maps
 * link names it indirectly but unambiguously, so it outranks the caption that travels with it: the
 * caption is a human label ("Lviv Opera", or a whole postal address across two lines), and handing
 * that to Maps as a destination is what made the shortcut open a search instead of a route (S1175
 * assumed the opposite order, strategic 1).
 *
 * Resolving a link to coordinates needs the network and belongs to [LauncherPlaceShareManager]; this
 * object stays free of it so it remains the one part of the ingress verifiable without a device.
 */
object LauncherPlaceShareParser {

    /** What a shared payload turned out to be. */
    sealed interface Shape {

        /** The payload named the point outright; [query] is a coordinate pair. */
        data class Coordinates(val query: String, val label: String) : Shape

        /** The payload carried a Maps link that has to be resolved before a route exists. */
        data class MapsLink(val link: String, val label: String) : Shape

        /** The payload carried only free text; it is the best destination available. */
        data class PlaceText(val query: String, val label: String) : Shape
    }

    fun parse(sharedText: String?): Shape? {
        val shared = sharedText?.trim().orEmpty()
        val urls = UrlInTextDetector.httpUrls(shared)
        val coordinate = COORDINATE.find(shared)?.value
        val placeText = urls.fold(shared) { value, url -> value.replace(url, "") }.trim()
        val mapsLink = urls.firstOrNull(::isMapsLink)
        val label = labelFrom(placeText.ifEmpty { shared })
        return when {
            shared.isEmpty() -> null
            coordinate != null -> Shape.Coordinates(query = coordinate, label = label)
            mapsLink != null -> Shape.MapsLink(link = mapsLink, label = label)
            placeText.isNotEmpty() -> Shape.PlaceText(query = placeText, label = label)
            else -> urls.firstOrNull()?.let { Shape.PlaceText(query = it, label = it) }
        }
    }

    private fun isMapsLink(url: String): Boolean {
        val lowercase = url.lowercase()
        return MAPS_LINK_MARKERS.any { lowercase.contains(it) }
    }

    private fun labelFrom(value: String): String = value.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    private val COORDINATE = Regex("-?\\d{1,2}\\.\\d+\\s*,\\s*-?\\d{1,3}\\.\\d+")

    private val MAPS_LINK_MARKERS = listOf("maps.app.goo.gl", "goo.gl/maps", "google.com/maps")
}
