package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.MainListSession

/**
 * S2199: remembers how the resource list was last narrowed and ordered.
 *
 * An interface rather than the store itself, because the screen must not reach into the data layer -
 * the `ui-imports-data` gate (S2103) refuses that, and it is right to: swapping the backing store
 * would otherwise mean editing a ViewModel.
 */
interface MainListSessionRepository {

    suspend fun read(): MainListSession

    suspend fun write(session: MainListSession)
}
