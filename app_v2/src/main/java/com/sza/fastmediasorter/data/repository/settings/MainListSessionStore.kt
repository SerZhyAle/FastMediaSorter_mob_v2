package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.domain.model.MainListSession
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.MainListSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2199: last-session sort mode and filters of the resource list on the main screen.
 *
 * Its own DataStore file rather than the shared "settings" one: strategic ADR-2 scopes a list's
 * remembered narrowing to the screen that owns it, and the one implementation in this tree that
 * shares a key across screens ([com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore])
 * is exactly why - a filter chosen in one resource reappears in the next one opened.
 *
 * Enum values are stored as raw `.name` strings and decoded here rather than by the caller. A name
 * that no longer parses is dropped instead of throwing: a constant renamed in a later release must
 * not stop the first screen of the app from opening, and a dropped filter degrades to "no filter",
 * which is the safe direction to be wrong in.
 */
private val Context.mainListSessionDataStore by preferencesDataStore("main_list_session")

@Singleton
class MainListSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MainListSessionRepository {

    override suspend fun read(): MainListSession {
        val prefs = context.mainListSessionDataStore.data.first()
        return MainListSession(
            sortMode = prefs[KEY_LAST_SORT]?.let { name ->
                runCatching { SortMode.valueOf(name) }.getOrNull()
            },
            filterByType = prefs[KEY_LAST_TYPE_FILTER]?.mapNotNull { name ->
                runCatching { ResourceType.valueOf(name) }.getOrNull()
            }?.toSet()?.takeIf { it.isNotEmpty() },
            filterByMediaType = prefs[KEY_LAST_MEDIA_TYPE_FILTER]?.mapNotNull { name ->
                runCatching { MediaType.valueOf(name) }.getOrNull()
            }?.toSet()?.takeIf { it.isNotEmpty() },
            filterByName = prefs[KEY_LAST_NAME_FILTER],
        )
    }

    /**
     * An absent facet removes its key instead of writing an empty value, so a filter the user
     * switched off reads back as absent on the next start rather than as the one they just cleared.
     */
    override suspend fun write(session: MainListSession) {
        context.mainListSessionDataStore.edit { prefs ->
            prefs[KEY_LAST_SORT] = (session.sortMode ?: SortMode.MANUAL).name
            prefs.putOrRemove(KEY_LAST_TYPE_FILTER, session.filterByType?.map { it.name }?.toSet())
            prefs.putOrRemove(
                KEY_LAST_MEDIA_TYPE_FILTER,
                session.filterByMediaType?.map { it.name }?.toSet()
            )
            prefs.putOrRemove(KEY_LAST_NAME_FILTER, session.filterByName)
        }
    }

    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) set(key, value) else remove(key)
    }

    private companion object {
        val KEY_LAST_SORT = stringPreferencesKey("last_sort")
        val KEY_LAST_TYPE_FILTER = stringSetPreferencesKey("last_type_filter")
        val KEY_LAST_MEDIA_TYPE_FILTER = stringSetPreferencesKey("last_media_type_filter")
        val KEY_LAST_NAME_FILTER = stringPreferencesKey("last_name_filter")
    }
}
