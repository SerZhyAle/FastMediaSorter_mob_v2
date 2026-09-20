package com.sza.fastmediasorter.ui.common.widget

import androidx.annotation.DrawableRes

/**
 * Content of the badge slot of a [MediaItemView].
 *
 * One structured type replaces the six one-off badge ids surveyed in
 * `docs/ui/PHONE_UI_COMPONENT_PATTERNS.md` section 2.2 (`tvPinBadge`, `tvErrorBadge`,
 * `tvDestinationBadge`, `taskbarUnpinBadge`, `cellRemoveBadge`, `cellModeBadge`), each of which
 * carried its own background drawable and its own text sizing.
 *
 * A badge carries [text], [iconRes] or both; a badge with neither is meaningless and
 * [MediaItemView.setBadge] hides the slot for it, which is also what `null` means.
 *
 * @property text label rendered in the pill, `null` for an icon-only badge.
 * @property iconRes icon drawn at the start of the label, `null` for a text-only badge.
 * @property tone semantic colour role of the pill.
 */
data class ItemBadge(
    val text: CharSequence? = null,
    @DrawableRes val iconRes: Int? = null,
    val tone: Tone = Tone.NEUTRAL,
) {

    /**
     * Colour role of a badge, resolved against the theme rather than against a literal.
     *
     * [NEUTRAL] is the default pill of `Widget.FastMediaSorter.Item.Badge`, [ACCENT] marks a state
     * the user chose (pinned, destination) and [ERROR] marks a state the user must notice.
     */
    enum class Tone { NEUTRAL, ACCENT, ERROR }

    /**
     * True when the badge has something to render. An empty badge is hidden rather than drawn as an
     * empty pill, which is what the retired one-off badges did on a blank label.
     */
    fun hasContent(): Boolean = !text.isNullOrEmpty() || iconRes != null
}
