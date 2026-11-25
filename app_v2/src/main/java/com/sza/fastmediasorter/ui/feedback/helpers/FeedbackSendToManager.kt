package com.sza.fastmediasorter.ui.feedback.helpers

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.feedback.FeedbackHandoff
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S4573: hands the sent feedback to the Send to.. menu. The form keeps its pictures and voice notes as
 * `file://` cache copies, which another app may not open, so each one is re-addressed through the app's
 * file provider first.
 */
class FeedbackSendToManager(
    private val activity: FragmentActivity,
    private val sendToMenu: SendToMenuManager,
) {

    fun show(handoff: FeedbackHandoff) {
        Timber.d("S4573: feedback Send to.. handoff attachments=${handoff.attachments.size}")
        activity.lifecycleScope.launch {
            // getUriForFile canonicalises the file, which is disk I/O per attachment.
            val uris = withContext(Dispatchers.IO) {
                handoff.attachments.mapNotNull { shareableUri(Uri.parse(it.uri)) }
            }
            Timber.d("S4869: feedback attachments re-addressed off main, uris=${uris.size}")
            val mimes = handoff.attachments.map { it.mimeType }.distinct()
            val mediaType = when {
                uris.isEmpty() -> MediaType.TEXT
                mimes.all { it.startsWith(IMAGE_PREFIX) } -> MediaType.IMAGE
                mimes.all { it.startsWith(AUDIO_PREFIX) } -> MediaType.AUDIO
                else -> MediaType.BINARY_OTHER
            }
            val mime = when (mediaType) {
                MediaType.TEXT -> TEXT_MIME
                MediaType.IMAGE -> mimes.singleOrNull() ?: "${IMAGE_PREFIX}*"
                MediaType.AUDIO -> mimes.singleOrNull() ?: "${AUDIO_PREFIX}*"
                else -> ANY_MIME
            }
            val content = ShareableContent(
                uris = uris,
                mime = mime,
                mediaType = mediaType,
                text = handoff.text.ifBlank { null },
                displayName = activity.getString(R.string.feedback_title),
            )
            sendToMenu.show(activity, content, handoff.settings)
        }
    }

    private fun shareableUri(uri: Uri): Uri? {
        val path = uri.path
        if (uri.scheme != FILE_SCHEME || path == null) return uri.takeIf { it.scheme != FILE_SCHEME }
        return runCatching {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", File(path))
        }.onFailure { Timber.w(it, "Feedback attachment is outside the shared cache, left out of Send to..") }
            .getOrNull()
    }

    private companion object {
        const val FILE_SCHEME = "file"
        const val IMAGE_PREFIX = "image/"
        const val AUDIO_PREFIX = "audio/"
        const val TEXT_MIME = "text/plain"
        const val ANY_MIME = "*/*"
    }
}
