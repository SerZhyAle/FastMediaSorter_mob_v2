package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import timber.log.Timber
import javax.inject.Inject

/**
 * "Open in.." receiver (S0459): hand the file to an external viewer via ACTION_VIEW + chooser,
 * mirroring the existing open-with path. Consumes only [ShareableContent].
 */
class OpenInShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val uri = content.uris.firstOrNull() ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, content.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            activity.startActivity(Intent.createChooser(view, activity.getString(R.string.open_with)))
            true
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "OpenInShareTargetHandler: no external viewer for %s", content.mime)
            false
        }
    }

    companion object {
        const val ID = "open_in"
    }
}
