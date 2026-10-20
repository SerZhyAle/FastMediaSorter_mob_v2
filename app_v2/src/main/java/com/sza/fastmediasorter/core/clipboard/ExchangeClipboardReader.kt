package com.sza.fastmediasorter.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the phone's clipboard held when it was read, as one value of contract DEVICE-EXCHANGE 22 item BU. */
sealed interface ExchangeClipboardSource {
    /** Text with LF line ends only (22 item CB). */
    data class Text(val text: String) : ExchangeClipboardSource {
        override fun toString(): String = "Text(length=${text.length})"
    }

    data class Content(val uri: Uri, val kind: ExchangeClipboardKind, val mimeType: String) : ExchangeClipboardSource

    /** Several items: the person archives them first (22 item BU). */
    data object Several : ExchangeClipboardSource
    data object Empty : ExchangeClipboardSource

    /** An item that is neither text nor a `content:` URI, such as an intent. */
    data object Unsupported : ExchangeClipboardSource
}

/**
 * Turns a clip into one clipboard value by the rules of 22 item CB: the first item decides, text and HTML are text,
 * a `content:` URI of an `image/` type is an image and any other one a file. The clip must have been read by a
 * window holding input focus, which is the caller's; this class never touches `ClipboardManager`. The sensitive-
 * content flag is deliberately not read (22 item CD).
 */
class ExchangeClipboardReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun read(clip: ClipData?): ExchangeClipboardSource {
        val count = clip?.itemCount ?: 0
        return when {
            clip == null || count == 0 -> ExchangeClipboardSource.Empty
            count > 1 -> ExchangeClipboardSource.Several
            else -> classify(clip.getItemAt(0), clip.description)
        }
    }

    private fun classify(item: ClipData.Item, description: ClipDescription?): ExchangeClipboardSource {
        val uri = item.uri
        val text = item.text?.toString() ?: item.htmlText?.let { item.coerceToText(context).toString() }
        return when {
            uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT -> contentOf(uri, description)
            uri == null && item.intent == null && text.isNullOrEmpty() -> ExchangeClipboardSource.Empty
            !text.isNullOrEmpty() -> ExchangeClipboardSource.Text(normalizeLineEnds(text))
            else -> ExchangeClipboardSource.Unsupported
        }
    }

    private fun contentOf(uri: Uri, description: ClipDescription?): ExchangeClipboardSource.Content {
        val declared = description?.let { d -> (0 until d.mimeTypeCount).map(d::getMimeType) }.orEmpty()
            .firstOrNull { it.contains('/') && it != ClipDescription.MIMETYPE_TEXT_URILIST && !it.contains('*') }
        val mimeType = context.contentResolver.getType(uri) ?: declared ?: MIME_UNKNOWN
        val kind = if (mimeType.startsWith(IMAGE_PREFIX)) ExchangeClipboardKind.IMAGE else ExchangeClipboardKind.FILE
        return ExchangeClipboardSource.Content(uri, kind, mimeType)
    }

    companion object {
        /** 22 item BU: `application/octet-stream` when the type is unknown. */
        const val MIME_UNKNOWN = "application/octet-stream"
        private const val IMAGE_PREFIX = "image/"

        /** CRLF and a lone CR become LF, which is how a value travels (22 item CB). */
        fun normalizeLineEnds(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
    }
}
