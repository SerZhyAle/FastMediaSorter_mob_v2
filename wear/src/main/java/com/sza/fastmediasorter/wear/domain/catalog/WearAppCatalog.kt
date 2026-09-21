package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId

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
            // S3178: the Network Monitor reads this watch's own radios, which the store artifact
            // declares no permission for - so the row is withheld rather than left to report nothing.
            WearApp(
                id = WearAppId.NETWORK_MONITOR,
                labelRes = R.string.wear_app_network_monitor,
                isAvailable = capabilities.offersDeviceDiagnostics && capabilities.offersNearbyDeviceState
            ),
            WearApp(
                id = WearAppId.GAME,
                labelRes = R.string.wear_app_game
            ),
            // S3178: the recorder opens the microphone through a foreground service the store
            // artifact does not declare, so the platform would refuse to start it.
            WearApp(
                id = WearAppId.VOICE_RECORDER,
                labelRes = R.string.wear_voice_note_app,
                isAvailable = capabilities.offersVoiceRecording
            ),
            // S3178: the watch's own report is device and usage data, which Play counts as personal
            // and sensitive whatever the screen does with it.
            WearApp(
                id = WearAppId.SYSTEM_INFO,
                labelRes = R.string.system_info_title,
                isAvailable = capabilities.offersDeviceDiagnostics
            ),
            // S3362: the screen consumes every pointer event so a wet wrist cannot dismiss it, which
            // is the gesture WO-V3 asks for on almost every screen - so the row leaves the store
            // artifact with the program rather than shipping a screen the review cannot leave.
            WearApp(
                id = WearAppId.WATER_FLASHLIGHT,
                labelRes = R.string.wear_water_flashlight_app,
                isAvailable = capabilities.offersScreenTakeoverPrograms
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
            // Its strategic §3.2 ruling - that the microphone broadcast is not hidden behind
            // WearRestrictedCapabilities - held while both flavors declared RECORD_AUDIO. S3178 moved
            // that declaration to the sideload manifest alone, so the store artifact has no session to
            // start and the row is withheld there rather than offering a refusal.
            WearApp(
                id = WearAppId.BROADCAST,
                labelRes = R.string.wear_broadcast_app,
                isAvailable = capabilities.offersVoiceRecording
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
                // S3178: telemetry as well as health - the dashboard reports the device alongside the
                // activity, so both answers must allow it.
                isAvailable = capabilities.offersHealthFeatures && capabilities.offersDeviceDiagnostics
            ),
            // S3109: appended rather than placed beside a program it resembles - the order of this
            // list is the owner's.
            // S3362: its one action hands the text to the paired phone, so it belongs to the transfer
            // capability. Without a companion the screen can only show an empty clipboard and a
            // disabled button, which is what a Play reviewer would see.
            WearApp(
                id = WearAppId.CLIPBOARD,
                labelRes = R.string.wear_app_clipboard,
                isAvailable = capabilities.offersContentTransfer
            ),
            // S3216: appended rather than placed beside the water flashlight it resembles - the order
            // of this list is the owner's, and moving an existing program is not that ticket's to
            // decide.
            // S3362: the same screen takeover as the water flashlight, plus a full-screen strobe and
            // the alarm stream raised to its maximum, so both leave the store artifact together.
            WearApp(
                id = WearAppId.SOS,
                labelRes = R.string.wear_app_sos,
                isAvailable = capabilities.offersScreenTakeoverPrograms
            )
        ).filter { it.isAvailable }
    }
}
