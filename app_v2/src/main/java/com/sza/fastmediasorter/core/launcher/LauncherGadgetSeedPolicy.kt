package com.sza.fastmediasorter.core.launcher

/**
 * S2672: every gadget key the desktop knows, and whether a fresh desktop seeds it.
 *
 * `LauncherGadgetRegistry` lives in `src/launcherEnabled` and Rule 14 forbids importing it from
 * `src/main`, so the keys are spelled here a second time - the same duplication contract
 * [LauncherStarterSets] has always carried, now with one home instead of fifteen private consts.
 * `LauncherStarterSetsParityTest` (testLauncherEnabled) is what ties the two spellings together.
 *
 * The point of the table is the OTHER twenty. Before this ticket the seed named only the keys it
 * emitted, so a key nothing seeds was indistinguishable from a key someone forgot, and twenty of the
 * thirty-five had accumulated in that gap with no recorded decision anywhere. A key now carries either
 * [SeedDecision.Seeded] or a reason it is not, and a new registry key with no entry here fails the
 * parity test rather than joining them silently.
 */
object LauncherGadgetSeedPolicy {

    const val KEY_CLOCK = "clock"
    const val KEY_WEATHER = "weather"
    const val KEY_PLAYLIST = "playlist"
    const val KEY_STREAMS = "streams"
    const val KEY_FOLDER_PREVIEW = "folder_preview"
    const val KEY_AUDIO_NOW_PLAYING = "audio_now_playing"
    const val KEY_COMPASS = "compass"
    const val KEY_SPEED = "speed"
    const val KEY_SPEED_CHART = "speed_chart"
    const val KEY_ALTITUDE_CHART = "altitude_chart"
    const val KEY_STEPS = "steps"
    const val KEY_ALTITUDE = "altitude"
    const val KEY_SATELLITES = "satellites"
    const val KEY_MAP = "map"
    const val KEY_GOOGLE_MAPS_LIVE = "google_maps_live"
    const val KEY_GOOGLE_KEEP_LIVE = "google_keep_live"
    const val KEY_GOOGLE_CALENDAR_LIVE = "google_calendar_live"
    const val KEY_TRANSLATOR = "translator"
    const val KEY_NETWORK = "network"
    const val KEY_BATTERY = "battery"
    const val KEY_STORAGE = "storage"
    const val KEY_RESOURCES = "resources"
    const val KEY_SEARCH = "search"
    const val KEY_NETWORK_INDICATOR = "network_indicator"
    const val KEY_YOUTUBE = "youtube"
    const val KEY_YOUTUBE_MUSIC = "youtube_music"
    const val KEY_MEDIA_AUDIO_WINDOW = "media_audio_window"
    const val KEY_MEDIA_VIDEO_WINDOW = "media_video_window"
    const val KEY_MEDIA_DOCUMENT_WINDOW = "media_document_window"
    const val KEY_MEDIA_IMAGE_WINDOW = "media_image_window"
    const val KEY_STREAM_WINDOW = "stream_window"
    const val KEY_YOUTUBE_CHANNEL_WINDOW = "youtube_channel_window"
    const val KEY_WORLD_CLOCK = "world_clock"
    const val KEY_SUN_DEWPOINT = "sun_dewpoint"
    const val KEY_RANDOM_PHOTO_FRAME = "random_photo_frame"
    const val KEY_CAMERA_QUICK_CAPTURE = "camera_quick_capture"

    /** Why a key is absent from every profile's starter desktop. */
    enum class NoSeedReason {
        /**
         * The cell's param is a token minted by the gadget's own configuration screen, keyed on an
         * `AppWidgetManager` instance id (S1930). A seed has no instance to point at and no way to
         * create one, so a bare key renders an unavailable tile.
         */
        REQUIRES_CONFIGURED_WIDGET_INSTANCE,

        /**
         * The cell needs a choice only a person can make - a channel, a time zone, a place, an
         * indicator - and the gadget has no defensible default. Deliberately a separate axis from
         * `LauncherGadget.requiresResourceParam`, which its own KDoc scopes to a picked library
         * resource; all four gadgets here declare that flag false because their param is not one.
         */
        REQUIRES_PICKED_PARAM,

        /**
         * A ruling kept the tile off a fresh desktop, and the ticket that made it is named in the
         * comment above the entry - S1747 for the two location readouts, S2682 for the ten it closed.
         * The tile stays addable by hand, so the ruling costs a user nothing but a gesture.
         */
        EXCLUDED_BY_OWNER,
    }

    sealed interface SeedDecision {
        /** Emitted by [LauncherStarterSets.itemsFor] on at least one profile - the parity test proves it. */
        data object Seeded : SeedDecision

