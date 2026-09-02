package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.AltitudeGadget
import com.sza.fastmediasorter.ui.launcher.gadget.CompassGadget
import com.sza.fastmediasorter.ui.launcher.gadget.GoogleCalendarLiveFrameGadget
import com.sza.fastmediasorter.ui.launcher.gadget.GoogleKeepLiveFrameGadget
import com.sza.fastmediasorter.ui.launcher.gadget.GoogleMapsLiveFrameGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.MapGadget
import com.sza.fastmediasorter.ui.launcher.gadget.SatellitesGadget
import com.sza.fastmediasorter.ui.launcher.gadget.SpeedGadget
import com.sza.fastmediasorter.ui.launcher.gadget.StepsGadget
import javax.inject.Inject

/**
 * Bundles basic sensor gadgets so [SensorGadgetModule.provideSensorGadgets] does not exceed
 * detekt's parameter count limit.
 */
class BasicSensorGadgets @Inject constructor(
    val compass: CompassGadget,
    val speed: SpeedGadget,
    val altitude: AltitudeGadget,
    val satellites: SatellitesGadget,
    val steps: StepsGadget,
    val map: MapGadget,
    val googleMapsLive: GoogleMapsLiveFrameGadget,
    val googleKeepLive: GoogleKeepLiveFrameGadget,
    val googleCalendarLive: GoogleCalendarLiveFrameGadget,
) {
    fun toList(): List<LauncherGadget> = listOf(
        altitude,
        speed,
        steps,
        compass,
        satellites,
        map,
        googleMapsLive,
        googleKeepLive,
        googleCalendarLive,
    )
}
