package com.sza.fastmediasorter.ui.settings.ticket.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.sza.fastmediasorter.domain.model.ticket.TicketAttachmentRole
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import timber.log.Timber
import java.io.File

/**
 * S4554: turns picked documents and a finished voice note into ticket attachments.
 *
 * The role comes from the MIME type (an image is a screenshot, text or `.log` a log); the read grant
 * is made persistable where the provider allows it, because the outbox copies the bytes only when the
 * owner presses Send, which may be after the picker's transient grant has lapsed.
 */
class TicketAttachmentPickerManager(private val context: Context) {

    fun resolve(uris: List<Uri>): List<TicketAttachmentSource> = uris.map { uri ->
        keepReadGrant(uri)
        val name = displayNameOf(uri)
        val mime = context.contentResolver.getType(uri) ?: DEFAULT_MIME
        TicketAttachmentSource(uri.toString(), name, mime, TicketAttachmentRole.fromMimeType(mime, name))
    }

    fun voiceNote(file: File): TicketAttachmentSource =
        TicketAttachmentSource(Uri.fromFile(file).toString(), file.name, VOICE_MIME, TicketAttachmentRole.VOICE)

    private fun keepReadGrant(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Timber.d("Ticket attachment grant is not persistable: ${it.javaClass.simpleName}") }
    }

    private fun displayNameOf(uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: FALLBACK_NAME
    }

    private companion object {
        const val DEFAULT_MIME = "application/octet-stream"
        const val VOICE_MIME = "audio/mp4"
        const val FALLBACK_NAME = "attachment"
    }
}
