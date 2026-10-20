package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.ScheduledRunJournalRepository
import javax.inject.Inject

class DeleteScheduledOperationUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository,
    private val runJournal: ScheduledRunJournalRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteById(id)
        // The journal belongs to the schedule; a recycled id must not inherit a dead one's history.
        runJournal.clear(id)
    }
}
