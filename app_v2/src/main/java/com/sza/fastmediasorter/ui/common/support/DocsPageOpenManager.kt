package com.sza.fastmediasorter.ui.common.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.input.InputHelpLinkResolver
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.common.support.DocsHelpFallbackDialogFragment.Reason
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * Opens a documentation-portal page in the external browser, or explains why it cannot.
 *
 * A TV box often ships without any browser and an offline phone would open an error page, so both
 * cases show [DocsHelpFallbackDialogFragment] with the address and a QR code the user can scan
 * from another device, instead of a silent no-op or an [ActivityNotFoundException] crash.
 */
object DocsPageOpenManager {

    /** Opens the documentation page for [surface] in the device language (English fallback). */
    fun open(activity: FragmentActivity, surface: UiSurface) {
        Timber.d("S3506: docs page open surface=$surface")
        val lang = LocaleHelper.getLanguage(activity)
        val url = InputHelpLinkResolver.urlFor(surface, lang)
        open(activity, url)
    }

    fun open(activity: FragmentActivity, url: String) {
        val intent = SupportIntentFactory.openUrl(url)
        val blocked = when {
            activity.packageManager.resolveActivityCompat(intent) == null -> Reason.NO_BROWSER
            !hasValidatedInternet(activity) -> Reason.OFFLINE
            else -> null
        }
        Timber.d("S2979: docs page open url=$url blocked=$blocked")
        val failure = blocked ?: launch(activity, intent)
        failure?.let { DocsHelpFallbackDialogFragment.show(activity.supportFragmentManager, url, it) }
    }

    private fun launch(activity: FragmentActivity, intent: Intent): Reason? =
        try {
            activity.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Docs page: resolved browser refused the launch")
            Reason.NO_BROWSER
        }

    private fun hasValidatedInternet(context: Context): Boolean {
        // No ConnectivityManager at all is a stripped-down build we cannot judge; let the browser try.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        return cm == null || caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
}
