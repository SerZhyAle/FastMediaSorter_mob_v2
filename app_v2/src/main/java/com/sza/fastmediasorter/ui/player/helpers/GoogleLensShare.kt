package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.File

/**
 * S0393 wave-C: host-agnostic "share image to Google Lens" (Context + FileProvider only), extracted
 * from the PlayerActivity-bound PlayerShareManager so any standalone host can reuse it.
 */
object GoogleLensShare {

    fun shareImageFile(activity: Activity, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
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
        } catch (e: Exception) {
            Timber.e(e, "GoogleLensShare: failed to share %s", file.name)
            Toast.makeText(activity, R.string.toast_error_google_lens, Toast.LENGTH_SHORT).show()
        }
    }
}