        data class NotSeeded(val reason: NoSeedReason) : SeedDecision
    }

    private val SEEDED = SeedDecision.Seeded

    private fun notSeeded(reason: NoSeedReason) = SeedDecision.NotSeeded(reason)

    /**
     * The whole registry, decided. Grouped by decision rather than by gadget family, because the
     * question this table answers is "does a fresh desktop show it", and a reader checking that should
     * not have to read thirty-five lines to find the four that share a cause.
     */
    val decisions: Map<String, SeedDecision> = buildMap {
        // Seeded. Which profiles each one lands on is decided by itemsFor, not restated here: a second
        // copy of those conditions would rot the moment a profile rule changed, and the parity test
        // proves the membership instead by running the table.
        put(KEY_CLOCK, SEEDED)
        put(KEY_WEATHER, SEEDED)
        put(KEY_PLAYLIST, SEEDED)
        put(KEY_STREAMS, SEEDED)
        put(KEY_FOLDER_PREVIEW, SEEDED)
        put(KEY_AUDIO_NOW_PLAYING, SEEDED)
        put(KEY_COMPASS, SEEDED)
        put(KEY_SPEED, SEEDED)
        put(KEY_SEARCH, SEEDED)
        put(KEY_BATTERY, SEEDED)
        put(KEY_GOOGLE_MAPS_LIVE, SEEDED)
        put(KEY_TRANSLATOR, SEEDED)
        put(KEY_STORAGE, SEEDED)
        put(KEY_MEDIA_IMAGE_WINDOW, SEEDED)
        put(KEY_MEDIA_AUDIO_WINDOW, SEEDED)
        put(KEY_MEDIA_VIDEO_WINDOW, SEEDED)
        put(KEY_MEDIA_DOCUMENT_WINDOW, SEEDED)

        // S1930: both are keyed on an AppWidgetManager instance id end to end. Minting that token is the
        // configuration flow's job and there is no default instance to fall back on.
        put(KEY_RANDOM_PHOTO_FRAME, notSeeded(NoSeedReason.REQUIRES_CONFIGURED_WIDGET_INSTANCE))
        put(KEY_CAMERA_QUICK_CAPTURE, notSeeded(NoSeedReason.REQUIRES_CONFIGURED_WIDGET_INSTANCE))

        // A channel, a zone, a place and an indicator. Without the param the first three say so on the
        // tile; network_indicator is worse - it substitutes a default indicator nobody chose.
        put(KEY_STREAM_WINDOW, notSeeded(NoSeedReason.REQUIRES_PICKED_PARAM))
        // S2032: a channel, like the stream window above - a seeded cell would carry no channel and
        // could only say so, and the owner's own reason for placing it is which channel it names.
        put(KEY_YOUTUBE_CHANNEL_WINDOW, notSeeded(NoSeedReason.REQUIRES_PICKED_PARAM))
        put(KEY_WORLD_CLOCK, notSeeded(NoSeedReason.REQUIRES_PICKED_PARAM))
        put(KEY_SUN_DEWPOINT, notSeeded(NoSeedReason.REQUIRES_PICKED_PARAM))
        put(KEY_NETWORK_INDICATOR, notSeeded(NoSeedReason.REQUIRES_PICKED_PARAM))

        // S1747: the compass replaced this pair in the seed. A bare satellite count says nothing to a
        // user, and altitude is a thing one adds deliberately.
        put(KEY_ALTITUDE, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_SATELLITES, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))

        // S2682: a second tile doing a job an already-seeded tile does. The budget is six, so a duplicate
        // is paid for by dropping something that is not one.
        put(KEY_SPEED_CHART, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_MAP, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_NETWORK, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_RESOURCES, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_YOUTUBE, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_YOUTUBE_MUSIC, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))

        // S2682: a readout one adds deliberately, by the argument S1747 used on altitude and satellites.
        // The chart of an already-excluded readout cannot be the more casual of the two.
        put(KEY_ALTITUDE_CHART, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_STEPS, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))

        // S2682: without a Google session both render a sign-in form, and a fresh desktop must not open
        // on one - the same objection REQUIRES_CONFIGURED_WIDGET_INSTANCE raises against an unavailable tile.
        put(KEY_GOOGLE_KEEP_LIVE, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
        put(KEY_GOOGLE_CALENDAR_LIVE, notSeeded(NoSeedReason.EXCLUDED_BY_OWNER))
    }

    /** The keys [LauncherStarterSets] may emit - derived, so the two can never be edited apart. */
    val seededKeys: Set<String> =
        decisions.filterValues { it is SeedDecision.Seeded }.keys
}
