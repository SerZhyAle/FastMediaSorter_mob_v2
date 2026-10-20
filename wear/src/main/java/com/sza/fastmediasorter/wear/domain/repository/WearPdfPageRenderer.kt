package com.sza.fastmediasorter.wear.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable

/**
 * S4227: draws the pages of a PDF on the watch, one at a time, at the width the screen asks for.
 *
 * The owner expects a PDF to open in the watch's own player (strategic §6 item 5); a page at screen width bounds
 * the memory a page costs on a round screen, and a document that cannot be opened is null, never a crash.
 */
interface WearPdfPageRenderer {

    /** Null for a missing, damaged or password-protected PDF. */
    suspend fun open(uri: Uri): WearPdfDocument?
}

/** One open PDF; [close] is owed when the reader leaves. */
interface WearPdfDocument : Closeable {

    val pageCount: Int

    /** The page [index] drawn [widthPx] wide on white, or null when this page cannot be drawn. */
    suspend fun page(index: Int, widthPx: Int): Bitmap?

    override fun close()
}
