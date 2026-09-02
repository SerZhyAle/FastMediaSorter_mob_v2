package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MainListSession
import com.sza.fastmediasorter.domain.repository.MainListSessionRepository
import javax.inject.Inject

/** S2199: remembers the sort and filters the resource list ended up in. */
class SaveMainListSessionUseCase @Inject constructor(
    private val repository: MainListSessionRepository
) {
    suspend operator fun invoke(session: MainListSession) = repository.write(session)
}
