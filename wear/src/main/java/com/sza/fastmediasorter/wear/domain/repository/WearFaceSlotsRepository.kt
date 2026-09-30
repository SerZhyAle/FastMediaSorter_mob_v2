package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots
import kotlinx.coroutines.flow.Flow

/**
 * S3558: the last watch-face slot choices the phone published, which the four slot providers read.
 * Emits [WearFaceSlots.DEFAULT] until the phone sends one.
 */
interface WearFaceSlotsRepository {
    val slots: Flow<WearFaceSlots>

    suspend fun save(slots: WearFaceSlots)
}
