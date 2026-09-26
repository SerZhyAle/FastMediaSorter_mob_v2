package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotAssignment
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import kotlinx.coroutines.flow.Flow

/** S3558: the phone's record of what each watch face button is set to. */
interface WearFaceSlotsRepository {

    /** Emits the current assignment and every later change; a slot never chosen reads as its default. */
    fun observe(): Flow<WearFaceSlotAssignment>

    suspend fun set(slot: WearFaceSlot, option: WearFaceSlotOption)
}
