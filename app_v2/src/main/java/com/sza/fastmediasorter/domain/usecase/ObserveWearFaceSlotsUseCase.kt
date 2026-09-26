package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WearFaceSlotAssignment
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S3558: what each watch face button is set to, live. */
class ObserveWearFaceSlotsUseCase @Inject constructor(
    private val repository: WearFaceSlotsRepository,
) {
    operator fun invoke(): Flow<WearFaceSlotAssignment> = repository.observe()
}
