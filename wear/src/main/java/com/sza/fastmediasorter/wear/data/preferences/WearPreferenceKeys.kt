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
    val LAST_USED_RESOURCE = stringPreferencesKey("wear_last_used_resource")
    val LAST_USED_RESOURCE_ID = stringPreferencesKey("wear_last_used_resource_id")
    val LAST_USED_RESOURCES = stringPreferencesKey("wear_last_used_resources")
    val STREAMS_SECTION_ENABLED = booleanPreferencesKey("wear_streams_section_enabled")

    // S2146: the streams screen's own filter and sort memory. Named per screen, not shared.
    val STREAMS_SORT_ORDER = stringPreferencesKey("wear_streams_sort_order")
    val STREAMS_FILTER_KIND = stringPreferencesKey("wear_streams_filter_kind")
    val STREAMS_SELECTED_TOPIC = stringPreferencesKey("wear_streams_selected_topic")
    val STREAMS_SELECTED_LANGUAGE = stringPreferencesKey("wear_streams_selected_language")

    val CALCULATOR_HISTORY = stringPreferencesKey("wear_calculator_history")
    val CALCULATOR_MEMORY = stringPreferencesKey("wear_calculator_memory")
    val GAME_STATE = stringPreferencesKey("wear_game_state")
    val AUTO_ROTATION_ENABLED = booleanPreferencesKey("wear_auto_rotation_enabled")
    val APP_LANGUAGE = stringPreferencesKey("wear_app_language")
    val VOICE_NOTE_SEND_POLICY = stringPreferencesKey("wear_voice_note_send_policy")
    val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("wear_notification_permission_asked")
    val SETTING_TIMESTAMPS = stringPreferencesKey("wear_setting_timestamps")
    val LAST_SETTINGS_SYNC = longPreferencesKey("wear_settings_last_sync")
}
