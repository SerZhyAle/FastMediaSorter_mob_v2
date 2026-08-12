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
        Section(LauncherCellCommand.SECTION_APP_FUNCTIONS, R.string.launcher_section_app_functions),
        Section(LauncherCellCommand.SECTION_EVERYTHING_ELSE, R.string.launcher_section_everything_else),
    )

    fun byKey(key: String): Section? = all.firstOrNull { it.key == key }
}
