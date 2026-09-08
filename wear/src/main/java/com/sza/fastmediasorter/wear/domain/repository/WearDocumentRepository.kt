package com.sza.fastmediasorter.wear.domain.repository

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent

/**
 * Turns a readable document into text the watch can hold, or into a reason it could not.
 *
 * S2532: reading is the data layer's obligation, never the screen's - strategic §5. The cap is a
 * parameter rather than something the caller applies afterwards because a screen cannot decline
 * bytes it has already been handed; by the time a multi-megabyte file is a `String`, the memory the
 * cap exists to protect is already spent.
 */
interface WearDocumentRepository {

    /**
     * The ceiling this reader applies when the caller has no reason to pin its own.
     *
     * Exposed on the contract so a use case can ask for the default without importing the data
     * layer that owns the number.
     */
    val defaultCapBytes: Long

    /**
     * Reads at most [capBytes] bytes of [uri] and decodes them.
     *
     * Never throws for an unreadable document: every failure arrives as
     * [WearDocumentContent.Failure] so the screen has one thing to render rather than two.
     */
    suspend fun readText(uri: Uri, capBytes: Long): WearDocumentContent
}
