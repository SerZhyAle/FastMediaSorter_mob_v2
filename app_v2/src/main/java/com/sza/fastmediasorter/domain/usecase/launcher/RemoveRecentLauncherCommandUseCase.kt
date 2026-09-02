package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import javax.inject.Inject

/** Removes a command from the launcher's own recents history. */
class RemoveRecentLauncherCommandUseCase @Inject constructor(
    private val journal: LauncherJournalRepository,
) {

    suspend operator fun invoke(command: LauncherCellCommand) {
        journal.remove(command)
    }
}
