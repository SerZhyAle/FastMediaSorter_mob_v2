package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Device-profile presets may enable the global `allFiles` shortcut as a starting point.
 * When the user manually turns off any specific media-type toggle afterwards, we treat that
 * interaction as an explicit exit from the shortcut so the manual override takes effect.
 */
fun AppSettings.exitAllFilesForManualSupportToggle(isChecked: Boolean): AppSettings {
    return if (allFiles && !isChecked) copy(allFiles = false) else this
}
