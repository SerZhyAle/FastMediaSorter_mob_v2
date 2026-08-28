package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.ui.launcher.gadget.di.AggregatedGadgets
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
    // S1566: the ninth parameter of ten. The next gadget joins a qualified list module instead of the
    // constructor - one more direct parameter here trips detekt's constructorThreshold.
    search: SearchGadget,
    // S1177: every family that arrives as a qualified collection - home widgets, sensor tiles, technical
    // tiles, text cells - now arrives joined, from AggregatedGadgetModule.
    //
    // Four tickets in a row wrote a comment here saying the next gadget must come as a qualified list
    // rather than a parameter, and every one of those lists was itself a parameter: the tenth tripped
    // detekt's LongParameterList regardless. A fifth family now costs a line in that module and nothing
    // here.
    //
    // @JvmSuppressWildcards is load-bearing, not decoration: Kotlin compiles a `List<LauncherGadget>`
    // parameter to Java `List<? extends LauncherGadget>`, which Dagger treats as a different key from
    // the `List<LauncherGadget>` the module provides - the graph then fails with MissingBinding at
    // hiltJavaCompile, long after `a.ps1 fk` has reported the Kotlin as clean. Same reason
    // ResolvePanelRouteAvailabilityUseCase writes Set<@JvmSuppressWildcards ScreenVideoRecordingController>.
    @AggregatedGadgets aggregated: List<@JvmSuppressWildcards LauncherGadget>,
) {

    private val gadgets: List<LauncherGadget> =
        listOf(clock, weather, playlist, streams, folderPreview, search) + aggregated

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
        const val KEY_AUDIO_NOW_PLAYING = "audio_now_playing"

        // S1179: a key is a storage format written into a cell's `target` column - never renamed.
        const val KEY_COMPASS = "compass"
        const val KEY_SPEED = "speed"
        const val KEY_SPEED_CHART = "speed_chart"
        const val KEY_ALTITUDE_CHART = "altitude_chart"
        const val KEY_STEPS = "steps"

        // S1560: same contract again - a key is the stored `target`, so it is never renamed.
        const val KEY_ALTITUDE = "altitude"
        const val KEY_SATELLITES = "satellites"

        // S1175: same contract - the key is what a cell's `target` column stores, so it is never renamed.
        const val KEY_MAP = "map"

        // S1177: same contract once more - this key is the stored `target` of a translator cell.
        const val KEY_TRANSLATOR = "translator"

        // S1178: same contract - the key is what a cell's `target` column stores, so it is never renamed.
        const val KEY_NETWORK = "network"
        const val KEY_BATTERY = "battery"
        const val KEY_STORAGE = "storage"
        const val KEY_RESOURCES = "resources"

        // S1566: same contract - the key is what a cell's `target` column stores, so it is never renamed.
        const val KEY_SEARCH = "search"

        // S1440: same contract - the key is what a cell's `target` column stores, so it is never
        // renamed. Deliberately not "network": [KEY_NETWORK] above is S1178's fixed technical tile,
        // and sharing one key would silently retarget every placed cell of either kind.
        const val KEY_NETWORK_INDICATOR = "network_indicator"

        // S1755: YouTube and YouTube Music app gadgets.
        const val KEY_YOUTUBE = "youtube"
        const val KEY_YOUTUBE_MUSIC = "youtube_music"

        // S1754: the media window family - one resource, played or read inside its own desktop cell.
        // Same contract as every key above: this is what a cell's `target` column stores, so it is
        // never renamed.
        const val KEY_MEDIA_AUDIO_WINDOW = "media_audio_window"
        const val KEY_MEDIA_VIDEO_WINDOW = "media_video_window"
        const val KEY_MEDIA_DOCUMENT_WINDOW = "media_document_window"
        const val KEY_MEDIA_IMAGE_WINDOW = "media_image_window"

        // S2031: one channel of the stream catalog, played inside its own cell. Same contract as every
        // key above - this is what a cell's `target` column stores, so it is never renamed.
        const val KEY_STREAM_WINDOW = "stream_window"

        // S1906: one remote time zone, shown beside the local clock. Same contract - the key is the
        // stored `target`, so it is never renamed. Deliberately not [KEY_CLOCK]: that cell shows the
        // system zone by contract, and reusing its key would retarget every clock already placed.
        const val KEY_WORLD_CLOCK = "world_clock"

        // S1930: the two home-screen widgets whose cell owns a configured instance. Same contract as
        // every key above - this is what a cell's `target` column stores, so it is never renamed - and
        // both spell the `gadgetKey` HomeWidgetCatalog already publishes for them, so the desktop cell
        // and its home-screen twin answer to one name.
        const val KEY_RANDOM_PHOTO_FRAME = "random_photo_frame"
        const val KEY_CAMERA_QUICK_CAPTURE = "camera_quick_capture"

        private const val SEPARATOR = ':'
    }
}
