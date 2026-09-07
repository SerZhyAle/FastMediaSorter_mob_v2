package com.sza.fastmediasorter.wear.data.preferences.sections

import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMediaTypePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearMediaTypePreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearMediaTypePreferences {

    override val isAudioEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.AUDIO_ENABLED] ?: true
    }

    override val isVideoEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.VIDEO_ENABLED] ?: true
    }

    override val isImagesEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.IMAGES_ENABLED] ?: true
    }

    override val isDocumentsEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.DOCUMENTS_ENABLED] ?: true
    }

    override suspend fun setAudioEnabled(enabled: Boolean) {
        stampedEdit("audioEnabled") { prefs ->
            prefs[WearPreferenceKeys.AUDIO_ENABLED] = enabled
        }
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        stampedEdit("videoEnabled") { prefs ->
            prefs[WearPreferenceKeys.VIDEO_ENABLED] = enabled
        }
    }

    override suspend fun setImagesEnabled(enabled: Boolean) {
        stampedEdit("imagesEnabled") { prefs ->
            prefs[WearPreferenceKeys.IMAGES_ENABLED] = enabled
        }
    }

    override suspend fun setDocumentsEnabled(enabled: Boolean) {
        stampedEdit("documentsEnabled") { prefs ->
            prefs[WearPreferenceKeys.DOCUMENTS_ENABLED] = enabled
        }
    }

    override val streamsSectionEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STREAMS_SECTION_ENABLED] ?: true
    }

    override suspend fun setStreamsSectionEnabled(enabled: Boolean) {
        stampedEdit("streamsSectionEnabled") { prefs ->
            prefs[WearPreferenceKeys.STREAMS_SECTION_ENABLED] = enabled
        }
    }
}
