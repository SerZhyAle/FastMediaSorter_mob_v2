package com.sza.fastmediasorter.wear.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Every key of the `wear_settings` store.
 *
 * S2655: moved out of `WearPreferencesRepositoryImpl` when the settings were split by theme - a key
 * set nested in one section would make the other six depend on that section rather than on the keys.
 *
 * Internal, not private: the one invariant that cannot be read off the code is that the file list and
 * the navigation screens address DIFFERENT keys (S1730 ADR-3), and a test has to see both names to
 * assert it.
 */
internal object WearPreferenceKeys {
    val AUDIO_ENABLED = booleanPreferencesKey("wear_audio_enabled")
    val VIDEO_ENABLED = booleanPreferencesKey("wear_video_enabled")
    val IMAGES_ENABLED = booleanPreferencesKey("wear_images_enabled")

    /**
     * S2130: added after the other three, so it defaults to on rather than to the absent-key
     * default of off - documents are shown on the Phone screen today, and a key that read false
     * on first launch would present the update as having removed them.
     */
    val DOCUMENTS_ENABLED = booleanPreferencesKey("wear_documents_enabled")

    val SLIDESHOW_ENABLED = booleanPreferencesKey("wear_slideshow_enabled")
    val SLIDESHOW_INTERVAL = intPreferencesKey("wear_slideshow_interval_seconds")
    val PANEL_AUTO_HIDE_SECONDS = intPreferencesKey("wear_panel_auto_hide_seconds")

    val DOWNLOAD_ALBUM_ART = booleanPreferencesKey("wear_download_album_art")
    val WEAR_DISABLE_ANIMATIONS = booleanPreferencesKey("wear_disable_animations")
    val WEAR_POWER_SAVING_TRIGGER = stringPreferencesKey("wear_power_saving_trigger")
    val WEAR_UNIT_SYSTEM = stringPreferencesKey("wear_unit_system")

    /**
     * S2773: the screen geometry the user picked, stored by enum name.
     *
     * A string rather than a boolean because an absent entry has to stay distinguishable from a real
     * choice: no stored value means "take the answer of this build variant", which a false could not
     * say. Every other appearance key above defaults to a value; this one defaults to a question.
     */
    val WEAR_GEOMETRY_MODE = stringPreferencesKey("wear_geometry_mode")

    val SHUFFLE_ENABLED = booleanPreferencesKey("wear_shuffle_enabled")

    val VIEW_MODE = stringPreferencesKey("wear_view_mode")
    val BACKGROUND_MODE = stringPreferencesKey("wear_background_mode")
    val COLOR_SCHEME = stringPreferencesKey("wear_color_scheme")
    val FILE_LIST_VIEW_MODE = stringPreferencesKey("wear_file_list_view_mode")
    val BROWSE_CONTENT_TYPES = stringSetPreferencesKey("wear_browse_content_types")
    val BROWSE_SORT_ORDER = stringPreferencesKey("wear_browse_sort_order")
    val VIDEO_SCALE_MODE = stringPreferencesKey("wear_video_scale_mode")
    val IMAGE_SCALE_MODE = stringPreferencesKey("wear_image_scale_mode")
    val KEEP_SCREEN_AWAKE = booleanPreferencesKey("wear_keep_screen_awake")
    val BACKGROUND_PLAYBACK = booleanPreferencesKey("wear_background_playback")
    val DIM_CLOCK_OVERLAY_ENABLED = booleanPreferencesKey("wear_dim_clock_overlay_enabled")
    val DIM_CLOCK_SECONDS_VISIBLE = booleanPreferencesKey("wear_dim_clock_seconds_visible")
    val LAST_USED_RESOURCE = stringPreferencesKey("wear_last_used_resource")
    val LAST_USED_RESOURCE_ID = stringPreferencesKey("wear_last_used_resource_id")
    val LAST_USED_RESOURCES = stringPreferencesKey("wear_last_used_resources")

    // S3116: the mini-program opened last, stored as a WearAppId name. Absent means none has been
    // opened yet, which the home row reads as the Broadcast entrance it carried before this ticket.
    val LAST_USED_APP = stringPreferencesKey("wear_last_used_app")
    val STREAMS_SECTION_ENABLED = booleanPreferencesKey("wear_streams_section_enabled")

    // S2146: the streams screen's own filter and sort memory. Named per screen, not shared.
    val STREAMS_SORT_ORDER = stringPreferencesKey("wear_streams_sort_order")
    val STREAMS_FILTER_KIND = stringPreferencesKey("wear_streams_filter_kind")
    val STREAMS_SELECTED_TOPIC = stringPreferencesKey("wear_streams_selected_topic")
    val STREAMS_SELECTED_LANGUAGE = stringPreferencesKey("wear_streams_selected_language")

    /**
     * S2532: the reader's own two memories - the text size, and where each document was left.
     *
     * The positions are one string rather than a key per document: this store is flat, and a key
     * per file would leave a row behind for every file ever opened, with nothing to remove it.
     */
    val DOCUMENT_FONT_SIZE = stringPreferencesKey("wear_document_font_size")
    val DOCUMENT_READING_POSITIONS = stringPreferencesKey("wear_document_reading_positions")

    /**
     * S2813: the broadcast's own identity - the port the last session actually bound, and the id that
     * names this watch as a stream source to whoever scans its code.
     *
     * The port is absent rather than zero while no session has ever succeeded: zero is the value that
     * asks the platform for an ephemeral port, so it cannot also mean "nothing to ask for yet".
     */
    val BROADCAST_PORT = intPreferencesKey("wear_broadcast_port")
    val BROADCAST_SOURCE_ID = stringPreferencesKey("wear_broadcast_source_id")

    val CALCULATOR_HISTORY = stringPreferencesKey("wear_calculator_history")
    val CALCULATOR_MEMORY = stringPreferencesKey("wear_calculator_memory")
    val GAME_STATE = stringPreferencesKey("wear_game_state")

    /**
     * S2825: the stopwatch's own two durable fields - how many participants the screen splits into, and
     * the text of the last finished measurement.
     *
     * The measurement itself is deliberately absent: it is derived from a monotonic instant that does not
     * survive a reboot, so storing it would promise a continuity the clock cannot keep.
     */
    val STOPWATCH_PARTICIPANT_COUNT = intPreferencesKey("wear_stopwatch_participant_count")
    val STOPWATCH_LAST_RESULT = stringPreferencesKey("wear_stopwatch_last_result")
    val AUTO_ROTATION_ENABLED = booleanPreferencesKey("wear_auto_rotation_enabled")
    val APP_LANGUAGE = stringPreferencesKey("wear_app_language")
    val VOICE_NOTE_SEND_POLICY = stringPreferencesKey("wear_voice_note_send_policy")
    val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("wear_notification_permission_asked")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("wear_onboarding_completed")
    val SETTING_TIMESTAMPS = stringPreferencesKey("wear_setting_timestamps")
    val LAST_SETTINGS_SYNC = longPreferencesKey("wear_settings_last_sync")
}
