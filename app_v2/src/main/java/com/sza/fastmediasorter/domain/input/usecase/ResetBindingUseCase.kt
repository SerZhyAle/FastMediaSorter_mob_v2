package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import timber.log.Timber
import javax.inject.Inject

class ResetBindingUseCase @Inject constructor(
    private val repo: InputBindingRepository
) {
    /** Clears all overrides for [commandId] across every device. */
    suspend operator fun invoke(commandId: String) {
        Timber.d("Reset binding: commandId=%s", commandId)
        repo.clearAllOverrides(commandId)
    }
}
