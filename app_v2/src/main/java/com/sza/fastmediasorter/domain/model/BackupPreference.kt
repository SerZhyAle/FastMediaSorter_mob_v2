package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S3130: one stored preference of the settings store, as it travels in a backup. [type] is the
 * one-letter tag of the stored value's type - without it the string form is not convertible back,
 * because `"true"` the string and `true` the boolean are indistinguishable on import.
 *
 * The wire names are pinned because a backup file outlives the process that wrote it and R8 would
 * otherwise rename the fields between releases.
 */
data class BackupPreference(
    @SerializedName("key") val key: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("value") val value: String = ""
)
