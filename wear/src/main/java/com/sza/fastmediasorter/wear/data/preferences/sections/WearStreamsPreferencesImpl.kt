package com.sza.fastmediasorter.wear.data.preferences.sections

import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearStreamsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2146: stored and returned as written, with no parsing here - the enums these names belong to are
 * UI types, and the screen is where they are read back.
 */
@Singleton
class WearStreamsPreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearStreamsPreferences {

    override val streamsSortOrderName: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STREAMS_SORT_ORDER]
    }

    override suspend fun setStreamsSortOrderName(name: String?) {
        writeNullableString(WearPreferenceKeys.STREAMS_SORT_ORDER, name)
    }

    override val streamsFilterKindName: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STREAMS_FILTER_KIND]
    }

    override suspend fun setStreamsFilterKindName(name: String?) {
        writeNullableString(WearPreferenceKeys.STREAMS_FILTER_KIND, name)
    }

    override val streamsSelectedTopic: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STREAMS_SELECTED_TOPIC]
    }

    override suspend fun setStreamsSelectedTopic(topic: String?) {
        writeNullableString(WearPreferenceKeys.STREAMS_SELECTED_TOPIC, topic)
    }

    override val streamsSelectedLanguage: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STREAMS_SELECTED_LANGUAGE]
    }

    override suspend fun setStreamsSelectedLanguage(language: String?) {
        writeNullableString(WearPreferenceKeys.STREAMS_SELECTED_LANGUAGE, language)
    }
}
