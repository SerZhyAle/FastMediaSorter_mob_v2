package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerViewModel

/**
 * Priority-driven portrait layout planner for the player command panel center group.
 *
 * Determines which commands appear directly on the command bar versus in the
 * overflow (three-dots) menu for portrait orientation. Only center-group adaptive
 * commands participate in the plan.
 *
 * Fixed button groups are NOT subject to adaptive width planning:
 *   – Back (left anchor)
 *   – Delete, Fullscreen, Previous, Next (always-visible right group)
 *   – Share, Info, Slideshow, Favorite (conditional right group; Phase 6 decision:
 *     kept in the fixed right-side button group, not subject to adaptive width
 *     planning, so they remain always visible per their individual predicates
 *     and do not need overflow parity).
 */
class CommandPanelLayoutPlanner {

    /**
     * Every command that participates in adaptive portrait layout planning.
     *
     * Priority: lower number = shown on bar first (highest priority stays on bar longest).
     * [barCapable] = has a dedicated command-bar view; [menuItemId] = overflow menu item.
     * [titleResId] = string resource for menu title.
     * [iconResId]  = drawable resource for menu icon; 0 means the icon is set dynamically.
     */
    @Suppress("unused") // entries are resolved at runtime via their properties
    enum class PlayerCommand(
        val priority: Int,
        val menuItemId: Int,
        val barCapable: Boolean,
        val titleResId: Int,
        val iconResId: Int
    ) {
        // ── Group 2 : current command-bar commands (priorities 200–499) ──────────────

        RENAME(200, R.id.menu_rename, true, R.string.rename, R.drawable.ic_rename),
        EDIT(210, R.id.menu_edit, true, R.string.edit, android.R.drawable.ic_menu_edit),
        // VR flavor only: placed directly after EDIT so it sits next to the Control dialog button.
        VR_3D(211, R.id.menu_3d_vr, true, R.string.vr_toggle_enter_description,
            R.drawable.ic_vr_3d),
        UNDO(220, R.id.menu_undo, true, R.string.undo, android.R.drawable.ic_menu_revert),
        CAST(230, R.id.menu_cast, true, R.string.cast_to_chromecast, R.drawable.ic_cast),
        // Low-priority direct video action: show on command bar when space permits,
        // otherwise let adaptive portrait layout spill it to overflow.
        SAVE_FRAME(235, R.id.menu_save_frame, true, R.string.menu_save_frame,
            R.drawable.ic_save_frame),
        LYRICS(240, R.id.menu_lyrics, true, R.string.lyrics, R.drawable.ic_microphone),
        SEARCH_YOUTUBE_MUSIC(250, R.id.menu_search_youtube_music, true,
            R.string.search_in_youtube_music, R.drawable.ic_youtube_music),

        // PDF
        SEARCH_PDF(260, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search),
        TRANSLATE_PDF(270, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate),  // icon replaced asynchronously by LanguageBadgeDrawable
        PDF_TEXT_SETTINGS(280, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book),
        OCR_PDF(290, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr),
        GOOGLE_LENS_PDF(300, R.id.menu_google_lens, true, R.string.google_lens,
            R.drawable.ic_google_lens),

        // TEXT
        SEARCH_TEXT(310, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search),
        EDIT_TEXT(320, R.id.menu_edit_text, true, R.string.edit,
            android.R.drawable.ic_menu_edit),
        TRANSLATE_TEXT(330, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate),  // icon replaced asynchronously
        TEXT_SETTINGS(340, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book),
        COPY_TEXT(350, R.id.menu_copy_text, true, R.string.copy,
            android.R.drawable.ic_menu_save),

        // EPUB
        SEARCH_EPUB(360, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search),
        TRANSLATE_EPUB(370, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate),  // icon replaced asynchronously
        EPUB_TEXT_SETTINGS(380, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book),
        OCR_EPUB(390, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr),

        // IMAGE / GIF
        TRANSLATE_IMAGE(400, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate),  // icon replaced asynchronously
        IMAGE_TEXT_SETTINGS(410, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book),
        OCR_IMAGE(420, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr),
        GOOGLE_LENS_IMAGE(430, R.id.menu_google_lens, true, R.string.google_lens,
            R.drawable.ic_google_lens),

        // ── Group 3 : overflow-only commands (priorities 500–699) ───────────────────

        SLEEP_TIMER(500, R.id.menu_sleep_timer, false, R.string.menu_sleep_timer,
            R.drawable.ic_sleep_timer),
        REOPEN_ENCODING(510, R.id.menu_reopen_encoding, false, R.string.reopen_with_encoding,
            android.R.drawable.ic_menu_sort_alphabetically),
        TOGGLE_MARKDOWN(520, R.id.menu_toggle_markdown, false, R.string.toggle_markdown,
            android.R.drawable.ic_menu_view),
        READER_SETTINGS(530, R.id.menu_reader_settings, false, R.string.reader_settings,
            android.R.drawable.ic_menu_preferences),
        READ_ALOUD(540, R.id.menu_read_aloud, false, R.string.read_aloud,
            android.R.drawable.ic_lock_silent_mode_off),
        PDF_SCROLL_MODE(550, R.id.menu_pdf_scroll_mode, false, R.string.pdf_scroll_mode,
            R.drawable.ic_view_list),
        PDF_COLOR_MODE(560, R.id.menu_pdf_color_mode, false, R.string.pdf_night_mode,
            R.drawable.ic_night_mode),
        PDF_THUMBNAILS(570, R.id.menu_pdf_thumbnails, false, R.string.pdf_thumbnails,
            R.drawable.ic_view_list),
        EPUB_READER_SETTINGS(580, R.id.menu_epub_reader_settings, false,
            R.string.epub_reader_settings, R.drawable.ic_settings),
        EPUB_SEARCH_ALL(590, R.id.menu_epub_search_all, false,
            R.string.epub_search_all_chapters, android.R.drawable.ic_menu_search),
        // Low-priority bar-capable command: appears on bar only when all higher-priority
        // commands fit and space remains; otherwise spills to overflow (⋯ menu).
        PRINT(600, R.id.menu_print, true, R.string.menu_print, R.drawable.ic_print);
    }

