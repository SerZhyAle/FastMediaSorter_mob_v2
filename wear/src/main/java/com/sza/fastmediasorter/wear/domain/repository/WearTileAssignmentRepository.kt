package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef

/**
 * S1955: stores which target each tile kind is assigned to.
 *
 * Stored per kind rather than per tile instance (strategic ADR-3).
 */
interface WearTileAssignmentRepository {
    suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef?
    suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef)
}
