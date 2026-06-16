package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.GoogleLensShare
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import javax.inject.Inject

/**
 * Google Lens receiver (S0459): hand the image Uri to Lens via the host-agnostic [GoogleLensShare],
 * which targets the Lens packages with a system-chooser fallback. The menu supplies a prepared,
 * image-typed Uri (applicableTypes gates this to images); returns false when there is no Uri.
 */
class LensShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val uri = content.uris.firstOrNull() ?: return false
        GoogleLensShare.shareImageUri(activity, uri)
        return true
    }

    companion object {
        const val ID = "lens"
    }
}
