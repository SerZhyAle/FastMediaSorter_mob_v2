package com.sza.fastmediasorter.wear.ui.apps

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

/**
 * The Apps screen renders what this catalog returns; it never decides for itself which programs exist.
 *
 * Order is fixed here rather than at the call site so a fourth program stays a one-record change and
 * cannot silently reorder the screen. The unavailable filter runs here for the same reason: a program
 * that later hides itself does so without the screen learning a second concept.
 */
object WearAppCatalog {

    fun apps(): List<WearApp> = listOf(
        WearApp(
            id = WearAppId.CALCULATOR,
            labelRes = R.string.wear_app_calculator,
            route = WearRoutes.CALCULATOR
        ),
        WearApp(
            id = WearAppId.NETWORK_MONITOR,
            labelRes = R.string.wear_app_network_monitor,
            route = WearRoutes.NETWORK_MONITOR
        ),
        WearApp(
            id = WearAppId.GAME,
            labelRes = R.string.wear_app_game,
            route = WearRoutes.GAME
        )
    ).filter { it.isAvailable }
}
