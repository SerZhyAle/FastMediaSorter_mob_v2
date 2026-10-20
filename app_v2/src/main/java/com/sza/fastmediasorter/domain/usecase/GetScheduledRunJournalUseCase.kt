package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import com.sza.fastmediasorter.domain.repository.ScheduledRunJournalRepository
import javax.inject.Inject

/** Run journal of one schedule, newest run first. */
class GetScheduledRunJournalUseCase @Inject constructor(
    private val journal: ScheduledRunJournalRepository
) {
    suspend operator fun invoke(operationId: Long): List<ScheduledRunRecord> = journal.read(operationId)
}
