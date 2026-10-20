package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.ScheduledRunJournalRepository
import javax.inject.Inject

class ClearScheduledOperationsUseCase @Inject constructor(
    private val repository: ScheduledOperationRepository,
    private val runJournal: ScheduledRunJournalRepository,
) {
    suspend operator fun invoke() {
        repository.deleteAll()
        runJournal.clearAll()
    }
}
