package com.sza.fastmediasorter.ui.settings.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * S1038: one row in the grouped gesture-action picker. [Header] labels a [GestureActionGroup] section;
 * [Entry] is a selectable action with its explanation. [Entry.enabled] is true for available actions
 * and reserved for later batches that surface an action but disable it (unmet permission/capability),
 * rendering it non-clickable rather than hiding it.
 *
 * S2256: the row carries a caller-supplied [Entry.actionKey] rather than one surface's action enum, so
 * the edge-gesture slots and the launcher desktop swipes render through the same dialog with their own
 * action types. Selection and click both compare keys, so a host gets back exactly what it put in.
 */
sealed class GesturePickerRow<out T : Any> {

    data class Header(@StringRes val titleRes: Int) : GesturePickerRow<Nothing>()

    data class Entry<out T : Any>(
        val actionKey: T,
        @StringRes val labelRes: Int,
        @StringRes val explanationRes: Int,
        @DrawableRes val iconRes: Int,
        val enabled: Boolean,
    ) : GesturePickerRow<T>()
}
