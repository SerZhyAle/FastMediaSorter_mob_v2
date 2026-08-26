package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.asLaunchOutcome
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import timber.log.Timber
import javax.inject.Inject

/**
 * "Open in.." receiver (S0459): hand the file to an external viewer via ACTION_VIEW + chooser,
 * mirroring the existing open-with path. Consumes only [ShareableContent].
 */
class OpenInShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    override suspend fun send(activity: Activity, content: ShareableContent): ShareTargetOutcome =
        open(activity, content).asLaunchOutcome()

    /**
     * The chooser launch itself, kept non-suspending because it only starts an Activity (S1884).
     *
     * Two callers reach this receiver outside the «Send to..» registry - the browse binary-file sheet
     * and the file-info one-tap open - and both are synchronous. They call this directly rather than
     * being turned into coroutines for a path that never waits on anything.
     *
     * @return true when a viewer was launched.
     */
    fun open(activity: Activity, content: ShareableContent): Boolean {
        val uri = content.uris.firstOrNull() ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, content.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val hasViewer = activity.packageManager.queryIntentActivitiesCompat(
            view,
            PackageManager.MATCH_DEFAULT_ONLY,
        ).isNotEmpty()
        return when {
            !hasViewer -> {
                Timber.w("OpenInShareTargetHandler: no viewer for %s", content.mime)
                false
            }
            else -> try {
                activity.startActivity(Intent.createChooser(view, activity.getString(R.string.open_with)))
                true
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "OpenInShareTargetHandler: no external viewer for %s", content.mime)
                false
            }
        }
    }

    companion object {
        const val ID = "open_in"
    }
}
