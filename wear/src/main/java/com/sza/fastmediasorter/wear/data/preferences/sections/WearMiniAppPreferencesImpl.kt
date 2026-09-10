package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMiniAppPreferences
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Record separator of the joined history. A control character, so no expression can contain it.
private const val HISTORY_RECORD_SEPARATOR = "\u001E"

@Singleton
class WearMiniAppPreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearMiniAppPreferences {

    // The history is one joined string rather than a string set: a set has no order, and the history
    // is defined newest first.
    override val calculatorHistory: Flow<List<String>> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.CALCULATOR_HISTORY]
            ?.split(HISTORY_RECORD_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    override suspend fun setCalculatorHistory(entries: List<String>) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.CALCULATOR_HISTORY] = entries.joinToString(HISTORY_RECORD_SEPARATOR)
        }
    }

    override val calculatorMemory: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.CALCULATOR_MEMORY]
    }

    override suspend fun setCalculatorMemory(value: String?) {
        writeNullableString(WearPreferenceKeys.CALCULATOR_MEMORY, value)
    }

    // S1710: the raw serialized snapshot, kept opaque here - the store must not know the game's
    // schema, so an unreadable string is rejected by GameStateSnapshot and never by this layer.
    override val gameState: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.GAME_STATE]
    }

    override suspend fun setGameState(value: String?) {
        writeNullableString(WearPreferenceKeys.GAME_STATE, value)
    }

    // S2825: snapped on the way out rather than on the way in, so a count written by an older build
    // still opens a screen instead of leaving the reader with a region the layout cannot draw.
    override val stopwatchParticipantCount: Flow<Int> = store.data.map { prefs ->
        WearStopwatchState.snapCount(
            prefs[WearPreferenceKeys.STOPWATCH_PARTICIPANT_COUNT] ?: WearStopwatchState.DEFAULT_COUNT
        )
    }

    override suspend fun setStopwatchParticipantCount(count: Int) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.STOPWATCH_PARTICIPANT_COUNT] = WearStopwatchState.snapCount(count)
        }
    }

    override val stopwatchLastResult: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.STOPWATCH_LAST_RESULT]
    }

    override suspend fun setStopwatchLastResult(value: String?) {
        writeNullableString(WearPreferenceKeys.STOPWATCH_LAST_RESULT, value)
    }
}
