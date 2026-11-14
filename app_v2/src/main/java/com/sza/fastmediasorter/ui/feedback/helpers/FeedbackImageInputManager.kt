package com.sza.fastmediasorter.ui.feedback.helpers

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.domain.model.ticket.TicketAttachmentRole
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.utils.queryDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S4573: the three picture sources of the feedback form - the clipboard, the photo picker and the
 * camera.
 *
 * Every picture is copied into the app cache at once: a clipboard or picker grant is transient and the
 * packet is staged only when the owner presses Send, so a URI kept as it came may be unreadable by then.
 */
class FeedbackImageInputManager(private val context: Context) {

    private val folder: File get() = File(context.cacheDir, CACHE_FOLDER).apply { mkdirs() }

    /** The image the primary clip carries, or null when the clipboard holds no picture. */
    suspend fun fromClipboard(): TicketAttachmentSource? {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val uri = clip?.let { (0 until it.itemCount).firstNotNullOfOrNull { index -> it.getItemAt(index).uri } }
        val mime = uri?.let { context.contentResolver.getType(it) }?.takeIf { it.startsWith(IMAGE_PREFIX) }
        return if (uri != null && mime != null) copy(uri, mime) else null
    }

    suspend fun fromPicker(uris: List<Uri>): List<TicketAttachmentSource> = uris.mapNotNull { uri ->
        copy(uri, context.contentResolver.getType(uri) ?: JPEG_MIME)
    }

    /** A cache file and its provider URI for the camera app to write into. */
    fun newPhotoTarget(): Pair<File, Uri> {
        val file = File(folder, "photo_${System.currentTimeMillis()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun fromPhoto(file: File): TicketAttachmentSource? = file.takeIf { it.length() > 0 }?.let {
        TicketAttachmentSource(Uri.fromFile(it).toString(), it.name, JPEG_MIME, TicketAttachmentRole.SCREENSHOT)
    }

    private suspend fun copy(uri: Uri, mime: String): TicketAttachmentSource? = withContext(Dispatchers.IO) {
        val name = context.contentResolver.queryDisplayName(uri)?.takeIf(String::isNotBlank)
            ?: "image_${System.currentTimeMillis()}.${mime.substringAfter('/')}"
        val target = File(folder, "${System.nanoTime()}_$name")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            TicketAttachmentSource(Uri.fromFile(target).toString(), name, mime, TicketAttachmentRole.SCREENSHOT)
        } catch (e: IOException) {
            Timber.w(e, "Feedback picture could not be copied")
            target.delete()
            null
        } catch (e: SecurityException) {
            Timber.w(e, "Feedback picture is no longer readable")
            null
        }
    }

    private companion object {
        const val CACHE_FOLDER = "feedback"
        const val IMAGE_PREFIX = "image/"
        const val JPEG_MIME = "image/jpeg"
    }
}
