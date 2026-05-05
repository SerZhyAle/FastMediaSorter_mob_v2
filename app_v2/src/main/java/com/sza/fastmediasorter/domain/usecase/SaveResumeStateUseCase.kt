package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import javax.inject.Inject

class SaveResumeStateUseCase @Inject constructor(
    private val repository: ResumeStateRepository
) {
    suspend operator fun invoke(windowId: String, state: ResumeState) {
        repository.saveState(windowId, state)
    }
}