    data class LayoutResult(
        val barCommands: List<PlayerCommand>,
        val overflowCommands: List<PlayerCommand>,
        val showOverflowButton: Boolean
    )

    /**
     * Build the ordered list of adaptive commands that are currently active for this
     * [state]. Overflow-only commands are always included when their conditions are met.
     *
     * @param canWrite Whether the current resource is writable.
     * @param canRead  Whether the current resource is readable.
     * @param isWifiConnected Whether a Wi-Fi connection is currently available.
     */
    fun buildActiveCommands(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean,
        canRead: Boolean,
        isWifiConnected: Boolean
    ): List<PlayerCommand> {
        val file = state.currentFile ?: return emptyList()
        val isImage = file.type == MediaType.IMAGE || file.type == MediaType.GIF
        val isVideo = file.type == MediaType.VIDEO || file.type == MediaType.AUDIO
        val isAudio = file.type == MediaType.AUDIO
        val isPdf = file.type == MediaType.PDF
        val isText = file.type == MediaType.TEXT
        val isEpub = file.type == MediaType.EPUB
        val isReadOnly = state.resource?.isReadOnly == true

        return buildList {
            // ── Group 2 ──────────────────────────────────────────────────────────────
            if (canWrite && state.allowRename) add(PlayerCommand.RENAME)
            if ((isImage && canWrite) || isVideo || isPdf) add(PlayerCommand.EDIT)
            if (state.lastOperation != null && canWrite) add(PlayerCommand.UNDO)
            if (BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected) add(PlayerCommand.CAST)
            if (isAudio) add(PlayerCommand.LYRICS)
            if (isAudio) add(PlayerCommand.SEARCH_YOUTUBE_MUSIC)

            if (isPdf) add(PlayerCommand.SEARCH_PDF)
            if (isPdf && state.enableTranslation) add(PlayerCommand.TRANSLATE_PDF)
            if (isPdf) add(PlayerCommand.PDF_TEXT_SETTINGS)
            if (isPdf && state.enableOcr) add(PlayerCommand.OCR_PDF)
            if (isPdf && state.enableGoogleLens) add(PlayerCommand.GOOGLE_LENS_PDF)

            if (isText) add(PlayerCommand.SEARCH_TEXT)
            if (isText && canWrite && !isReadOnly) add(PlayerCommand.EDIT_TEXT)
            if (isText && state.enableTranslation) add(PlayerCommand.TRANSLATE_TEXT)
            if (isText) add(PlayerCommand.TEXT_SETTINGS)
            if (isText || isPdf) add(PlayerCommand.COPY_TEXT)

            if (isEpub) add(PlayerCommand.SEARCH_EPUB)
            if (isEpub && state.enableTranslation) add(PlayerCommand.TRANSLATE_EPUB)
            if (isEpub) add(PlayerCommand.EPUB_TEXT_SETTINGS)
            if (isEpub && state.enableOcr) add(PlayerCommand.OCR_EPUB)

            if (isImage && state.enableTranslation) add(PlayerCommand.TRANSLATE_IMAGE)
            if (isImage) add(PlayerCommand.IMAGE_TEXT_SETTINGS)
            if (isImage && state.enableOcr) add(PlayerCommand.OCR_IMAGE)
            if (isImage && state.enableGoogleLens) add(PlayerCommand.GOOGLE_LENS_IMAGE)

            // ── Group 3 (overflow-only) ───────────────────────────────────────────
            if (isAudio || isVideo) add(PlayerCommand.SLEEP_TIMER)
            if (isText) add(PlayerCommand.REOPEN_ENCODING)
            if (isText && file.name.endsWith(".md", ignoreCase = true)) {
                add(PlayerCommand.TOGGLE_MARKDOWN)
            }
            if (isText) add(PlayerCommand.READER_SETTINGS)
            if (isText || isPdf || isEpub) add(PlayerCommand.READ_ALOUD)
            if (isPdf) add(PlayerCommand.PDF_SCROLL_MODE)
            if (isPdf) add(PlayerCommand.PDF_COLOR_MODE)
            if (isPdf) add(PlayerCommand.PDF_THUMBNAILS)
            if (isEpub) add(PlayerCommand.EPUB_READER_SETTINGS)
            if (isEpub) add(PlayerCommand.EPUB_SEARCH_ALL)
            if (isPdf || isText || isImage) add(PlayerCommand.PRINT)
            // Save Frame is only available for video (not audio, images, or docs)
            if (file.type == MediaType.VIDEO) add(PlayerCommand.SAVE_FRAME)
            // 3DVR toggle: VR flavor only, video files only
            if (BuildConfig.SUPPORT_VR_PLAYER && file.type == MediaType.VIDEO) add(PlayerCommand.VR_3D)
        }.sortedBy { it.priority }
    }

