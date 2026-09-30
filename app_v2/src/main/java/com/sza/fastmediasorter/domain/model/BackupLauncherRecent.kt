package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/** S3836: one launcher recents entry - its encoded command, latest launch and launch count. */
data class BackupLauncherRecent(
    @SerializedName("target")
    val target: String = "",
    @SerializedName("lastLaunchedAt")
    val lastLaunchedAt: Long = 0L,
    @SerializedName("launchCount")
    val launchCount: Int = 0,
)
