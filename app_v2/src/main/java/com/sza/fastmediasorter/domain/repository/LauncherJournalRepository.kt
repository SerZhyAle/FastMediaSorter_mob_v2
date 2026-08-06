package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import kotlinx.coroutines.flow.Flow

/** S0404: the app's own record of what was launched through the launcher (strategic ADR-7). */
interface LauncherJournalRepository {

    suspend fun record(target: LauncherCellCommand)

    /**
     * S1097: recently launched commands of every kind (apps, features, resources, streams, OS
     * shortcuts), newest first, each distinct command listed once. Recents mirror the Windows-taskbar
     * model - everything the user opened from the launcher, not third-party apps alone.
     */
    fun recentCommands(limit: Int): Flow<List<LauncherCellCommand>>

    /** Drops the whole journal, leaving the recent strip in the state a fresh install has. */
    suspend fun clearJournal()
}
