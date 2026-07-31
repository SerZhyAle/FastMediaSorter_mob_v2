package com.sza.fastmediasorter.ui.browse.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * S1252: everything the file overflow menu needs to decide item visibility, in one object.
 * Replaces the flat 21-parameter `showFor` signature that had outgrown detekt's limits (spec §4a)
 * - the menu's growth now lands here instead of on every caller.
 */
data class BrowseFileMenuContext(
    val file: MediaFile,
    /**
     * S1233: the folder as Browse is currently showing it, so "Open in VR Cinema" can hand the
     * immersive host something its PREV/NEXT can walk. Empty keeps the old single-file launch.
     */
    val siblings: List<MediaFile> = emptyList(),
    val appSettings: AppSettings,
    val isWritable: Boolean,
    val hasDestinations: Boolean,
    val isGridMode: Boolean,
)

/**
 * S1252: the callback half of the former parameter list. Nullable callbacks keep their existing
 * meaning - a null hides the matching item exactly like before.
 */
@Suppress("LongParameterList") // Callback holder: one named slot per menu action, same as MainProgramsPanelManager.
class BrowseFileMenuActions(
    val onCopy: (MediaFile) -> Unit,
    val onMove: (MediaFile) -> Unit,
    val onRename: (MediaFile) -> Unit,
    val onDelete: (MediaFile) -> Unit,
    val onMoveUp: ((MediaFile) -> Unit)? = null,
    val onMoveDown: ((MediaFile) -> Unit)? = null,
    val onExtractArchive: ((MediaFile) -> Unit)? = null,
    val onFavorite: ((MediaFile) -> Unit)? = null,
    /** S0459 Phase 07: single «Send to..» entry point - replaces the former Share + Telegram items. */
    val onSendTo: ((MediaFile) -> Unit)? = null,
    val onInfo: ((MediaFile) -> Unit)? = null,
    val onDrawOverlay: ((MediaFile) -> Unit)? = null,
    val onSearchYoutubeMusic: ((MediaFile) -> Unit)? = null,
    val onOpenInPlayer: ((MediaFile) -> Unit)? = null,
    val onOpenInNewWindow: ((MediaFile) -> Unit)? = null,
)
