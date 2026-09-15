package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import timber.log.Timber

/**
 * The Apps screen renders what this catalog returns; it never decides for itself which programs exist.
 *
 * Order is fixed here rather than at the call site so a fourth program stays a one-record change and
 * cannot silently reorder the screen. The unavailable filter runs here for the same reason: a program
 * that later hides itself does so without the screen learning a second concept.
 *
 * S2457 / S2995: [capabilities] provides BUILD-TIME answers from `WearRestrictedCapabilities`, whose
 * implementation is chosen by the flavor source set and cannot change while the app runs.
 *
 * S2751: moved here from the Apps screen's own package. What it returns is domain records, so its place
 * above the layer that consumes them was an accident of the screen having been its first caller; a tile
 * is built outside that screen and had to reach up across the layer arrow to read it.
 */
object WearAppCatalog {

    fun apps(capabilities: WearRestrictedCapabilities): List<WearApp> {
        return listOf(
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
            // S2995: health features (Motion Monitor) withheld from store builds (standard flavor)
            WearApp(
                id = WearAppId.MOTION_MONITOR,
                labelRes = R.string.wear_motion_monitor_app,
                isAvailable = capabilities.offersHealthFeatures
            ),
            // S2457: body sensor diagnostics withheld from store builds
            WearApp(
                id = WearAppId.BODY_SENSOR,
                labelRes = R.string.wear_app_body_sensor,
                isAvailable = capabilities.offersBodySensorDiagnostics
            ),
            // S2995: blood pressure log withheld from store builds (standard flavor)
            WearApp(
                id = WearAppId.BLOOD_PRESSURE,
                labelRes = R.string.wear_app_blood_pressure,
                isAvailable = capabilities.offersHealthFeatures
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
            ),
            // S3042: health features (Tourist step/distance telemetry) withheld from store builds (standard flavor)
            WearApp(
                id = WearAppId.TOURIST,
                labelRes = R.string.wear_tourist_app,
                isAvailable = capabilities.offersHealthFeatures
            )
        ).filter { it.isAvailable }
    }
}
