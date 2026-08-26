package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Record separator of the joined history. A control character, so no expression can contain it.
private const val HISTORY_RECORD_SEPARATOR = "\u001E"

/**
 * DataStore-based implementation of WearPreferencesRepository.
 */
class WearPreferencesRepositoryImpl(
    private val context: Context
) : WearPreferencesRepository {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wear_settings")

    // Internal, not private: the one invariant that cannot be read off the code is that the file
    // list and the navigation screens address DIFFERENT keys (S1730 ADR-3), and a test has to see
    // both names to assert it.
    internal object PreferencesKeys {
        val AUDIO_ENABLED = booleanPreferencesKey("wear_audio_enabled")
        val VIDEO_ENABLED = booleanPreferencesKey("wear_video_enabled")
        val IMAGES_ENABLED = booleanPreferencesKey("wear_images_enabled")

        val SLIDESHOW_ENABLED = booleanPreferencesKey("wear_slideshow_enabled")
        val SLIDESHOW_INTERVAL = intPreferencesKey("wear_slideshow_interval_seconds")

        val DOWNLOAD_ALBUM_ART = booleanPreferencesKey("wear_download_album_art")

        val SHUFFLE_ENABLED = booleanPreferencesKey("wear_shuffle_enabled")

        val VIEW_MODE = stringPreferencesKey("wear_view_mode")
        val BACKGROUND_MODE = stringPreferencesKey("wear_background_mode")
        val FILE_LIST_VIEW_MODE = stringPreferencesKey("wear_file_list_view_mode")
        val VIDEO_SCALE_MODE = stringPreferencesKey("wear_video_scale_mode")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("wear_keep_screen_awake")
        val LAST_USED_RESOURCE = stringPreferencesKey("wear_last_used_resource")
        val LAST_USED_RESOURCE_ID = stringPreferencesKey("wear_last_used_resource_id")
        val LAST_USED_RESOURCES = stringPreferencesKey("wear_last_used_resources")
        val STREAMS_SECTION_ENABLED = booleanPreferencesKey("wear_streams_section_enabled")

        val CALCULATOR_HISTORY = stringPreferencesKey("wear_calculator_history")
        val CALCULATOR_MEMORY = stringPreferencesKey("wear_calculator_memory")
        val GAME_STATE = stringPreferencesKey("wear_game_state")
        val AUTO_ROTATION_ENABLED = booleanPreferencesKey("wear_auto_rotation_enabled")
        val APP_LANGUAGE = stringPreferencesKey("wear_app_language")
        val VOICE_NOTE_SEND_POLICY = stringPreferencesKey("wear_voice_note_send_policy")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("wear_notification_permission_asked")
    }

    // Media type toggles
    override val isAudioEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUDIO_ENABLED] ?: true
    }

    override val isVideoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VIDEO_ENABLED] ?: true
    }

    override val isImagesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IMAGES_ENABLED] ?: true
    }

    override suspend fun setAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUDIO_ENABLED] = enabled
        }
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VIDEO_ENABLED] = enabled
        }
    }

    override suspend fun setImagesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IMAGES_ENABLED] = enabled
        }
    }

    // Slideshow settings
    override val isSlideshowEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SLIDESHOW_ENABLED] ?: false
    }

    override val slideshowIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SLIDESHOW_INTERVAL] ?: 5
    }

    override suspend fun setSlideshowEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SLIDESHOW_ENABLED] = enabled
        }
    }

    override suspend fun setSlideshowIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SLIDESHOW_INTERVAL] = seconds
        }
    }

    // Album art settings
    override val downloadAlbumArt: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DOWNLOAD_ALBUM_ART] ?: false
    }

    override suspend fun setDownloadAlbumArt(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DOWNLOAD_ALBUM_ART] = enabled
        }
    }

    // Playback order
    override val isShuffleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHUFFLE_ENABLED] ?: false
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHUFFLE_ENABLED] = enabled
        }
    }

    // Screen settings
    override val viewMode: Flow<WearViewMode> = context.dataStore.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[PreferencesKeys.VIEW_MODE])
    }

    override suspend fun setViewMode(mode: WearViewMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VIEW_MODE] = mode.name
        }
    }

    // S1730: its own key, never wear_view_mode - the home screen stays a list while a photo folder
    // is a grid, which one shared value cannot express.
    override val fileListViewMode: Flow<WearViewMode> = context.dataStore.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[PreferencesKeys.FILE_LIST_VIEW_MODE])
    }

    override suspend fun setFileListViewMode(mode: WearViewMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.FILE_LIST_VIEW_MODE] = mode.name
        }
    }

    // S2000: an absent value reads as the branded animation - the one background that needs no
    // delivered file, so a watch that never received a frame still draws something.
    override val backgroundMode: Flow<WearBackgroundMode> = context.dataStore.data.map { prefs ->
        WearBackgroundMode.fromNameOrDefault(prefs[PreferencesKeys.BACKGROUND_MODE])
    }

    override suspend fun setBackgroundMode(mode: WearBackgroundMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BACKGROUND_MODE] = mode.name
        }
    }

    // S1948: an absent value has to read as FIT, so a watch that never touched the button keeps
    // today's first-run behaviour rather than inheriting whatever the enum happens to declare first.
    override val videoScaleMode: Flow<VideoScaleMode> = context.dataStore.data.map { prefs ->
        VideoScaleMode.fromNameOrDefault(prefs[PreferencesKeys.VIDEO_SCALE_MODE])
    }

    override suspend fun setVideoScaleMode(mode: VideoScaleMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VIDEO_SCALE_MODE] = mode.name
        }
    }

    override val keepScreenAwakeOutsidePlayers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEEP_SCREEN_AWAKE] ?: false
    }

    override suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEEP_SCREEN_AWAKE] = enabled
        }
    }

    // Home section state
    // S1836: an entry is emitted only when both halves are stored. An install upgraded from a build
    // that kept the name alone holds no identifier, and a caption that addresses nothing is not a
    // shortcut. S1974: the single stored pair became a history, and the two legacy keys are read as
    // its seed so an upgraded install keeps the shortcut it already had.
    override val lastUsedResources: Flow<List<LastUsedResource>> = context.dataStore.data.map { prefs ->
        val stored = prefs[PreferencesKeys.LAST_USED_RESOURCES]
        if (stored == null) legacyLastUsedResource(prefs) else LastUsedResourceHistory.decode(stored)
    }

    private fun legacyLastUsedResource(prefs: Preferences): List<LastUsedResource> {
        val id = prefs[PreferencesKeys.LAST_USED_RESOURCE_ID]
        val name = prefs[PreferencesKeys.LAST_USED_RESOURCE]
        return if (id == null || name == null) emptyList() else listOf(LastUsedResource(id, name))
    }

    override suspend fun setLastUsedResource(id: String, name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.LAST_USED_RESOURCES]
                ?.let { LastUsedResourceHistory.decode(it) }
                ?: legacyLastUsedResource(prefs)
            val pushed = LastUsedResourceHistory.push(current, LastUsedResource(id, name))
            prefs[PreferencesKeys.LAST_USED_RESOURCES] = LastUsedResourceHistory.encode(pushed)
        }
    }

    override suspend fun clearLastUsedResource() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.LAST_USED_RESOURCES)
            // The legacy pair is removed too: it is the fallback the reader falls back to, so leaving
            // it behind would resurrect the shortcut the caller just cleared.
            prefs.remove(PreferencesKeys.LAST_USED_RESOURCE_ID)
            prefs.remove(PreferencesKeys.LAST_USED_RESOURCE)
        }
    }

    override val streamsSectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.STREAMS_SECTION_ENABLED] ?: true
    }

    override suspend fun setStreamsSectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.STREAMS_SECTION_ENABLED] = enabled
        }
    }

    // Calculator state. The history is one joined string rather than a string set: a set has no order,
    // and the history is defined newest first.
    override val calculatorHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CALCULATOR_HISTORY]
            ?.split(HISTORY_RECORD_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    override suspend fun setCalculatorHistory(entries: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CALCULATOR_HISTORY] = entries.joinToString(HISTORY_RECORD_SEPARATOR)
        }
    }

    override val calculatorMemory: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CALCULATOR_MEMORY]
    }

    override suspend fun setCalculatorMemory(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(PreferencesKeys.CALCULATOR_MEMORY)
            } else {
                prefs[PreferencesKeys.CALCULATOR_MEMORY] = value
            }
        }
    }

    // S1710: the raw serialized snapshot, kept opaque here - the store must not know the game's
    // schema, so an unreadable string is rejected by GameStateSnapshot and never by this layer.
    override val gameState: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.GAME_STATE]
    }

    override suspend fun setGameState(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(PreferencesKeys.GAME_STATE)
            } else {
                prefs[PreferencesKeys.GAME_STATE] = value
            }
        }
    }

    override val isAutoRotationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUTO_ROTATION_ENABLED] ?: false
    }

    override suspend fun setAutoRotationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_ROTATION_ENABLED] = enabled
        }
    }

    override val appLanguage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.APP_LANGUAGE]
    }

    override suspend fun setAppLanguage(languageCode: String?) {
        context.dataStore.edit { prefs ->
            if (languageCode == null) {
                prefs.remove(PreferencesKeys.APP_LANGUAGE)
            } else {
                prefs[PreferencesKeys.APP_LANGUAGE] = languageCode
            }
        }
    }

    // S1862: stored by name, like every other enum preference in this file - an ordinal would
    // re-point stored values the day a third policy is inserted between the two.
    override val voiceNoteSendPolicy: Flow<VoiceNoteSendPolicy> = context.dataStore.data.map { prefs ->
        VoiceNoteSendPolicy.fromNameOrDefault(prefs[PreferencesKeys.VOICE_NOTE_SEND_POLICY])
    }

    override suspend fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VOICE_NOTE_SEND_POLICY] = policy.name
        }
    }

    // S1961: absent reads as "not asked yet", which is what an untouched watch is.
    override val notificationPermissionAsked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.NOTIFICATION_PERMISSION_ASKED] ?: false
    }

    override suspend fun setNotificationPermissionAsked(asked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFICATION_PERMISSION_ASKED] = asked
        }
    }
}
