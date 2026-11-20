package com.sza.fastmediasorter.ui.common.widget

/**
 * Decides when a settings row moves its value under the caption (S3863, S4788).
 *
 * The caption is measured first, so a caption that nearly fills the row leaves the value a narrow
 * column. Two situations then read badly. A column narrower than the value's widest word splits that
 * word in the middle, leaving a last letter alone on the next line, which no wrap setting prevents.
 * A column under a third of the row wraps the value one glyph per line at a large accessibility font
 * scale. The value moves under the caption in either case, and stays beside it otherwise, because
 * proximity of the pair is the rule ("Caption and Value Proximity", docs/ARCHITECTURE.md).
 *
 * A pure function of widths, so [SettingsSelectionRow] and [SettingsDropdownRow] cannot drift apart and
 * the decision is testable without a screen. A row inside a shared label column never asks: the group
 * applies a column only when every value fits at full length.
 */
internal object SettingsValueStackPolicy {

    /** A third keeps a default-scale two-line value beside its caption and stacks the one-glyph column. */
    private const val ROOM_DIVISOR = 3

    /**
     * @param contentPx the row's width less its padding.
     * @param besideRoomPx what is left beside the caption for the value, after every other child and gap.
     * @param valueNaturalPx the value's single-line width.
     * @param valueWordPx the value's widest unbreakable run, the narrowest column that splits no word.
     */
    fun shouldStack(contentPx: Int, besideRoomPx: Int, valueNaturalPx: Int, valueWordPx: Int): Boolean {
        if (besideRoomPx >= valueNaturalPx) return false
        return besideRoomPx < valueWordPx || besideRoomPx < contentPx / ROOM_DIVISOR
    }
}
