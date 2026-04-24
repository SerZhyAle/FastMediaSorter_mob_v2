package com.sza.fastmediasorter.ui.common.input

import androidx.annotation.StringRes

/**
 * Source-of-truth entry for the F1 help dialog.
 *
 * Each surface exposes a list of entries; the dialog renders them and
 * adds a clickable link to the full website documentation through
 * [InputHelpLinkResolver].
 *
 * The same table is used by documentation generators so there is a
 * single source of truth between in-app help and website docs.
 *
 * @param keys human-readable key label (e.g. `"F5 / Ctrl+C"`). Kept as
 *   a plain string so it can mix Latin letters, `+`, `/` and special
 *   keys without localisation indirection.
 * @param descriptionRes string resource describing the action in the
 *   user's language.
 */
data class InputHelpEntry(
    val keys: String,
    @StringRes val descriptionRes: Int,
) {
    /**
     * Grouping header above a set of entries (e.g. "Navigation",
     * "File operations"). Used by the dialog to render sections.
     */
    data class Section(
        @StringRes val titleRes: Int,
        val entries: List<InputHelpEntry>,
    )
}
