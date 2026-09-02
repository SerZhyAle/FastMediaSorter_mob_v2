package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.domain.model.weather.WeatherLocation

/**
 * S2213: decides which place a weather cell is drawn for.
 *
 * A launcher reset clears the desktop and the launcher re-seeds the starter set, which lays a weather
 * cell out again carrying no place of its own - so without a fallback the user's city is gone from a
 * block that visibly came back.
 *
 * The cell's own param stays the source of truth (strategic ADR-2): a desktop carrying several weather
 * cells for different cities keeps every one of them, and only a cell whose param does not decode to a
 * place borrows the one the user picked last. Stated here rather than inline in the render path because
 * that path needs a view hierarchy and a unit test cannot reach it.
 */
object LauncherWeatherParamFallback {

    /**
     * Returns the param the renderer should use for [key], substituting [savedLocation] only where the
     * cell has no readable place of its own. A saved value that does not decode is ignored rather than
     * passed on, so a corrupted preference cannot turn a configured-looking cell into a broken one.
     */
    fun resolve(key: String, param: String?, savedLocation: String?): String? = when {
        key != LauncherGadgetRegistry.KEY_WEATHER -> param
        WeatherLocation.decode(param) != null -> param
        else -> savedLocation?.takeIf { WeatherLocation.decode(it) != null } ?: param
    }
}
