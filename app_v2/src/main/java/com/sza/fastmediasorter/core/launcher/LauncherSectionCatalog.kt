package com.sza.fastmediasorter.core.launcher

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand

/**
 * S1428: the preset section headers as data, in the order a fresh desktop seeds them.
 *
 * Strategic §5.3 requires a future user-created group to be a second entry rather than a code change,
 * which holds only while a section's title is looked up through its key. Both the label resolver and the
 * content picker read this one table, so a third section is one line here and nothing else.
 *
 * Modelled on [LauncherActionCatalog][com.sza.fastmediasorter.core.panel.LauncherActionCatalog], which
 * the picker already consumes the same way.
 */
object LauncherSectionCatalog {

    data class Section(val key: String, @StringRes val labelRes: Int)

    val all: List<Section> = listOf(
        Section(LauncherCellCommand.SECTION_WIDGETS, R.string.launcher_section_widgets),
        Section(LauncherCellCommand.SECTION_RESOURCES, R.string.launcher_section_resources),
        Section(LauncherCellCommand.SECTION_APP_FUNCTIONS, R.string.launcher_section_app_functions),
        Section(LauncherCellCommand.SECTION_ANDROID_APPS, R.string.launcher_section_android_apps),
        Section(LauncherCellCommand.SECTION_MAIN, R.string.launcher_section_main),
        Section(LauncherCellCommand.SECTION_GOOGLE, R.string.launcher_section_google),
        Section(LauncherCellCommand.SECTION_DESKTOP, R.string.launcher_section_desktop),
    )

    fun byKey(key: String): Section? = all.firstOrNull { it.key == key }

    /**
     * S1742: what marks a section the user made, rather than one this file lists.
     *
     * A prefix rather than a registry, because a section's whole identity is the `target` string of its
     * cell: the settings backup carries that string verbatim and resets everything else (research 01
     * item 1), so anything a key needs to say it must say by itself. No preset key above starts with
     * this, which is what makes a collision impossible rather than unlikely.
     */
    const val USER_KEY_PREFIX = "user-"

    /**
     * Mints the key of a newly created section from the moment it was created.
     *
     * The timestamp is an identity, never a displayed value - the user's chosen name lives in the cell's
     * label override. Two sections created in the same millisecond on one desktop is not a case the user
     * can produce by hand, and the placement path would refuse the second one anyway.
     */
    fun mintUserKey(createdAtMillis: Long): String = "$USER_KEY_PREFIX$createdAtMillis"

    fun isUserKey(key: String): Boolean = key.startsWith(USER_KEY_PREFIX)
}
