package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists counters and state for the In-App Review eligibility check (S0135).
 *
 * Keys are namespaced with `review_` to avoid collision with existing preference keys.
 * Uses the shared `DataStore<Preferences>` instance from AppModule.
 */
@Singleton
class ReviewEligibilityDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val SORT_OPS_COUNT      = longPreferencesKey("review_sort_ops_count")
        val SESSIONS_WITH_SORT  = longPreferencesKey("review_sessions_with_sort")
        val REVIEW_SHOWN_EPOCH  = longPreferencesKey("review_shown_epoch_ms")
    }

    /** Increments cumulative sort-op count by [delta]. Returns new value. */
    suspend fun incrementSortOps(delta: Long = 1L): Long {
        var newValue = 0L
        dataStore.edit { prefs ->
            newValue = (prefs[SORT_OPS_COUNT] ?: 0L) + delta
            prefs[SORT_OPS_COUNT] = newValue
        }
        return newValue
    }

    /** Increments session-with-sort count by 1. Returns new value. */
    suspend fun incrementSessions(): Long {
        var newValue = 0L
        dataStore.edit { prefs ->
            newValue = (prefs[SESSIONS_WITH_SORT] ?: 0L) + 1L
            prefs[SESSIONS_WITH_SORT] = newValue
        }
        return newValue
    }

    /** Records that the review dialog was shown at [epochMs]. */
    suspend fun markReviewShown(epochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs -> prefs[REVIEW_SHOWN_EPOCH] = epochMs }
    }

    /** Returns a one-shot snapshot of the three counters. */
    suspend fun snapshot(): ReviewEligibilitySnapshot {
        val prefs = dataStore.data.first()
        return ReviewEligibilitySnapshot(
            sortOpsCount     = prefs[SORT_OPS_COUNT]     ?: 0L,
            sessionsWithSort = prefs[SESSIONS_WITH_SORT] ?: 0L,
            reviewShownEpoch = prefs[REVIEW_SHOWN_EPOCH] ?: 0L
        )
    }

    /** Resets all counters to zero. Debug builds only. */
    suspend fun resetDebug() {
        dataStore.edit { prefs ->
            prefs[SORT_OPS_COUNT]     = 0L
            prefs[SESSIONS_WITH_SORT] = 0L
            prefs[REVIEW_SHOWN_EPOCH] = 0L
        }
    }
}

/** Immutable snapshot of review eligibility state at a point in time. */
data class ReviewEligibilitySnapshot(
    val sortOpsCount: Long,
    val sessionsWithSort: Long,
    val reviewShownEpoch: Long
)
