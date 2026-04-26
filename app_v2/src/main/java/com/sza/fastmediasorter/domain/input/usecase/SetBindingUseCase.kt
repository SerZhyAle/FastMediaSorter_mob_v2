package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.InputTrigger
import timber.log.Timber
import javax.inject.Inject

class SetBindingUseCase @Inject constructor(
    private val repo: InputBindingRepository
) {
    suspend operator fun invoke(
        commandId: String,
        device: String,
        slot: Int,
        trigger: InputTrigger
    ) {
        Timber.d("Set binding: commandId=%s device=%s slot=%d trigger=%s", commandId, device, slot, trigger.serialize())
        repo.setOverride(commandId, device, slot, trigger)
    }
}
