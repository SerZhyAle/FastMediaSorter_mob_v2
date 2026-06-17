package com.sza.fastmediasorter.core.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.File

/**
 * Host-agnostic "share image to Google Lens" (Context + FileProvider only), originally S0393 wave-C.
 *
 * S0459: relocated from `ui/player/helpers` to `core/share` (it carries no UI state) and given a
 * Uri-based entry [shareImageUri] so the unified «Send to..» Lens receiver can reuse it with an
 * already-resolved Uri without the core layer depending on the UI layer.
 */
object GoogleLensShare {

    /** Share a local image [file] (FileProvider-wrapped) to Google Lens. */
    fun shareImageFile(activity: Activity, file: File) {
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            shareImageUri(activity, uri)
        } catch (e: IllegalArgumentException) {
            // FileProvider rejects a file outside its configured paths.
            Timber.e(e, "GoogleLensShare: failed to resolve %s", file.name)
            Toast.makeText(activity, R.string.toast_error_google_lens, Toast.LENGTH_SHORT).show()
        }
    }

    /** Share an already-resolved image [uri] to Google Lens; system chooser fallback when absent. */
    fun shareImageUri(activity: Activity, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(null, uri)
            }
            val pm = activity.packageManager
            for (pkg in listOf("com.google.ar.lens", "com.google.android.googlequicksearchbox")) {
                intent.setPackage(pkg)
                if (intent.resolveActivity(pm) != null) {
                    activity.startActivity(intent)
                    return
                }
            }
            intent.setPackage(null)
            activity.startActivity(
                Intent.createChooser(intent, activity.getString(R.string.enable_google_lens))
            )
        } catch (e: ActivityNotFoundException) {
            // No app (not even the chooser) could handle the image send.
            Timber.e(e, "GoogleLensShare: failed to share image to Lens")
            Toast.makeText(activity, R.string.toast_error_google_lens, Toast.LENGTH_SHORT).show()
        }
    }
}
