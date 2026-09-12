package com.sza.fastmediasorter.wear.domain.repository.preferences

import kotlinx.coroutines.flow.Flow

/** State owned by the watch's mini-apps, kept on the watch and never reconciled with the phone. */
interface WearMiniAppPreferences {

    /**
     * S1710: the calculator's own state.
     *
     * An empty history and a null memory are a first run, not a broken store.
     */
    val calculatorHistory: Flow<List<String>>
    suspend fun setCalculatorHistory(entries: List<String>)

    val calculatorMemory: Flow<String?>
    suspend fun setCalculatorMemory(value: String?)

    /**
     * S1710: the game started on the watch, serialized by GameStateSnapshot.
     *
     * Null means no game has been started - an absent key is a first run, not a broken save, and a
     * stored string the snapshot cannot read is discarded the same way rather than reported.
     */
    val gameState: Flow<String?>
    suspend fun setGameState(value: String?)

    /**
     * S2825: the stopwatch's participant count.
     *
     * An absent key is a first run, not a broken store, and a stored value the app no longer offers is
     * snapped into the allowed set rather than reported.
     */
    val stopwatchParticipantCount: Flow<Int>
    suspend fun setStopwatchParticipantCount(count: Int)

    /**
     * S2825: the rendered text of the last finished measurement.
     *
     * Null means no measurement has been finished yet - the watch has no file to export to, so this is
     * where a result survives the program being dismissed.
     */
    val stopwatchLastResult: Flow<String?>
    suspend fun setStopwatchLastResult(value: String?)
}
