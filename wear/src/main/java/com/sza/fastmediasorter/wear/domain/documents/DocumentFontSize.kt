package com.sza.fastmediasorter.wear.domain.documents

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R

/** Multipliers over the module's body style, not absolute sizes - the theme keeps owning the scale. */
private const val SCALE_SMALL = 0.8f
private const val SCALE_MEDIUM = 1.0f
private const val SCALE_LARGE = 1.4f

/**
 * How large the reader draws the document's text.
 *
 * S2532: three steps rather than a continuous slider, because on a round watch the control has to be
 * a tap target of its own - strategic §3.2 sets that minimum - and three targets is what fits beside
 * each other on the narrowest face the module supports.
 *
 * In domain rather than beside the screen because it is a stored preference: [WearDocumentPreferences]
 * both reads and writes it, and a repository contract that names a type living in `ui` would make the
 * whole persistence path depend on the screen that happens to draw it today.
 *
 * @param scale multiplier applied to the body text style the reader would otherwise use.
 * @param labelRes the size's name, spoken by TalkBack; the control itself is drawn as a glyph, so
 * this is the only place the size is ever put into words. A string id is a plain Int and travels here
 * exactly as it already does on [WearDocumentFormat] in this package.
 */
enum class DocumentFontSize(val scale: Float, @StringRes val labelRes: Int) {
    SMALL(SCALE_SMALL, R.string.wear_document_font_small),
    MEDIUM(SCALE_MEDIUM, R.string.wear_document_font_medium),
    LARGE(SCALE_LARGE, R.string.wear_document_font_large)
}
