package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MainListSession
import com.sza.fastmediasorter.domain.repository.MainListSessionRepository
import javax.inject.Inject

/** S2199: the sort and filters the resource list was last left in, or nothing remembered. */
class ReadMainListSessionUseCase @Inject constructor(
    private val repository: MainListSessionRepository
) {
    suspend operator fun invoke(): MainListSession = repository.read()
}
