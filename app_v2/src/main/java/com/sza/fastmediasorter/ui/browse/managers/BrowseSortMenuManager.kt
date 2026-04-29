package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.utils.UserActionLogger

/**
 * Manages sort mode popup menu and sort mode display names for BrowseActivity.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition — IV.1).
 */
class BrowseSortMenuManager(
    private val context: Context,
    private val onSortModeSelected: (SortMode) -> Unit,
    // Called when user picks RANDOM regardless of current sort mode — always reshuffles.
    private val onRandomReshuffle: () -> Unit,
    private val getCurrentSortMode: () -> SortMode,
    private val getCurrentResource: () -> MediaResource?
) {
    companion object {
        fun getSortModeIconRes(mode: SortMode): Int? {
            return when (mode) {
                SortMode.RANDOM -> R.drawable.ic_sort_random
                else -> null
            }
        }
    }

    /**
     * Returns sort modes relevant for the given resource's media type configuration.
     * Metadata-specific modes (artist, title, duration, dateTaken) are filtered
     * to only appear when the resource actually supports the related media types.
     */
    fun getRelevantSortModes(resource: MediaResource?): Array<SortMode> {
        val types = resource?.supportedMediaTypes ?: emptySet()
        val showAll = resource?.allFiles ?: true
        return SortMode.values().filter { mode ->
            when (mode) {
                SortMode.ARTIST_ASC, SortMode.ARTIST_DESC,
                SortMode.TITLE_ASC, SortMode.TITLE_DESC ->
                    showAll || MediaType.AUDIO in types
                SortMode.DURATION_ASC, SortMode.DURATION_DESC ->
                    showAll || MediaType.AUDIO in types || MediaType.VIDEO in types
                SortMode.DATE_TAKEN_ASC, SortMode.DATE_TAKEN_DESC ->
                    showAll || MediaType.IMAGE in types || MediaType.GIF in types
                else -> true
            }
        }.toTypedArray()
    }

    fun showSortPopupMenu(anchor: android.view.View) {
        val popup = PopupMenu(context, anchor)
        val resource = getCurrentResource()
        val sortModes = getRelevantSortModes(resource)

        sortModes.forEachIndexed { index, mode ->
            popup.menu.add(0, index, index, getSortModeName(mode))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedMode = sortModes[menuItem.itemId]
            UserActionLogger.logButtonClick("SortMenu_${selectedMode.name}", "BrowseActivity")
            if (selectedMode == SortMode.RANDOM) {
                // Always reshuffle when RANDOM is tapped, even if already active.
                onRandomReshuffle()
            } else if (selectedMode != getCurrentSortMode()) {
                onSortModeSelected(selectedMode)
            }
            true
        }

        popup.show()
    }

    fun getSortModeShortName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> "Manual"
            SortMode.NAME_ASC -> "A-Z"
            SortMode.NAME_DESC -> "Z-A"
            SortMode.DATE_ASC -> "Old ↑"
            SortMode.DATE_DESC -> "New ↓"
            SortMode.SIZE_ASC -> "Size ↑"
            SortMode.SIZE_DESC -> "Size ↓"
            SortMode.TYPE_ASC -> "Type ↑"
            SortMode.TYPE_DESC -> "Type ↓"
            SortMode.ARTIST_ASC -> "Artist ↑"
            SortMode.ARTIST_DESC -> "Artist ↓"
            SortMode.TITLE_ASC -> "Title ↑"
            SortMode.TITLE_DESC -> "Title ↓"
            SortMode.DURATION_ASC -> "Duration ↑"
            SortMode.DURATION_DESC -> "Duration ↓"
            SortMode.DATE_TAKEN_ASC -> "DateTaken ↑"
            SortMode.DATE_TAKEN_DESC -> "DateTaken ↓"
            SortMode.RANDOM -> "Random"
        }
    }

    fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> context.getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> context.getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> context.getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> context.getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> context.getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> context.getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> context.getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> context.getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> context.getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> context.getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> context.getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> context.getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> context.getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> context.getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> context.getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> context.getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> context.getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> context.getString(R.string.sort_mode_random)
        }
    }
}
