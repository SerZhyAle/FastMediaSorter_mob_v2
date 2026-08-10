package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.ui.launcher.gadget.di.HomeWidgetGadgets
import com.sza.fastmediasorter.ui.launcher.gadget.di.SensorGadgets
import com.sza.fastmediasorter.ui.launcher.gadget.di.TechnicalGadgets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0404: the set of gadgets the desktop knows, and the codec for a GADGET cell's `target` column.
 *
 * The encoding mirrors [LauncherCellCommand][com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand]'s
 * prefix scheme (`<key>` or `<key>:<param>`) rather than inventing a second one, so both cell kinds
 * stay one TEXT column with no schema change.
 */
@Singleton
class LauncherGadgetRegistry @Inject constructor(
    clock: ClockGadget,
    weather: WeatherGadget,
    playlist: PlaylistGadget,
    streams: StreamsGadget,
    folderPreview: FolderPreviewGadget,
    // S1170: the home-widget counterparts arrive as one qualified collection, not one parameter each -
    // fourteen more constructor arguments would put this class at nineteen, past detekt's threshold of
    // 10 and past the point where the list says anything.
    //
    // @JvmSuppressWildcards is load-bearing, not decoration: Kotlin compiles a `List<LauncherGadget>`
    // parameter to Java `List<? extends LauncherGadget>`, which Dagger treats as a different key from
    // the `List<LauncherGadget>` the module provides - the graph then fails with MissingBinding at
    // hiltJavaCompile, long after `a.ps1 fk` has reported the Kotlin as clean. Same reason
    // ResolvePanelRouteAvailabilityUseCase writes Set<@JvmSuppressWildcards ScreenVideoRecordingController>.
    @HomeWidgetGadgets homeWidgets: List<@JvmSuppressWildcards LauncherGadget>,
    // S1179: the sensor tiles arrive the same way and for the same two reasons - the constructor
    // threshold, and the @JvmSuppressWildcards erasure trap documented above.
    @SensorGadgets sensors: List<@JvmSuppressWildcards LauncherGadget>,
    // S1178: the four technical status tiles, for the same two reasons again - the constructor threshold
    // and the @JvmSuppressWildcards erasure trap documented above.
    @TechnicalGadgets technical: List<@JvmSuppressWildcards LauncherGadget>,
) {

    private val gadgets: List<LauncherGadget> =
        listOf(clock, weather, playlist, streams, folderPreview) + homeWidgets + sensors + technical

    /** Picker order (Phase 07): cheapest and most universal first. */
    fun all(): List<LauncherGadget> = gadgets

    /**
     * S1179: what the picker offers - everything this device can actually show. Deliberately not the
     * same list as [all]: a cell already on the desktop still resolves through [byKey] even if its
     * sensor stops answering, so an existing desktop never loses a tile on upgrade.
     */
    fun available(): List<LauncherGadget> = gadgets.filter { it.isAvailable() }

    fun byKey(key: String): LauncherGadget? = gadgets.firstOrNull { it.key == key }

    fun encodeTarget(key: String, param: String?): String =
        if (param.isNullOrBlank()) key else "$key$SEPARATOR$param"

    /**
     * Splits on the FIRST separator only - a param may legitimately contain one, and the key never
     * does. Returns null for a blank target; an unknown key is the caller's problem to render (the
     * registry does not decide what a broken cell looks like).
     */
    fun decodeTarget(raw: String?): Pair<String, String?>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val separator = value.indexOf(SEPARATOR)
        return when {
            separator < 0 -> value to null
            // A leading separator means an empty key, which no gadget can ever answer to.
            separator == 0 -> null
            else -> value.substring(0, separator) to
                value.substring(separator + 1).takeIf { it.isNotBlank() }
        }
    }

    companion object {
        const val KEY_CLOCK = "clock"
        const val KEY_WEATHER = "weather"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_STREAMS = "streams"
        const val KEY_FOLDER_PREVIEW = "folder_preview"

        // S1179: a key is a storage format written into a cell's `target` column - never renamed.
        const val KEY_COMPASS = "compass"
        const val KEY_SPEED = "speed"
        const val KEY_SPEED_CHART = "speed_chart"
        const val KEY_ALTITUDE_CHART = "altitude_chart"
        const val KEY_STEPS = "steps"

        // S1178: same contract - the key is what a cell's `target` column stores, so it is never renamed.
        const val KEY_NETWORK = "network"
        const val KEY_BATTERY = "battery"
        const val KEY_STORAGE = "storage"
        const val KEY_RESOURCES = "resources"

        private const val SEPARATOR = ':'
    }
}