    /**
     * Compute portrait layout for the center command group.
     *
     * @param activeCommands       Commands eligible for display in the current state,
     *                             sorted by priority.
     * @param availableWidthPx     Pixel width available for the center group (between
     *                             the left Back anchor and the right fixed-button group).
     * @param buttonSizePx         Width of one bar command button in pixels.
     * @param overflowButtonSizePx Width of the overflow (three-dots) button in pixels.
     */
    fun planLayout(
        activeCommands: List<PlayerCommand>,
        availableWidthPx: Int,
        buttonSizePx: Int,
        overflowButtonSizePx: Int
    ): LayoutResult {
        if (activeCommands.isEmpty()) {
            return LayoutResult(emptyList(), emptyList(), showOverflowButton = false)
        }

        val sorted = activeCommands.sortedBy { it.priority }
        val barCapable = sorted.filter { it.barCapable }
        val overflowOnly = sorted.filter { !it.barCapable }

        // Extreme narrow: no usable width at all
        if (availableWidthPx <= 0) {
            return LayoutResult(
                barCommands = emptyList(),
                overflowCommands = sorted,
                showOverflowButton = sorted.isNotEmpty()
            )
        }

        // If all bar-capable commands fit and there are no overflow-only active commands,
        // no overflow button is needed.
        val hasOverflowOnlyActive = overflowOnly.isNotEmpty()
        if (!hasOverflowOnlyActive && barCapable.size * buttonSizePx <= availableWidthPx) {
            return LayoutResult(
                barCommands = barCapable,
                overflowCommands = emptyList(),
                showOverflowButton = false
            )
        }

        // Overflow button is needed; reserve its width.
        val widthForBarCommands = availableWidthPx - overflowButtonSizePx
        if (widthForBarCommands <= 0) {
            // Extreme narrow: overflow button only, everything in overflow
            return LayoutResult(
                barCommands = emptyList(),
                overflowCommands = sorted,
                showOverflowButton = sorted.isNotEmpty()
            )
        }

        val maxBarSlots = widthForBarCommands / buttonSizePx
        val onBar = barCapable.take(maxBarSlots)
        val spilledToOverflow = barCapable.drop(maxBarSlots)
        val allOverflow = (spilledToOverflow + overflowOnly).sortedBy { it.priority }

        // Single bar-capable overflow item: promote to bar, hide overflow button
        val singlePromote = allOverflow.singleOrNull { it.barCapable }
        if (singlePromote != null && allOverflow.size == 1) {
            return LayoutResult(
                barCommands = onBar + singlePromote,
                overflowCommands = emptyList(),
                showOverflowButton = false
            )
        }

        return LayoutResult(
            barCommands = onBar,
            overflowCommands = allOverflow,
            showOverflowButton = allOverflow.isNotEmpty()
        )
    }
}
