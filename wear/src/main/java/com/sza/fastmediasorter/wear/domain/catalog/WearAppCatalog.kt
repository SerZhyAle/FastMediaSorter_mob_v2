package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId

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
 *
 * S2751: moved here from the Apps screen's own package. What it returns is domain records, so its place
 * above the layer that consumes them was an accident of the screen having been its first caller; a tile
 * is built outside that screen and had to reach up across the layer arrow to read it.
 */
object WearAppCatalog {

    fun apps(offersBodySensorDiagnostics: Boolean): List<WearApp> = listOf(
        WearApp(
            id = WearAppId.CALCULATOR,
            labelRes = R.string.wear_app_calculator
        ),
        WearApp(
            id = WearAppId.NETWORK_MONITOR,
            labelRes = R.string.wear_app_network_monitor
        ),
        WearApp(
            id = WearAppId.GAME,
            labelRes = R.string.wear_app_game
        ),
        WearApp(
            id = WearAppId.VOICE_RECORDER,
            labelRes = R.string.wear_voice_note_app
        ),
        WearApp(
            id = WearAppId.SYSTEM_INFO,
            labelRes = R.string.system_info_title
        ),
        WearApp(
            id = WearAppId.WATER_FLASHLIGHT,
            labelRes = R.string.wear_water_flashlight_app
        ),
        // S2458: stays listed on every watch. A watch with no step sensor, or an edition without the
        // permission, is explained inside the program - hiding the row would answer a question the user
        // has not asked yet and leave the absence unexplained.
        WearApp(
            id = WearAppId.MOTION_MONITOR,
            labelRes = R.string.wear_motion_monitor_app
        ),
        // S2457: the one row this list withholds rather than explains. The Motion Monitor above stays
        // listed everywhere because its absence is a property of the WATCH the user can be told about;
        // this one's absence is a property of the BUILD, and a row that opened only to say "not in this
        // edition" would advertise a capability the store listing must not claim (ADR-1).
        WearApp(
            id = WearAppId.BODY_SENSOR,
            labelRes = R.string.wear_app_body_sensor,
            isAvailable = offersBodySensorDiagnostics
        ),
        // S2809: available in both flavors - manual entry needs no permission or Health Services.
        WearApp(
            id = WearAppId.BLOOD_PRESSURE,
            labelRes = R.string.wear_app_blood_pressure
        ),
        // S2509: the second of the two equal entrances the owner chose; the first is the Home section.
        // Listed in both flavors - strategic §3.2 rules that the microphone broadcast is not hidden
        // behind WearRestrictedCapabilities, unlike the row above it.
        WearApp(
            id = WearAppId.BROADCAST,
            labelRes = R.string.wear_broadcast_app
        ),
        // S2825: listed in both flavors - the stopwatch needs no permission and no hardware, so there
        // is nothing here for a store review to withhold.
        WearApp(
            id = WearAppId.STOPWATCH,
            labelRes = R.string.wear_app_stopwatch
        )
    ).filter { it.isAvailable }
}
