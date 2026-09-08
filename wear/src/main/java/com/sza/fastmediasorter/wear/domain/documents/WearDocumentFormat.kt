package com.sza.fastmediasorter.wear.domain.documents

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R

/**
 * A document format this module tells apart, and whether the watch renders it as text.
 *
 * S2532: the router, the refusal screen and the reader all have to agree on "does the watch render
 * this?", and before this enum that answer was a mime-prefix comparison hidden inside the router -
 * invisible to the screen, which could therefore only show one general phrase for every document
 * alike. Naming the format makes it a value the graph can carry and a screen can read.
 *
 * @property readableOnWatch whether the watch shows the content as text. Only the text-shaped
 * entries do in this iteration; PDF, EPUB and the office formats stay with the phone.
 * @property labelRes how the format is named to the wearer when a screen has to say which one it is.
 * The readable entries share one label on purpose - nothing has to name them, because they open.
 */
enum class WearDocumentFormat(
    val readableOnWatch: Boolean,
    @StringRes val labelRes: Int
) {
    PLAIN_TEXT(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    MARKDOWN(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    CSV(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    LOG(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    JSON(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    XML(readableOnWatch = true, labelRes = R.string.wear_document_format_text),
    PDF(readableOnWatch = false, labelRes = R.string.wear_document_format_pdf),
    EPUB(readableOnWatch = false, labelRes = R.string.wear_document_format_epub),
    OFFICE(readableOnWatch = false, labelRes = R.string.wear_document_format_office),
    OTHER(readableOnWatch = false, labelRes = R.string.wear_document_format_other);

    companion object {

        /**
         * The format a navigation argument names, or [OTHER] when it names none.
         *
         * A route argument outlives the process that wrote it, so it can arrive spelled by a build
         * that knew a name this one does not; `valueOf` would answer that with a crash on the
         * wearer's screen, where [OTHER] is both true and harmless.
         */
        fun fromToken(token: String?): WearDocumentFormat =
            WearDocumentFormat.entries.firstOrNull { it.name == token } ?: OTHER
    }
}
