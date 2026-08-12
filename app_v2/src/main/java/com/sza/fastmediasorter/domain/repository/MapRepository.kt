package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.map.MapSnapshot

/**
 * S1175: what the launcher is allowed to know about the current-position map.
 *
 * [Stale] mirrors `WeatherResult.Stale` (S0426) for the same reason: losing the network must not blank
 * the cell, because an old picture with a marker still says where the user is, while an empty cell
 * reads as a broken gadget. [PermissionMissing] is kept apart from [Unavailable] because only the
 * first one is something the user can act on.
 */
sealed interface MapResult {

    data class Fresh(val snapshot: MapSnapshot) : MapResult

    data class Stale(val snapshot: MapSnapshot, val ageMs: Long) : MapResult

    /**
     * Carries the last snapshot when there is one: strategic §11.4 requires the cell to stay
     * meaningful without the permission, and a revoked grant - including Android's automatic revoke
     * for an unused app - must not turn a working picture into an error-only tile.
     */
    data class PermissionMissing(val snapshot: MapSnapshot?) : MapResult

    data object Unavailable : MapResult
}

interface MapRepository {

    suspend fun current(): MapResult
}
