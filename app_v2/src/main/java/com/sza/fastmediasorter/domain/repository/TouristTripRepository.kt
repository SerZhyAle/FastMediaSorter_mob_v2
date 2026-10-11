package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot

/**
 * S4145: where the Tourist trip survives a process restart. An absent store loads as
 * [TouristTripSnapshot.EMPTY] - the ordinary "no trip yet" case, not an error.
 */
interface TouristTripRepository {

    suspend fun load(): TouristTripSnapshot

    suspend fun save(snapshot: TouristTripSnapshot)
}
