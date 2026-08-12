package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.BuildConfig

/** Hilt qualifier value for the tile-only HTTP client, kept apart from the shared one. */
const val MAP_TILE_CLIENT = "mapTiles"

/**
 * S1175: the identifying User-Agent the OSM tile usage policy demands.
 *
 * It names the application and its version rather than a browser string: the policy blocks by agent,
 * and an agent that impersonates a browser is what gets a whole application banned. Applied once, by
 * the interceptor in `MapModule`, so no future call site can forget it.
 */
object TileUserAgent {

    fun value(): String = "FastMediaSorter/${BuildConfig.VERSION_NAME} (Android; sza.od.ua)"
}
