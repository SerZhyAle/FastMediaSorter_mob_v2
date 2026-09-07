package com.sza.fastmediasorter.wear.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

/**
 * S2655: the shared half of every themed settings section - the one store and the two write helpers.
 *
 * A base class rather than seven copies because [stampedEdit] is the S2093 invariant: the value and
 * its timestamp are written in one edit, so a setter cannot record a change without recording when it
 * happened. Copied per section, the next section to be written would be the one that forgot.
 */
abstract class WearPreferenceSection(settings: WearSettingsDataStore) {

    protected val store = settings.store

    // S2093: callers name the registry field rather than the DataStore key, because the phone-watch
    // exchange contract addresses fields.
    protected suspend fun stampedEdit(field: String, write: (MutablePreferences) -> Unit) {
        val changedAt = System.currentTimeMillis()
        store.edit { prefs ->
            write(prefs)
            prefs[WearPreferenceKeys.SETTING_TIMESTAMPS] = SettingTimestampsCodec.encode(
                SettingTimestampsCodec.decode(prefs[WearPreferenceKeys.SETTING_TIMESTAMPS]) +
                    (field to changedAt)
            )
        }
    }

    // A null clears the key rather than writing an empty string, so "never chosen" and "chosen, then
    // cleared" leave the same state.
    protected suspend fun writeNullableString(key: Preferences.Key<String>, value: String?) {
        store.edit { prefs ->
            if (value == null) {
                prefs.remove(key)
            } else {
                prefs[key] = value
            }
        }
    }
}
