package com.sza.fastmediasorter.domain.model.launcher

import java.io.File

/**
 * S1401: one launchable installed app as the cache knows it.
 *
 * The icon travels as a [File], never as a decoded image: a surface listing a hundred apps would
 * otherwise hold a hundred bitmaps alive whether or not any of them is on screen, which is the cost
 * the cache exists to remove. Whoever draws the icon decides when to decode it.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val category: Int,
    val isSystemApp: Boolean,
    val iconFile: File?
)

/**
 * S1401: the orders the all-apps screen offers, signed off by the owner on 2026-08-05.
 *
 * App size and system-wide usage statistics are deliberately absent - both need the "Usage access"
 * permission the user grants by hand, which this feature does not ask for.
 */
enum class InstalledAppSortOrder {
    LABEL,
    INSTALL_DATE,
    UPDATE_DATE,

    /** Counted from the launcher's own journal, so it stays empty until the user launches from here. */
    LAUNCH_FREQUENCY,

    /** Declared by the publisher and often left unset; unset apps group together rather than scatter. */
    CATEGORY;

    companion object {
        /** Tolerant read: an unknown or missing stored name falls back to the default order. */
        fun fromNameOrDefault(name: String?): InstalledAppSortOrder =
            entries.firstOrNull { it.name == name } ?: LABEL
    }
}
