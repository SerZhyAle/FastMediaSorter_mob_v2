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
 *
 * S2457: [offersBodySensorDiagnostics] is a BUILD-TIME answer, not an observable one - it comes from
 * `WearRestrictedCapabilities`, whose implementation is chosen by the flavor source set and cannot change
 * while the app runs. It is a parameter rather than an injection because this is an `object` and both of
 * its callers are Hilt-constructed, so the answer is cheaper to pass than to reach for.
 */
object WearAppCatalog {

    fun apps(offersBodySensorDiagnostics: Boolean): List<WearApp> = listOf(
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
        ),
        WearApp(
            id = WearAppId.VOICE_RECORDER,
            labelRes = R.string.wear_voice_note_app,
            route = WearRoutes.VOICE_RECORDER
        ),
        WearApp(
            id = WearAppId.SYSTEM_INFO,
            labelRes = R.string.system_info_title,
            route = WearRoutes.SYSTEM_INFO
        ),
        WearApp(
            id = WearAppId.WATER_FLASHLIGHT,
            labelRes = R.string.wear_water_flashlight_app,
            route = WearRoutes.WATER_FLASHLIGHT
        ),
        // S2458: stays listed on every watch. A watch with no step sensor, or an edition without the
        // permission, is explained inside the program - hiding the row would answer a question the user
        // has not asked yet and leave the absence unexplained.
        WearApp(
            id = WearAppId.MOTION_MONITOR,
            labelRes = R.string.wear_motion_monitor_app,
            route = WearRoutes.MOTION_MONITOR
        ),
        // S2457: the one row this list withholds rather than explains. The Motion Monitor above stays
        // listed everywhere because its absence is a property of the WATCH the user can be told about;
        // this one's absence is a property of the BUILD, and a row that opened only to say "not in this
        // edition" would advertise a capability the store listing must not claim (ADR-1).
        WearApp(
            id = WearAppId.BODY_SENSOR,
            labelRes = R.string.wear_app_body_sensor,
            route = WearRoutes.BODY_SENSOR,
            isAvailable = offersBodySensorDiagnostics
        )
    ).filter { it.isAvailable }
}
