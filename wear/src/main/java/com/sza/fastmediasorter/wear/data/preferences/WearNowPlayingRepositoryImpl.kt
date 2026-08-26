package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.domain.model.WearNowPlaying
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nowPlayingDataStore: DataStore<Preferences> by preferencesDataStore(name = "wear_now_playing")

/**
 * S2044 / S2047: DataStore implementation storing now-playing state in its own isolated file.
 */
@Singleton
class WearNowPlayingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearNowPlayingRepository {

    private object PreferencesKeys {
        val TITLE = stringPreferencesKey("title")
        val SUBTITLE = stringPreferencesKey("subtitle")
        val IS_PLAYING = booleanPreferencesKey("is_playing")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    override val nowPlaying: Flow<WearNowPlaying> = context.nowPlayingDataStore.data.map { prefs ->
        val title = prefs[PreferencesKeys.TITLE].orEmpty()
        if (title.isBlank()) {
            WearNowPlaying.EMPTY
        } else {
            WearNowPlaying(
                title = title,
                subtitle = prefs[PreferencesKeys.SUBTITLE],
                isPlaying = prefs[PreferencesKeys.IS_PLAYING] ?: false,
                updatedAtEpochMs = prefs[PreferencesKeys.UPDATED_AT] ?: 0L
            )
        }
    }

    override suspend fun setNowPlaying(title: String, subtitle: String?) {
        context.nowPlayingDataStore.edit { prefs ->
            prefs[PreferencesKeys.TITLE] = title
            if (subtitle != null) {
                prefs[PreferencesKeys.SUBTITLE] = subtitle
            } else {
                prefs.remove(PreferencesKeys.SUBTITLE)
            }
            prefs[PreferencesKeys.IS_PLAYING] = true
            prefs[PreferencesKeys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    override suspend fun setPlaying(isPlaying: Boolean) {
        context.nowPlayingDataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_PLAYING] = isPlaying
            prefs[PreferencesKeys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    override suspend fun clearPlayingFlag() {
        context.nowPlayingDataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_PLAYING] = false
            prefs[PreferencesKeys.UPDATED_AT] = System.currentTimeMillis()
        }
    }
}
