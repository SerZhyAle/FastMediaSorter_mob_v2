package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.domain.model.MainListSession
import com.sza.fastmediasorter.domain.usecase.ReadMainListSessionUseCase
import com.sza.fastmediasorter.domain.usecase.SaveMainListSessionUseCase
import com.sza.fastmediasorter.ui.main.MainState

/**
 * S2199: carries the resource list's sort and filters across a restart.
 *
 * Its own class rather than two methods on `MainViewModel`, which is already at the size ceiling -
 * adding them there is what pushed it past `LargeClass`, and the ViewModel gains nothing from
 * knowing how a session is shaped.
 */
class MainListSessionManager(
    private val readSession: ReadMainListSessionUseCase,
    private val saveSession: SaveMainListSessionUseCase
) {

    /** The state with anything remembered applied; fields with nothing stored keep [state]'s value. */
    suspend fun restore(state: MainState): MainState {
        val session = readSession()
        return state.copy(
            sortMode = session.sortMode ?: state.sortMode,
            filterByType = session.filterByType,
            filterByMediaType = session.filterByMediaType,
            filterByName = session.filterByName
        )
    }

    /**
     * Reads the values off [state] rather than taking them as arguments, because a manual reorder
     * flips the sort to MANUAL behind the caller's back and a caller passing its own choice would
     * store an order the screen is not in.
     */
    suspend fun persist(state: MainState) {
        saveSession(
            MainListSession(
                sortMode = state.sortMode,
                filterByType = state.filterByType,
                filterByMediaType = state.filterByMediaType,
                filterByName = state.filterByName
            )
        )
    }
}
