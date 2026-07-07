package com.sza.fastmediasorter.ui.browse.helpers

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner.PlayerCommand
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class BrowseFileOverflowMenuManager @Inject constructor(
    @ActivityContext private val context: Context,
    // S0962 (VR Cinema, Столп 1): self-sourced gate + cold-launch for the "Open in VR Cinema" item.
    private val vrCinemaLaunchManager: BrowseVrCinemaLaunchManager,
) {
    private data class MenuItem(val label: String, val action: () -> Unit)

    fun showFor(
        anchor: View,
        file: MediaFile,
        appSettings: AppSettings,
        isWritable: Boolean,
        hasDestinations: Boolean,
        isGridMode: Boolean,
        onCopy: (MediaFile) -> Unit,
        onMove: (MediaFile) -> Unit,
        onRename: (MediaFile) -> Unit,
        onDelete: (MediaFile) -> Unit,
        onMoveUp: ((MediaFile) -> Unit)? = null,
        onMoveDown: ((MediaFile) -> Unit)? = null,
        onExtractArchive: ((MediaFile) -> Unit)? = null,
        onFavorite: ((MediaFile) -> Unit)? = null,
        // S0459 Phase 07: single «Send to..» entry point - replaces the former Share + Telegram items.
        onSendTo: ((MediaFile) -> Unit)? = null,
        onInfo: ((MediaFile) -> Unit)? = null,
        onDrawOverlay: ((MediaFile) -> Unit)? = null,
        onSearchYoutubeMusic: ((MediaFile) -> Unit)? = null,
        onOpenInPlayer: ((MediaFile) -> Unit)? = null,
        onOpenInNewWindow: ((MediaFile) -> Unit)? = null,
    ) {
        val items = mutableListOf<MenuItem>()

        // "Open" (= play) first: lets the user open a playable media file straight from this
        // menu when the tap landed on the row's overflow button instead of the card itself.
        // Folders and binary files have their own open semantics and are excluded.
        if (!file.isDirectory && !file.type.isBinaryFile() && onOpenInPlayer != null) {
            items += MenuItem(context.getString(R.string.action_open)) { onOpenInPlayer(file) }
        }

        // S0962: "Open in VR Cinema" - video files only, gated on runtime VR availability
        // (device XR-capable AND master toggle on). Hidden on non-VR flavors via the No-Op facade.
        if (file.type == MediaType.VIDEO && vrCinemaLaunchManager.isAvailable) {
            items += MenuItem(context.getString(R.string.action_open_in_vr_cinema)) {
                vrCinemaLaunchManager.launch(file)
            }
        }

        // Basic ops - same gates as the direct buttons
        if (hasDestinations && appSettings.enableCopying)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.copy)) { onCopy(file) }
        if (isWritable && appSettings.enableMoving)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.move)) { onMove(file) }
        if (isWritable && appSettings.allowRename)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.rename)) { onRename(file) }
        if (isWritable && appSettings.allowDelete)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.delete)) { onDelete(file) }

        // Grid move-up/move-down - only in grid mode with manual order callbacks
        if (isGridMode && onMoveUp != null)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.move_up)) { onMoveUp(file) }
        if (isGridMode && onMoveDown != null)
            items += MenuItem(context.getString(com.sza.fastmediasorter.R.string.move_down)) { onMoveDown(file) }
        if (isZipArchive(file) && onExtractArchive != null) {
            items += MenuItem(context.getString(R.string.unarchive_action_extract)) { onExtractArchive(file) }
        }

        if (appSettings.allowSeparateWindow && onOpenInNewWindow != null) {
            items += MenuItem(context.getString(R.string.action_open_in_separate_window)) {
                onOpenInNewWindow(file)
            }
        }

        // Dynamic extended commands - full player-panel matrix for this file type,
        // minus player-only and basic-group commands (see buildExtendedCommands).
        val extendedCommands = buildExtendedCommands(file, appSettings, isWritable)
        for (cmd in extendedCommands) {
            val action: () -> Unit = when (cmd) {
                PlayerCommand.FAVORITE          -> { { onFavorite?.invoke(file) } }
                PlayerCommand.SEND_TO           -> { { onSendTo?.invoke(file) } }
                PlayerCommand.INFO              -> { { onInfo?.invoke(file) } }
                PlayerCommand.DRAW_OVERLAY      -> { { onDrawOverlay?.invoke(file) } }
                PlayerCommand.SEARCH_YOUTUBE_MUSIC -> { { onSearchYoutubeMusic?.invoke(file) } }
                else                            -> { { onOpenInPlayer?.invoke(file) } }
            }
            items += MenuItem(context.getString(cmd.titleResId), action)
        }

        if (items.isEmpty()) return

        val labels = items.map { it.label }
        val popup = ListPopupWindow(context)
        popup.setAnchorView(anchor)
        popup.setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, labels))
        popup.width = measurePopupWidth(labels)
        popup.height = ListPopupWindow.WRAP_CONTENT
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            items[position].action()
            popup.dismiss()
        }
        popup.show()
    }

    /**
     * Measure popup width to fit the widest label, capped at 2/3 of screen width.
     * Adds horizontal padding to account for list item insets.
     */
    private fun measurePopupWidth(labels: List<String>): Int {
        val dm = context.resources.displayMetrics
        val maxWidth = dm.widthPixels * 2 / 3
        val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, dm)
        val paint = android.graphics.Paint().apply { textSize = textSizePx }
        val measured = labels.maxOfOrNull { paint.measureText(it) }?.toInt() ?: 0
        val padding = (48 * dm.density).toInt()
        return minOf(measured + padding, maxWidth)
    }

    /**
     * Build the subset of player-panel commands applicable to a Browse-context file row,
     * using only [MediaFile] type and [AppSettings] flags (no PlayerState required).
     *
     * Excluded (player-only, meaningless without an open player session):
     * FULLSCREEN, RANDOM, BLACK_SCREEN, UNDO, CAST, SLEEP_TIMER,
     * ROTATION_TOGGLE, OPEN_IN_SEPARATE_WINDOW, SLIDESHOW.
     * Also excluded: DELETE and RENAME - already in the basic group above.
     *
     * Non-player actions (FAVORITE, SEND_TO, INFO, DRAW_OVERLAY,
     * SEARCH_YOUTUBE_MUSIC) are routed to their own callbacks;
     * all other extended commands route to [onOpenInPlayer] which opens the file
     * in PlayerActivity where the feature is accessible.
     */
    private fun buildExtendedCommands(
        file: MediaFile,
        appSettings: AppSettings,
        isWritable: Boolean,
    ): List<PlayerCommand> {
        val isFolder = file.isDirectory
        val isImage = file.type == MediaType.IMAGE || file.type == MediaType.GIF
        val isAudio = file.type == MediaType.AUDIO
        val isVideo = file.type == MediaType.VIDEO
        val isPdf = file.type == MediaType.PDF
        val isText = file.type == MediaType.TEXT
        val isEpub = file.type == MediaType.EPUB
        val nameLower = file.name.lowercase()
        val isStaticBitmap = isImage && !nameLower.endsWith(".gif") && !nameLower.endsWith(".apng")

        return buildList {
            // Universal - available for every file type
            if (!isFolder) add(PlayerCommand.FAVORITE)
            // S0459 Phase 07: unified «Send to..» replaces the per-file Share + Telegram items.
            // Receiver applicability (incl. Telegram-installed gating) is resolved by SendToMenuManager.
            add(PlayerCommand.SEND_TO)
            add(PlayerCommand.INFO)

            // Audio-specific
            if (isAudio) add(PlayerCommand.LYRICS)
            if (isAudio) add(PlayerCommand.SEARCH_YOUTUBE_MUSIC)

            // Edit: writable images, video (non-audio), PDF
            if ((isImage && isWritable) || isVideo || isPdf) add(PlayerCommand.EDIT)

            // PDF commands
            if (isPdf) add(PlayerCommand.SEARCH_PDF)
            if (isPdf && appSettings.enableTranslation) add(PlayerCommand.TRANSLATE_PDF)
            if (isPdf) add(PlayerCommand.PDF_TEXT_SETTINGS)
            if (isPdf && appSettings.enableOcr) add(PlayerCommand.OCR_PDF)
            // GOOGLE_LENS_PDF omitted: requires a rendered page bitmap from the player - not usable from Browse.

            // Text commands
            if (isText) add(PlayerCommand.SEARCH_TEXT)
            if (isText && isWritable) add(PlayerCommand.EDIT_TEXT)
            if (isText && appSettings.enableTranslation) add(PlayerCommand.TRANSLATE_TEXT)
            if (isText) add(PlayerCommand.TEXT_SETTINGS)
            if (isText || isPdf) add(PlayerCommand.COPY_TEXT)

            // EPUB commands
            if (isEpub) add(PlayerCommand.SEARCH_EPUB)
            if (isEpub && appSettings.enableTranslation) add(PlayerCommand.TRANSLATE_EPUB)
            if (isEpub) add(PlayerCommand.EPUB_TEXT_SETTINGS)
            if (isEpub && appSettings.enableOcr) add(PlayerCommand.OCR_EPUB)

            // Image commands
            if (isImage && appSettings.enableTranslation) add(PlayerCommand.TRANSLATE_IMAGE)
            if (isImage) add(PlayerCommand.IMAGE_TEXT_SETTINGS)
            if (isImage && appSettings.enableOcr) add(PlayerCommand.OCR_IMAGE)
            // S0459 Phase 08 (§11.7 audit): no per-file Google Lens item here - Lens is surfaced
            // through the unified «Send to..» (SEND_TO) entry, which the "lens" receiver registers for
            // image/gif files. A browse-local ACTION_SEND would duplicate that registered handler.

            // Print: PDF, text, image
            if (isPdf || isText || isImage) add(PlayerCommand.PRINT)

            // Video: save frame (requires write access)
            if (isVideo && isWritable) add(PlayerCommand.SAVE_FRAME)

            // Static image manipulation
            if (isStaticBitmap) {
                if (isWritable) add(PlayerCommand.CROP)
                add(PlayerCommand.CROP_TO_FILE)
                add(PlayerCommand.COMPRESS_COPY)
                add(PlayerCommand.DRAW_OVERLAY)
            }
        }
    }

    private fun isZipArchive(file: MediaFile): Boolean {
        return file.type == MediaType.BINARY_ARCHIVE &&
            file.name.substringAfterLast('.', "").equals("zip", ignoreCase = true)
    }
}
