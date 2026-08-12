package com.sza.fastmediasorter.ui.launcher.share

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import com.sza.fastmediasorter.ui.share.UrlInTextDetector

/**
 * S1175: turns the text another map application shares into a geographic cell command.
 *
 * The order is by descending usefulness rather than by convenience: coordinates and free text both
 * resolve to a route from the current position, while a bare short link resolves to nothing a route
 * can start from, so it degrades to showing the place instead of silently pretending to be a route
 * (strategic risk "shared place carries no parsable address").
 *
 * Kept apart from the Activity and the manager because it is the only part of the ingress that can be
 * verified without a device: Maps shares at least four payload shapes and each one is a separate
 * branch here.
 */
object LauncherPlaceShareParser {

    fun parse(sharedText: String?): LauncherCellCommand.Geographic? {
        val shared = sharedText?.trim().orEmpty()
        val urls = UrlInTextDetector.httpUrls(shared)
        val coordinate = COORDINATE.find(shared)?.value
        val placeText = urls.fold(shared) { value, url -> value.replace(url, "") }.trim()
        val url = urls.firstOrNull()
        return when {
            shared.isEmpty() -> null
            coordinate != null -> geographic(LauncherGeographicAction.DIRECTIONS, coordinate, shared)
            placeText.isNotEmpty() -> geographic(LauncherGeographicAction.DIRECTIONS, placeText, placeText)
            url != null -> geographic(LauncherGeographicAction.SHOW_PLACE, url, url)
            else -> null
        }
    }

    private fun geographic(
        action: LauncherGeographicAction,
        query: String,
        labelSource: String,
    ): LauncherCellCommand.Geographic = LauncherCellCommand.Geographic(
        action = action,
        query = query,
        label = labelFrom(labelSource),
    )

    private fun labelFrom(value: String): String = value.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    private val COORDINATE = Regex("-?\\d{1,2}\\.\\d+\\s*,\\s*-?\\d{1,3}\\.\\d+")
}
