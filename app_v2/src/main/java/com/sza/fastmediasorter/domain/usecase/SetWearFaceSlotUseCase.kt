package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
import javax.inject.Inject

/**
 * S3558: points one watch face button at an option. Only the record is written here - reaching the
 * watch is [PushWearFaceSlotsUseCase]'s job, which follows the record on its own.
 */
class SetWearFaceSlotUseCase @Inject constructor(
    private val repository: WearFaceSlotsRepository,
) {
    suspend operator fun invoke(slot: WearFaceSlot, option: WearFaceSlotOption) = repository.set(slot, option)
}
