package com.sza.fastmediasorter.wear.data.broadcast

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * S2813: what stays the same about this watch's broadcast between two sessions.
 *
 * Both values exist for one reason - a repeated broadcast used to be unrecognisable. The port was
 * asked of the platform on every bind and the address carried nothing naming the watch, so the second
 * session's code differed from the first and the phone filed it as an unrelated stream, leaving the
 * entry the owner had already used pointing at a dead address.
 *
 * Takes the store rather than [com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore]
 * so it can be exercised against a plain file store; the wrapper's own KDoc rules out binding
 * `DataStore<Preferences>` into the graph, which is why the wiring is a `@Provides` and not an
 * `@Inject constructor`.
 */
class WearBroadcastIdentityStore(
    private val store: DataStore<Preferences>
) {

    /** The port the last broadcast really listened on, or null while none ever has. */
    suspend fun preferredPort(): Int? = store.data.first()[WearPreferenceKeys.BROADCAST_PORT]

    /**
     * Records the port a session actually bound. A fallback overwrites the previous value on purpose:
     * the next session then starts from a port that was free a moment ago rather than from one that
     * has already refused once.
     */
    suspend fun rememberPort(port: Int) {
        store.edit { prefs -> prefs[WearPreferenceKeys.BROADCAST_PORT] = port }
    }

    /**
     * This watch's source id, generated on first use and stable afterwards.
     *
     * Generation happens inside the edit block, so two callers racing at the first broadcast cannot
     * end up with two ids - the second reads what the first wrote. An id that changed per session
     * would leave the phone unable to recognise the source, which is the defect this store exists to
     * remove.
     */
    suspend fun sourceId(): String {
        var resolved = ""
        store.edit { prefs ->
            val existing = prefs[WearPreferenceKeys.BROADCAST_SOURCE_ID]
            resolved = if (existing.isNullOrBlank()) {
                UUID.randomUUID().toString().also { prefs[WearPreferenceKeys.BROADCAST_SOURCE_ID] = it }
            } else {
                existing
            }
        }
        return resolved
    }
}
