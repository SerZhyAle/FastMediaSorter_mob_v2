package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker
import javax.inject.Inject

/**
 * Google Keep (drawing) receiver (S0459): send the exported drawing image to a resolved Keep client.
 * The menu prepares the merged image Uri; this just targets Keep, chooser fallback when absent.
 */
class KeepDrawingShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val keepPackage = GoogleKeepAvailabilityChecker(activity).resolveTargetPackage() ?: return false
        return SystemShareInvoker.invokeFiles(
            context = activity,
            // Honour the surface's declared MIME (PNG/WEBP/BMP images keep their type); fall back to a
            // generic image type rather than mislabelling every image as JPEG.
            mime = content.mime.ifBlank { MIME_IMAGE_FALLBACK },
            uris = content.uris,
            preferredPackage = keepPackage,
        )
    }

    companion object {
        const val ID = "keep_drawing"
        private const val MIME_IMAGE_FALLBACK = "image/*"
    }
}
