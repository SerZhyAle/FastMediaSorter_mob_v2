package com.sza.fastmediasorter.domain.model

/** S3836: one launcher recents entry - its encoded command, latest launch and launch count. */
data class BackupLauncherRecent(
    val target: String = "",
    val lastLaunchedAt: Long = 0L,
    val launchCount: Int = 0,
)
