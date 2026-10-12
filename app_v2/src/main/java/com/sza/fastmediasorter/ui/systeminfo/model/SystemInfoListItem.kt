package com.sza.fastmediasorter.ui.systeminfo.model

/**
 * Items displayed in the System Information list.
 */
sealed interface SystemInfoListItem {
    data class SectionHeader(
        val title: String,
        val count: Int,
        val isExpanded: Boolean,
    ) : SystemInfoListItem

    data class Field(
        val sectionTitle: String,
        val label: String,
        val value: String,
    ) : SystemInfoListItem

    data class EmptyState(
        val message: String,
    ) : SystemInfoListItem
}
