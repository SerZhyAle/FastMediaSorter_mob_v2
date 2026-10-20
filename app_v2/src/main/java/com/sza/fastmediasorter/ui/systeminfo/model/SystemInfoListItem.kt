package com.sza.fastmediasorter.ui.systeminfo.model

import androidx.annotation.DrawableRes

/**
 * Items displayed in the System Information list.
 */
sealed interface SystemInfoListItem {
    /**
     * [stateKey] identifies the group independently of the localized [title]; [iconRes] is null for a
     * group without a known icon.
     */
    data class SectionHeader(
        val title: String,
        val stateKey: String,
        val count: Int,
        val isExpanded: Boolean,
        @DrawableRes val iconRes: Int?,
    ) : SystemInfoListItem

    /** [isShaded] marks every second row of a group so adjacent rows do not read as one block. */
    data class Field(
        val sectionKey: String,
        val label: String,
        val value: String,
        val isShaded: Boolean,
    ) : SystemInfoListItem

    data class EmptyState(
        val message: String,
    ) : SystemInfoListItem
}
