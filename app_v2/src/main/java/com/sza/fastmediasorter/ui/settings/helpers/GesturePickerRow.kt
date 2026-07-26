package com.sza.fastmediasorter.ui.settings.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * S1038: one row in the grouped gesture-action picker. [Header] labels a [GestureActionGroup] section;
 * [Entry] is a selectable action with its explanation. [Entry.enabled] is true for available actions
 * and reserved for later batches that surface an action but disable it (unmet permission/capability),
 * rendering it non-clickable rather than hiding it.
 */
sealed class GesturePickerRow {

    data class Header(@StringRes val titleRes: Int) : GesturePickerRow()

    data class Entry(
        val action: ScreenshotGestureAction,
        @StringRes val labelRes: Int,
        @StringRes val explanationRes: Int,
        @DrawableRes val iconRes: Int,
        val enabled: Boolean,
    ) : GesturePickerRow()
}
