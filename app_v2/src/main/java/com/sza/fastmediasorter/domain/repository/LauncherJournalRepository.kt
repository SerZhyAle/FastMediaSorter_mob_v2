package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import kotlinx.coroutines.flow.Flow

/** S0404: the app's own record of what was launched through the launcher (strategic ADR-7). */
interface LauncherJournalRepository {

    suspend fun record(target: LauncherCellCommand)

    /**
     * S1097: recently launched commands of every kind (apps, features, resources, streams, OS
     * shortcuts), newest first. S3836: the journal stores one row per command, so each is listed
     * once. Recents mirror the Windows-taskbar
     * model - everything the user opened from the launcher, not third-party apps alone.
     */
    fun recentCommands(limit: Int): Flow<List<LauncherCellCommand>>

    /** Removes one command from the recents history without affecting the command itself. */
    suspend fun remove(target: LauncherCellCommand)

    /** Drops the whole journal, leaving the recent strip in the state a fresh install has. */
    suspend fun clearJournal()

    companion object {
        /** S3836: how many distinct programs the journal keeps and the recents strip scrolls through. */
        const val MAX_RECENT_PROGRAMS = 200
    }
}
