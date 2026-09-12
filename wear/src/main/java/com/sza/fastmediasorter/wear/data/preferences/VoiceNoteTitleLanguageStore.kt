package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceNoteTitleDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wear_voice_note_titles")

/**
 * S2626: the UI language the published voice-note titles were last written under.
 *
 * Its own store rather than a pair on [com.sza.fastmediasorter.wear.domain.repository
 * .WearPreferencesRepository] for two reasons. It is not a setting - nothing displays it, the phone
 * never sends it and the owner cannot change it - and that repository already sits at detekt's
 * `TooManyFunctions` ceiling, so a pair added there buys a correct fix with absorbed debt.
 *
 * Absent means the titles predate this record, which reads as a mismatch against any active language
 * and so makes the first pass after an upgrade repair whatever an earlier build left behind.
 */
@Singleton
class VoiceNoteTitleLanguageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val tag: Flow<String?> = context.voiceNoteTitleDataStore.data.map { prefs ->
        prefs[TAG_KEY]
    }

    suspend fun setTag(tag: String) {
        context.voiceNoteTitleDataStore.edit { prefs ->
            prefs[TAG_KEY] = tag
        }
    }

    private companion object {
        val TAG_KEY = stringPreferencesKey("titles_language_tag")
    }
}
