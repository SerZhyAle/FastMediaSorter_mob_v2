package com.sza.fastmediasorter.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Puts a value received from the clipboard on the server into the phone's clipboard (contract DEVICE-EXCHANGE 22
 * item CB). Writing the clipboard needs no focus, unlike reading it, so a background worker may call this.
 */
class ExchangeClipboardPlacer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * A short text goes in as text with the line ends it arrived with; a text too large for one Binder transaction,
     * an image or a file goes in as a FileProvider URI of [file] carrying the value's type.
     */
    fun place(value: ExchangeClipboardValue, file: File) {
        val label = context.getString(R.string.exchange_clipboard_clip_label)
        val clip = if (value.kind == ExchangeClipboardKind.TEXT && file.length() <= TEXT_CLIP_MAX_BYTES) {
            ClipData.newPlainText(label, file.readText(Charsets.UTF_8))
        } else {
            val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
            val mimeType = if (value.kind == ExchangeClipboardKind.TEXT) MIME_TEXT else value.mimeType ?: MIME_UNKNOWN
            ClipData(ClipDescription(label, arrayOf(mimeType)), ClipData.Item(uri))
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    companion object {
        /** S4216 R1: roughly 1 MB per Binder transaction, and the text travels as UTF-16 with overhead. */
        const val TEXT_CLIP_MAX_BYTES = 262_144L
        private const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_UNKNOWN = "application/octet-stream"
    }
}
