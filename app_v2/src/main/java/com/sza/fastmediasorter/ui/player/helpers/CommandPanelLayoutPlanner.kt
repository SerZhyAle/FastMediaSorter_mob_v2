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
 *   – Previous, Next (right anchor — always visible)
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
        val iconResId: Int,
        // Short label for Big Buttons Mode top-panel display. 0 = use titleResId at runtime.
        val shortTitleResId: Int = 0
    ) {
        // ── Group 1 : high-priority adaptive commands (priorities 5–70) ───────────
        // Lowest priority numbers = first on bar (leftmost), last to overflow.
        // Previous/Next are fixed right anchors — not in this group.

        PLAYBACK_ORDER(5, R.id.menu_playback_order, true, R.string.playback_order_menu_title, 0,
            R.string.big_btn_short_playback_order),
        DELETE(10, R.id.menu_delete, true, R.string.delete, R.drawable.ic_delete,
            R.string.big_btn_short_delete),
        FAVORITE(20, R.id.menu_favorite, true, R.string.favorite, R.drawable.ic_star_outline,
            R.string.big_btn_short_favorite),
        SHARE(30, R.id.menu_share, true, R.string.share, R.drawable.ic_share,
            R.string.big_btn_short_share),
        INFO(40, R.id.menu_info, true, R.string.file_information, R.drawable.ic_info,
            R.string.big_btn_short_info),
        FULLSCREEN(50, R.id.menu_fullscreen, true, R.string.fullscreen_mode, R.drawable.ic_fullscreen,
            R.string.big_btn_short_fullscreen),
        SLIDESHOW(60, R.id.menu_slideshow, true, R.string.slideshow, R.drawable.ic_play,
            R.string.big_btn_short_slideshow),
        RANDOM(70, R.id.menu_random, true, R.string.random_file_description, R.drawable.ic_random_nav,
            R.string.big_btn_short_random),

        // ── Group 2 : current command-bar commands (priorities 200–499) ──────────────

        BLACK_SCREEN(195, R.id.menu_black_screen, true,
            R.string.black_screen_button_title, R.drawable.ic_black_screen,
            R.string.big_btn_short_black_screen),
        RENAME(200, R.id.menu_rename, true, R.string.rename, R.drawable.ic_rename,
            R.string.big_btn_short_rename),
        EDIT(210, R.id.menu_edit, true, R.string.edit, android.R.drawable.ic_menu_edit,
            R.string.big_btn_short_edit),
        // VR flavor only: placed directly after EDIT so it sits next to the Control dialog button.
        VR_3D(211, R.id.menu_3d_vr, true, R.string.vr_toggle_enter_description,
            R.drawable.ic_vr_3d),
        UNDO(220, R.id.menu_undo, true, R.string.undo, android.R.drawable.ic_menu_revert,
            R.string.big_btn_short_undo),
        CAST(230, R.id.menu_cast, true, R.string.cast_to_chromecast, R.drawable.ic_cast,
            R.string.big_btn_short_cast),
        // Low-priority direct video action: show on command bar when space permits,
        // otherwise let adaptive portrait layout spill it to overflow.
        SAVE_FRAME(235, R.id.menu_save_frame, true, R.string.menu_save_frame,
            R.drawable.ic_save_frame, R.string.big_btn_short_save_frame),
        LYRICS(240, R.id.menu_lyrics, true, R.string.lyrics, R.drawable.ic_microphone,
            R.string.big_btn_short_lyrics),
        SEARCH_YOUTUBE_MUSIC(250, R.id.menu_search_youtube_music, true,
            R.string.search_in_youtube_music, R.drawable.ic_youtube_music),
        // S0162: Rotation toggle — low-priority, shows on bar only when space permits
        ROTATION_TOGGLE(490, R.id.menu_rotation_toggle, true,
            R.string.rotation_toggle_title, R.drawable.ic_rotation_unlocked,
            R.string.big_btn_short_rotation),

        // PDF
        SEARCH_PDF(260, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search, R.string.big_btn_short_search),
        TRANSLATE_PDF(270, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate, R.string.big_btn_short_translate),  // icon replaced asynchronously by LanguageBadgeDrawable
        PDF_TEXT_SETTINGS(280, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book, R.string.big_btn_short_text_settings),
        OCR_PDF(290, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr,
            R.string.big_btn_short_ocr),
        GOOGLE_LENS_PDF(300, R.id.menu_google_lens, true, R.string.google_lens,
            R.drawable.ic_google_lens),

        // TEXT
        SEARCH_TEXT(310, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search, R.string.big_btn_short_search),
        EDIT_TEXT(320, R.id.menu_edit_text, true, R.string.edit,
            android.R.drawable.ic_menu_edit, R.string.big_btn_short_edit),
        TRANSLATE_TEXT(330, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate, R.string.big_btn_short_translate),  // icon replaced asynchronously
        TEXT_SETTINGS(340, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book, R.string.big_btn_short_text_settings),
        COPY_TEXT(350, R.id.menu_copy_text, true, R.string.copy,
            android.R.drawable.ic_menu_save, R.string.big_btn_short_copy),

        // EPUB
        SEARCH_EPUB(360, R.id.menu_search, true, R.string.search,
            android.R.drawable.ic_menu_search, R.string.big_btn_short_search),
        TRANSLATE_EPUB(370, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate, R.string.big_btn_short_translate),  // icon replaced asynchronously
        EPUB_TEXT_SETTINGS(380, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book, R.string.big_btn_short_text_settings),
        OCR_EPUB(390, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr,
            R.string.big_btn_short_ocr),

        // IMAGE / GIF
        TRANSLATE_IMAGE(400, R.id.menu_translate, true, R.string.translate,
            R.drawable.ic_translate, R.string.big_btn_short_translate),  // icon replaced asynchronously
        IMAGE_TEXT_SETTINGS(410, R.id.menu_text_settings, true, R.string.translation_settings,
            R.drawable.ic_book, R.string.big_btn_short_text_settings),
        OCR_IMAGE(420, R.id.menu_ocr, true, R.string.ocr_button_description, R.drawable.ic_ocr,
            R.string.big_btn_short_ocr),
        GOOGLE_LENS_IMAGE(430, R.id.menu_google_lens, true, R.string.google_lens,
            R.drawable.ic_google_lens, R.string.big_btn_short_lens),

        // ── Group 3 : overflow-only commands (priorities 500–699) ───────────────────

        SLEEP_TIMER(500, R.id.menu_sleep_timer, true, R.string.menu_sleep_timer,
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
        PRINT(600, R.id.menu_print, true, R.string.menu_print, R.drawable.ic_print),
        // S0028: multi-window — overflow-only; shown only when VR+setting allows it
        OPEN_IN_SEPARATE_WINDOW(610, R.id.menu_open_in_separate_window, false,
            R.string.action_open_in_separate_window, R.drawable.ic_open_in_browse),
        // S0106: Image crop & compress (IMAGE only — static formats JPEG/PNG/WebP)
        CROP(620, R.id.menu_crop, false, R.string.menu_crop, R.drawable.ic_crop),
        CROP_TO_FILE(630, R.id.menu_crop_to_file, false, R.string.menu_crop_to_file, R.drawable.ic_crop_to_file),
        COMPRESS_COPY(640, R.id.menu_compress_copy, false, R.string.menu_compress_copy, R.drawable.ic_compress),
        // S0107: Draw overlay — annotate static images (IMAGE only, not GIF/APNG)
        DRAW_OVERLAY(650, R.id.menu_draw_overlay, false, R.string.menu_draw_overlay, R.drawable.ic_draw_overlay);
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
     * @param canWrite        Whether the current resource is writable.
     * @param canRead         Whether the current resource is readable.
     * @param isWifiConnected Whether a Wi-Fi connection is currently available.
     * @param showFavorite    Whether the Favorite button should be included (async setting).
     * @param showRandom      Whether the Random navigation button should be included.
     */
    fun buildActiveCommands(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean,
        canRead: Boolean,
        isWifiConnected: Boolean,
        showFavorite: Boolean = true,
        showRandom: Boolean = false,
        allowSeparateWindow: Boolean = false
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
            // ── Group 1 : high-priority adaptive buttons ──────────────────────────────
            if (isAudio || isVideo) add(PlayerCommand.PLAYBACK_ORDER)
            if (canWrite && state.allowDelete) add(PlayerCommand.DELETE)
            if (showFavorite) add(PlayerCommand.FAVORITE)
            add(PlayerCommand.SHARE)
            add(PlayerCommand.INFO)
            if (!isAudio && (isImage || isVideo || isPdf || isText || isEpub)) add(PlayerCommand.FULLSCREEN)
            if (showRandom) add(PlayerCommand.RANDOM)

            // ── Group 2 ──────────────────────────────────────────────────────────────
            if ((isAudio || isVideo) && state.showBlackScreenButton) add(PlayerCommand.BLACK_SCREEN)
            if (canWrite && state.allowRename) add(PlayerCommand.RENAME)
            if ((isImage && canWrite) || (isVideo && !isAudio) || isPdf) add(PlayerCommand.EDIT)
            if (state.lastOperation != null && canWrite) add(PlayerCommand.UNDO)
            if (BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected) add(PlayerCommand.CAST)
            if (isAudio) add(PlayerCommand.LYRICS)
            if (isAudio) add(PlayerCommand.SEARCH_YOUTUBE_MUSIC)
            // S0162: Rotation toggle — only when global delegation is OFF and device has accelerometer
            if (state.showRotationToggle) add(PlayerCommand.ROTATION_TOGGLE)

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
            // S0028: multi-window tear-off — VR+setting guard already applied by controller
            if (allowSeparateWindow) add(PlayerCommand.OPEN_IN_SEPARATE_WINDOW)
            // S0106: Crop commands — static images only (exclude animated GIF and APNG)
            val isStaticBitmap = isImage && !file.name.lowercase().endsWith(".gif") &&
                !file.name.lowercase().endsWith(".apng")
            if (isStaticBitmap && canWrite && !isReadOnly) add(PlayerCommand.CROP)
            if (isStaticBitmap) add(PlayerCommand.CROP_TO_FILE)
            if (isStaticBitmap) add(PlayerCommand.COMPRESS_COPY)
            // S0107: Draw overlay — static images only (same guard as crop)
            if (isStaticBitmap) add(PlayerCommand.DRAW_OVERLAY)
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

    /**
     * Compute Big Buttons Mode layout for the top panel command area after fixed buttons have
     * already reserved their slots. When overflow is required, the overflow trigger consumes one
     * of the remaining command slots so the total visible top-panel button count still caps out.
     */
    fun planBigButtonsLayout(
        activeCommands: List<PlayerCommand>,
        maxCommandSlots: Int
    ): LayoutResult {
        if (activeCommands.isEmpty()) {
            return LayoutResult(emptyList(), emptyList(), showOverflowButton = false)
        }

        val sorted = activeCommands.sortedBy { it.priority }
        if (maxCommandSlots <= 0) {
            return LayoutResult(
                barCommands = emptyList(),
                overflowCommands = sorted,
                showOverflowButton = true
            )
        }

        val barCapable = sorted.filter { it.barCapable }
        val overflowOnly = sorted.filterNot { it.barCapable }

        if (overflowOnly.isEmpty() && barCapable.size <= maxCommandSlots) {
            return LayoutResult(
                barCommands = barCapable,
                overflowCommands = emptyList(),
                showOverflowButton = false
            )
        }

        val visibleBarSlots = (maxCommandSlots - 1).coerceAtLeast(0)
        val onBar = barCapable.take(visibleBarSlots)
        val overflow = (barCapable.drop(visibleBarSlots) + overflowOnly)
            .sortedBy { it.priority }

        return LayoutResult(
            barCommands = onBar,
            overflowCommands = overflow,
            showOverflowButton = overflow.isNotEmpty()
        )
    }
}
