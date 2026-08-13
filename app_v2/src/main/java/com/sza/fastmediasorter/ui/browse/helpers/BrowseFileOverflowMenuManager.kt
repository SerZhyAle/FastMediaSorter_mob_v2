package com.sza.fastmediasorter.ui.browse.helpers

import android.content.Context
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner.PlayerCommand
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject

/**
 * S1252: the per-row file overflow menu, grouped. The former flat `ListPopupWindow` list cleared
 * 20 visible items on a writable PDF/image; a native `PopupMenu` with `SubMenu` groups (the S0459
 * ADR-2 pattern the player's «Send to..» already uses) keeps six direct actions on top and folds
 * the rest into Organize / Text / Editing / Share-and-info. Framework menus own sizing, D-pad
 * traversal and submenu back-behaviour, which the hand-rolled popup never had (Rule 16).
 *
 * Item gating is unchanged from the flat menu - only placement moved.
 */
@ActivityScoped
class BrowseFileOverflowMenuManager @Inject constructor(
    @ActivityContext private val context: Context,
    // S0962 (VR Cinema, Столп 1): self-sourced gate + cold-launch for the "Open in VR Cinema" item.
    private val vrCinemaLaunchManager: BrowseVrCinemaLaunchManager,
) {
    private data class MenuEntry(val label: String, val action: () -> Unit)

    /** Build order mirrors the menu: direct entries first, then the four groups. */
    private class GroupedEntries {
        val topLevel = mutableListOf<MenuEntry>()
        val organize = mutableListOf<MenuEntry>()
        val text = mutableListOf<MenuEntry>()
        val edit = mutableListOf<MenuEntry>()
        val share = mutableListOf<MenuEntry>()

        fun isEmpty(): Boolean =
            topLevel.isEmpty() && organize.isEmpty() && text.isEmpty() && edit.isEmpty() && share.isEmpty()
    }

    fun showFor(anchor: View, menuContext: BrowseFileMenuContext, actions: BrowseFileMenuActions) {
        val entries = GroupedEntries()
        addDirectEntries(entries, menuContext, actions)
        addExtendedEntries(entries, menuContext, actions)
        if (entries.isEmpty()) return
        val groupSizes = listOf(entries.topLevel, entries.organize, entries.text, entries.edit, entries.share)
        render(anchor, entries)
    }

    /** The former direct `MenuItem` block, gates verbatim; only the target list differs. */
    private fun addDirectEntries(
        entries: GroupedEntries,
        menuContext: BrowseFileMenuContext,
        actions: BrowseFileMenuActions,
    ) {
        val file = menuContext.file
        val settings = menuContext.appSettings

        // "Open" (= play) first: lets the user open a playable media file straight from this
        // menu when the tap landed on the row's overflow button instead of the card itself.
        // Folders and binary files have their own open semantics and are excluded.
        val onOpen = actions.onOpenInPlayer
        val supportsPlayer = BrowseItemOperationPolicy.supports(BrowseItemOperation.OPEN_IN_PLAYER, file)
        if (supportsPlayer && !file.type.isBinaryFile() && onOpen != null) {
            entries.topLevel += MenuEntry(context.getString(R.string.action_open)) { onOpen(file) }
        }

        // S0962: "Open in VR Cinema" - video files only, gated on runtime VR availability
        // (device XR-capable AND master toggle on). Hidden on non-VR flavors via the No-Op facade.
        if (file.type == MediaType.VIDEO && supportsPlayer && vrCinemaLaunchManager.isAvailable) {
            entries.topLevel += MenuEntry(context.getString(R.string.action_open_in_vr_cinema)) {
                vrCinemaLaunchManager.launch(file, menuContext.siblings)
            }
        }

        // Basic ops - same gates as the direct buttons.
        if (menuContext.hasDestinations && settings.enableCopying) {
            entries.topLevel += MenuEntry(context.getString(R.string.copy)) { actions.onCopy(file) }
        }
        if (menuContext.isWritable && settings.enableMoving) {
            entries.topLevel += MenuEntry(context.getString(R.string.move)) { actions.onMove(file) }
        }
        if (menuContext.isWritable && settings.allowRename) {
            entries.topLevel += MenuEntry(context.getString(R.string.rename)) { actions.onRename(file) }
        }
        if (menuContext.isWritable && settings.allowDelete) {
            entries.topLevel += MenuEntry(context.getString(R.string.delete)) { actions.onDelete(file) }
        }

        addOrganizeEntries(entries, menuContext, actions)
    }

    /** The Organize group's direct (non-PlayerCommand) entries; gates verbatim from the flat menu. */
    private fun addOrganizeEntries(
        entries: GroupedEntries,
        menuContext: BrowseFileMenuContext,
        actions: BrowseFileMenuActions,
    ) {
        val file = menuContext.file

        // Grid move-up/move-down - only in grid mode with manual order callbacks.
        val onMoveUp = actions.onMoveUp
        if (menuContext.isGridMode && onMoveUp != null) {
            entries.organize += MenuEntry(context.getString(R.string.move_up)) { onMoveUp(file) }
        }
        val onMoveDown = actions.onMoveDown
        if (menuContext.isGridMode && onMoveDown != null) {
            entries.organize += MenuEntry(context.getString(R.string.move_down)) { onMoveDown(file) }
        }
        val onExtract = actions.onExtractArchive
        val supportsExtract = BrowseItemOperationPolicy.supports(BrowseItemOperation.EXTRACT_ARCHIVE, file)
        if (supportsExtract && isZipArchive(file) && onExtract != null) {
            entries.organize += MenuEntry(context.getString(R.string.unarchive_action_extract)) {
                onExtract(file)
            }
        }
        val onNewWindow = actions.onOpenInNewWindow
        if (menuContext.appSettings.allowSeparateWindow && onNewWindow != null) {
            entries.organize += MenuEntry(context.getString(R.string.action_open_in_separate_window)) {
                onNewWindow(file)
            }
        }
    }

    /**
     * Dynamic extended commands - full player-panel matrix for this file type, minus player-only
     * and basic-group commands (see [buildExtendedCommands]). Each command keeps its own localized
     * title, so the Text group shows the right per-format entry (e.g. "Search in PDF") without a
     * second label set.
     */
    private fun addExtendedEntries(
        entries: GroupedEntries,
        menuContext: BrowseFileMenuContext,
        actions: BrowseFileMenuActions,
    ) {
        val file = menuContext.file
        val commands = buildExtendedCommands(file, menuContext.appSettings, menuContext.isWritable)
        for (cmd in commands) {
            val action: () -> Unit = when (cmd) {
                PlayerCommand.FAVORITE -> { { actions.onFavorite?.invoke(file) } }
                PlayerCommand.SEND_TO -> { { actions.onSendTo?.invoke(file) } }
                PlayerCommand.INFO -> { { actions.onInfo?.invoke(file) } }
                PlayerCommand.DRAW_OVERLAY -> { { actions.onDrawOverlay?.invoke(file) } }
                PlayerCommand.SEARCH_YOUTUBE_MUSIC -> { { actions.onSearchYoutubeMusic?.invoke(file) } }
                else -> { { actions.onOpenInPlayer?.invoke(file) } }
            }
            groupFor(entries, cmd) += MenuEntry(context.getString(cmd.titleResId), action)
        }
    }

    /** S1252 §7: which submenu a command belongs to; unknown commands stay on the top level. */
    private fun groupFor(entries: GroupedEntries, cmd: PlayerCommand): MutableList<MenuEntry> =
        when (cmd) {
            PlayerCommand.FAVORITE,
            -> entries.organize

            PlayerCommand.SEND_TO,
            PlayerCommand.INFO,
            PlayerCommand.PRINT,
            PlayerCommand.LYRICS,
            PlayerCommand.SEARCH_YOUTUBE_MUSIC,
            -> entries.share

            PlayerCommand.EDIT,
            PlayerCommand.EDIT_TEXT,
            PlayerCommand.CROP,
            PlayerCommand.CROP_TO_FILE,
            PlayerCommand.COMPRESS_COPY,
            PlayerCommand.DRAW_OVERLAY,
            PlayerCommand.SAVE_FRAME,
            -> entries.edit

            PlayerCommand.SEARCH_PDF,
            PlayerCommand.SEARCH_TEXT,
            PlayerCommand.SEARCH_EPUB,
            PlayerCommand.TRANSLATE_PDF,
            PlayerCommand.TRANSLATE_TEXT,
            PlayerCommand.TRANSLATE_EPUB,
            PlayerCommand.TRANSLATE_IMAGE,
            PlayerCommand.OCR_PDF,
            PlayerCommand.OCR_EPUB,
            PlayerCommand.OCR_IMAGE,
            PlayerCommand.PDF_TEXT_SETTINGS,
            PlayerCommand.TEXT_SETTINGS,
            PlayerCommand.EPUB_TEXT_SETTINGS,
            PlayerCommand.IMAGE_TEXT_SETTINGS,
            PlayerCommand.COPY_TEXT,
            -> entries.text

            else -> entries.topLevel
        }

    /**
     * Native menu render: top-level entries, then one `SubMenu` per non-empty group. A group with
     * a single entry hoists it to the top level - no single-child groups (§7). Submenu headers
     * carry no action id, so the click listener returns false for them and the framework opens
     * the nested level itself.
     */
    private fun render(anchor: View, entries: GroupedEntries) {
        val popup = PopupMenu(context, anchor)
        val actionsById = mutableMapOf<Int, () -> Unit>()
        var nextId = 1

        fun addEntry(target: Menu, entry: MenuEntry) {
            val id = nextId++
            target.add(Menu.NONE, id, Menu.NONE, entry.label)
            actionsById[id] = entry.action
        }

        entries.topLevel.forEach { addEntry(popup.menu, it) }
        val groups = listOf(
            R.string.browse_menu_group_organize to entries.organize,
            R.string.browse_menu_group_text to entries.text,
            R.string.browse_menu_group_edit to entries.edit,
            R.string.browse_menu_group_share_info to entries.share,
        )
        for ((labelRes, groupEntries) in groups) {
            when {
                groupEntries.isEmpty() -> Unit
                groupEntries.size == 1 -> addEntry(popup.menu, groupEntries.first())
                else -> {
                    val sub = popup.menu.addSubMenu(
                        Menu.NONE,
                        Menu.NONE,
                        Menu.NONE,
                        context.getString(labelRes),
                    )
                    groupEntries.forEach { addEntry(sub, it) }
                }
            }
        }
        popup.setOnMenuItemClickListener { item ->
            val action = actionsById[item.itemId]
            action?.invoke()
            action != null
        }
        popup.show()
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
     * all other extended commands route to `onOpenInPlayer` which opens the file
     * in PlayerActivity where the feature is accessible.
     */
    private fun buildExtendedCommands(
        file: MediaFile,
        appSettings: AppSettings,
        isWritable: Boolean,
    ): List<PlayerCommand> {
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
            if (BrowseItemOperationPolicy.supports(BrowseItemOperation.FAVORITE, file)) {
                add(PlayerCommand.FAVORITE)
            }
            // S0459 Phase 07: unified «Send to..» replaces the per-file Share + Telegram items.
            // Receiver applicability (incl. Telegram-installed gating) is resolved by SendToMenuManager.
            // S1325: both stage or read a single file, so a folder row offers neither.
            if (BrowseItemOperationPolicy.supports(BrowseItemOperation.SEND_TO, file)) {
                add(PlayerCommand.SEND_TO)
            }
            if (BrowseItemOperationPolicy.supports(BrowseItemOperation.INFO, file)) {
                add(PlayerCommand.INFO)
            }

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
