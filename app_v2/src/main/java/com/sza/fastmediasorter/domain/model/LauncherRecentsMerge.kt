package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.data.local.db.LauncherJournalEntity
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsEntity

/** S3836: the journal rows and counters a restore writes; both lists exclude what is already current. */
data class LauncherRecentsMerge(
    val journal: List<LauncherJournalEntity>,
    val stats: List<LauncherLaunchStatsEntity>,
)
