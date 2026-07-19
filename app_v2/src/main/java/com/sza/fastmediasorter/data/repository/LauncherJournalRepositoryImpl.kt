package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherJournalDao
import com.sza.fastmediasorter.data.local.db.LauncherJournalEntity
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
) : LauncherJournalRepository {

    override suspend fun record(target: LauncherCellCommand) {
        withContext(Dispatchers.IO) {
            dao.insert(
                LauncherJournalEntity(target = target.encode(), launchedAt = System.currentTimeMillis())
            )
            dao.trim(MAX_JOURNAL_ROWS)
        }
    }

    override fun recentCommands(limit: Int): Flow<List<LauncherCellCommand>> =
        dao.recent(RECENT_QUERY_ROWS)
            .map { rows ->
                rows.asSequence()
                    .mapNotNull { LauncherCellCommand.decode(it.target) }
                    // Dedup by the encoded target so relaunching the same thing lifts it to the front
                    // instead of listing it twice.
                    .distinctBy { it.encode() }
                    .take(limit)
                    .toList()
            }
            .distinctUntilChanged()

    private companion object {
        const val MAX_JOURNAL_ROWS = 50

        // Read the whole retained journal: relaunches of the same command collapse on dedup, so a
        // query capped at `limit` could return fewer than `limit` distinct commands.
        const val RECENT_QUERY_ROWS = MAX_JOURNAL_ROWS
    }
}
