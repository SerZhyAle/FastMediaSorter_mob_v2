package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherJournalDao
import com.sza.fastmediasorter.data.local.db.LauncherJournalEntity
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsDao
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LauncherJournalRepositoryImpl @Inject constructor(
    private val dao: LauncherJournalDao,
    private val statsDao: LauncherLaunchStatsDao,
) : LauncherJournalRepository {

    override suspend fun record(target: LauncherCellCommand) {
        withContext(Dispatchers.IO) {
            val encoded = target.encode()
            val launchedAt = System.currentTimeMillis()
            dao.insert(LauncherJournalEntity(target = encoded, launchedAt = launchedAt))
            dao.trim(LauncherJournalRepository.MAX_RECENT_PROGRAMS)
            // S1401: the counter lives outside the trimmed journal, so the "most used" order keeps
            // counting for programs the journal has already let go of.
            statsDao.recordLaunch(encoded, launchedAt)
        }
    }

    override fun recentCommands(limit: Int): Flow<List<LauncherCellCommand>> =
        dao.recent(LauncherJournalRepository.MAX_RECENT_PROGRAMS)
            .map { rows ->
                rows.asSequence()
                    .mapNotNull { LauncherCellCommand.decode(it.target) }
                    // Rows are unique by target since S3836; two targets can still decode to one
                    // command after a codec change, which must not list it twice.
                    .distinctBy { it.encode() }
                    .take(limit)
                    .toList()
            }
            .distinctUntilChanged()

    override suspend fun remove(target: LauncherCellCommand) {
        withContext(Dispatchers.IO) {
            dao.deleteByTarget(target.encode())
        }
    }

    override suspend fun clearJournal() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }
}
