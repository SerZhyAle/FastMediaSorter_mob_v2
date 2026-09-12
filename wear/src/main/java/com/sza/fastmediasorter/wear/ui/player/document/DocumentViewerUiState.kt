package com.sza.fastmediasorter.wear.ui.player.document

import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.ui.common.WearListPosition

/**
 * What the reader screen draws.
 *
 * S2532: [failure] keeps the Phase 02 enum instead of the message string the other viewers carry.
 * Strategic §2 goal 5 asks a read error, an empty file and an unsupported format to stay apart on
 * screen, and §3.2 asks every one of those sentences to exist in EN/RU/UK - an exception text folded
 * into a `String?` can satisfy neither, because nothing downstream can tell the four cases apart or
 * translate what it was handed.
 *
 * @param paragraphs the document as list items; empty while loading, and empty for [isEmpty].
 * @param truncated the file was longer than the reading cap, so [paragraphs] end early on purpose.
 * @param isEmpty the file was read and held nothing - deliberately not a [WearDocumentFailure].
 * @param fontSize the stored choice, read before the text so the first frame is drawn at the right size.
 * @param initialPosition where this document was left, or null to open it at its start. Published in the
 * same update as [paragraphs], so the screen never sees text without knowing where to put it.
 */
data class DocumentViewerUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val paragraphs: List<String> = emptyList(),
    val truncated: Boolean = false,
    val isEmpty: Boolean = false,
    val failure: WearDocumentFailure? = null,
    val fontSize: DocumentFontSize = DocumentFontSize.MEDIUM,
    val initialPosition: WearListPosition? = null
)
