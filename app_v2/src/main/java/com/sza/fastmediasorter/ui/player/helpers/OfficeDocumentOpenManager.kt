package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import timber.log.Timber
import java.io.File

/**
 * Opens passive Office documents through installed apps. FMS is excluded from the
 * chooser candidates so a default-document alias cannot launch itself recursively.
 */
object OfficeDocumentOpenManager {

    fun openPreparedFile(activity: Activity, file: File, displayName: String = file.name): Boolean {
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        return openUri(activity, uri, displayName)
    }

    fun openUri(activity: Activity, uri: Uri, displayName: String, mimeType: String? = null): Boolean {
        val resolvedMimeType = mimeType
            ?: MediaTypeUtils.officeMimeTypeForFileName(displayName)
            ?: "application/octet-stream"

        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(activity.contentResolver, displayName, uri)
        }

        val candidates = queryCandidates(activity, baseIntent)
            .filter { it.activityInfo?.packageName != activity.packageName }

        if (candidates.isEmpty()) {
            Timber.w("OfficeDocumentOpenManager: no external viewer for $displayName ($resolvedMimeType)")
            return false
        }

        val explicitIntents = candidates.map { resolveInfo ->
            val info = resolveInfo.activityInfo
            Intent(baseIntent).apply {
                component = ComponentName(info.packageName, info.name)
                setPackage(info.packageName)
            }
        }

        grantReadPermission(activity, uri, explicitIntents)

        val launchIntent = if (explicitIntents.size == 1) {
            explicitIntents.first()
        } else {
            Intent.createChooser(explicitIntents.first(), activity.getString(R.string.open_with)).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, explicitIntents.drop(1).toTypedArray())
            }
        }

        return try {
            activity.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Timber.e(e, "OfficeDocumentOpenManager: failed to launch viewer for $displayName")
            false
        }
    }

    private fun queryCandidates(activity: Activity, intent: Intent) =
        activity.packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY)

    private fun grantReadPermission(activity: Activity, uri: Uri, intents: List<Intent>) {
        intents.forEach { intent ->
            val packageName = intent.component?.packageName ?: return@forEach
            try {
                activity.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Timber.d(e, "OfficeDocumentOpenManager: read grant skipped for $packageName")
            }
        }
    }
}
