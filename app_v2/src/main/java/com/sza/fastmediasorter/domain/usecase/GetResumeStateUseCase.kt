package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import javax.inject.Inject

class GetResumeStateUseCase @Inject constructor(
    private val repository: ResumeStateRepository
) {
    suspend operator fun invoke(): ResumeState? {
        return repository.getState()
    }
}
