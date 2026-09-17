package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import java.util.Locale

/**
 * S2922: manages map, sharing, and clipboard actions for the Tourist dashboard.
 */
class TouristActionsManager(
    private val context: Context,
) {

    fun openMap(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            val uriStr = String.format(Locale.US, "geo:%f,%f?q=%f,%f", latitude, longitude, latitude, longitude)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
            tryStartActivity(intent) {
                // Fallback to web maps URL if no dedicated maps app is installed
                val webUri = String.format(Locale.US, "https://www.google.com/maps?q=%f,%f", latitude, longitude)
                tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
            }
        } else {
            val genericGeo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            tryStartActivity(genericGeo) {
                Toast.makeText(context, R.string.tourist_status_no_fix, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLocation(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            val mapUrl = String.format(Locale.US, "https://maps.google.com/?q=%f,%f", latitude, longitude)
            val latStr = String.format(Locale.US, "%.5f", latitude)
            val lonStr = String.format(Locale.US, "%.5f", longitude)
            val shareBody = context.getString(R.string.tourist_share_template, latStr, lonStr) + "\n" + mapUrl

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.tourist_share_subject))
                putExtra(Intent.EXTRA_TEXT, shareBody)
            }
            val chooser = Intent.createChooser(sendIntent, context.getString(R.string.tourist_action_share_location))
            tryStartActivity(chooser)
        } else {
            Toast.makeText(context, R.string.tourist_status_no_fix, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyCoordinates(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            val text = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Coordinates", text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * S3216: opens the distress signal from the Tourist dashboard.
     *
     * The route's own intent builder rather than a hand-built one, so the dashboard button, the programs
     * menu and a launcher cell all land on the same window with the same flags.
     */
    fun launchSos() {
        tryStartActivity(AppLaunchPanelRouteIntents.sos(context))
    }

    private fun tryStartActivity(intent: Intent, onFallback: (() -> Unit)? = null) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            onFallback?.invoke()
        }
    }
}
