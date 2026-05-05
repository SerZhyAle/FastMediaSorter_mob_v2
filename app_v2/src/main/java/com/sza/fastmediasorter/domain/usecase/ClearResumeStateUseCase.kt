package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import javax.inject.Inject

class ClearResumeStateUseCase @Inject constructor(
    private val repository: ResumeStateRepository
) {
    suspend operator fun invoke(windowId: String) {
        repository.clearState(windowId)
    }
}
