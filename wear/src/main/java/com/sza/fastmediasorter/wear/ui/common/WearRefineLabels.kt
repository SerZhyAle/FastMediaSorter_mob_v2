package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * S2136: what a sort order and a content type are called in the refine dialogs.
 *
 * One home for both maps because every content screen offers the same vocabulary, and two copies is
 * how two screens start naming the same order differently - the defect S2130 recorded when a chip
 * and the screen it opened were labelled from separate key sets.
 *
 * Both are exhaustive over their enum, so an order or a type added later fails to compile here
 * rather than reaching a dialog as a blank row. Which of these a given screen actually offers is a
 * separate question, answered per list by `BrowseRefineKeys.availableSortOrders` and by the types
 * present in the loaded page - never by leaving a label out.
 */
@StringRes
fun labelForSortOrder(order: BrowseSortOrder): Int = when (order) {
    BrowseSortOrder.DEFAULT -> R.string.wear_browse_sort_default
    BrowseSortOrder.NAME_ASC -> R.string.wear_browse_sort_name_asc
    BrowseSortOrder.NAME_DESC -> R.string.wear_browse_sort_name_desc
    BrowseSortOrder.DATE_ASC -> R.string.wear_browse_sort_date_asc
    BrowseSortOrder.DATE_DESC -> R.string.wear_browse_sort_date_desc
    BrowseSortOrder.SIZE_ASC -> R.string.wear_browse_sort_size_asc
    BrowseSortOrder.SIZE_DESC -> R.string.wear_browse_sort_size_desc
}

/**
 * The type's name for the filter dialog.
 *
 * Streams and the umbrella "other" share one label: neither names a kind of file a folder listing
 * distinguishes, and offering two rows that filter the same set would be a choice without a
 * difference.
 */
@StringRes
fun labelForContentType(type: WearContentType): Int = when (type) {
    WearContentType.MUSIC -> R.string.wear_content_type_music
    WearContentType.VIDEO -> R.string.wear_content_type_video
    WearContentType.IMAGE -> R.string.wear_content_type_image
    WearContentType.DOCUMENT -> R.string.wear_content_type_document
    WearContentType.FOLDER -> R.string.wear_content_type_folder
    WearContentType.STREAM,
    WearContentType.OTHER -> R.string.wear_content_type_other
}
