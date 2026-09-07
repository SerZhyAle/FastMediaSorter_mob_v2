package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearPlaybackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 5
private const val DEFAULT_PANEL_AUTO_HIDE_SECONDS = 15

@Singleton
class WearPlaybackPreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearPlaybackPreferences {

    override val isSlideshowEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.SLIDESHOW_ENABLED] ?: false
    }

    override val slideshowIntervalSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.SLIDESHOW_INTERVAL] ?: DEFAULT_SLIDESHOW_INTERVAL_SECONDS
    }

    override suspend fun setSlideshowEnabled(enabled: Boolean) {
        stampedEdit("slideshowEnabled") { prefs ->
            prefs[WearPreferenceKeys.SLIDESHOW_ENABLED] = enabled
        }
    }

    override suspend fun setSlideshowIntervalSeconds(seconds: Int) {
        stampedEdit("slideshowIntervalSeconds") { prefs ->
            prefs[WearPreferenceKeys.SLIDESHOW_INTERVAL] = seconds
        }
    }

    override val panelAutoHideSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.PANEL_AUTO_HIDE_SECONDS] ?: DEFAULT_PANEL_AUTO_HIDE_SECONDS
    }

    override suspend fun setPanelAutoHideSeconds(seconds: Int) {
        stampedEdit("panelAutoHideSeconds") { prefs ->
            prefs[WearPreferenceKeys.PANEL_AUTO_HIDE_SECONDS] = seconds
        }
    }

    override val downloadAlbumArt: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.DOWNLOAD_ALBUM_ART] ?: false
    }

    override suspend fun setDownloadAlbumArt(enabled: Boolean) {
        stampedEdit("downloadAlbumArt") { prefs ->
            prefs[WearPreferenceKeys.DOWNLOAD_ALBUM_ART] = enabled
        }
    }

    override val isShuffleEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.SHUFFLE_ENABLED] ?: false
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.SHUFFLE_ENABLED] = enabled
        }
    }

    // S1948: an absent value has to read as FIT, so a watch that never touched the button keeps
    // today's first-run behaviour rather than inheriting whatever the enum happens to declare first.
    override val videoScaleMode: Flow<VideoScaleMode> = store.data.map { prefs ->
        VideoScaleMode.fromNameOrDefault(prefs[WearPreferenceKeys.VIDEO_SCALE_MODE])
    }

    override suspend fun setVideoScaleMode(mode: VideoScaleMode) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.VIDEO_SCALE_MODE] = mode.name
        }
    }

    override val imageScaleMode: Flow<VideoScaleMode> = store.data.map { prefs ->
        VideoScaleMode.fromNameOrDefault(prefs[WearPreferenceKeys.IMAGE_SCALE_MODE])
    }

    override suspend fun setImageScaleMode(mode: VideoScaleMode) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.IMAGE_SCALE_MODE] = mode.name
        }
    }

    override val backgroundPlaybackEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.BACKGROUND_PLAYBACK] ?: false
    }

    override suspend fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        stampedEdit("backgroundPlaybackEnabled") { prefs ->
            prefs[WearPreferenceKeys.BACKGROUND_PLAYBACK] = enabled
        }
    }
}
