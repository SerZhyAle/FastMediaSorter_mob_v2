package com.sza.fastmediasorter.ui.main.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WatchFaceLinks
import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.model.WatchInstallOffer
import com.sza.fastmediasorter.domain.usecase.FindWatchInstallOfferUseCase
import com.sza.fastmediasorter.domain.usecase.OpenWatchAppOnWatchUseCase
import com.sza.fastmediasorter.domain.usecase.OpenWatchFaceOnWatchUseCase
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.wear.companion.helpers.WatchFaceNudgeManager
import com.sza.fastmediasorter.util.launchBoundToHost
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S4012: offers the watch app and the watch face once, the first time the home screen finds a
 * connected watch after install.
 *
 * The lookup repeats on every fresh start until a watch answers, because a watch paired after the
 * install is the common case; once the offer has been shown it never runs again. The flag is written
 * as the dialog appears, not on a button, for the same reason as [WatchFaceNudgeManager]: the owner
 * asked for one offer, and a process death before a click would otherwise repeat it. The companion
 * window's own face hint is switched off at the same moment so the face is not offered twice.
 */
class MainWatchInstallOfferManager(
    private val activity: AppCompatActivity,
    private val findOffer: FindWatchInstallOfferUseCase,
    private val openWatchApp: OpenWatchAppOnWatchUseCase,
    private val openWatchFace: OpenWatchFaceOnWatchUseCase,
) {

    // The home screen's shared preference file is already in memory by now (the Chrome OS banner reads
    // it on every resume), so the flag read below is no disk access. The use cases are main-safe: the
    // repository moves the Wear queries to IO itself.
    private val prefs by lazy {
        activity.applicationContext.getSharedPreferences(WatchFaceNudgeManager.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun offerIfNeeded() {
        if (prefs.getBoolean(KEY_OFFER_SHOWN, false)) return
        activity.lifecycleScope.launch {
            val offer = findOffer() ?: return@launch
            activity.withResumed {
                if (!prefs.getBoolean(KEY_OFFER_SHOWN, false)) show(offer)
            }
        }
    }

    private fun show(offer: WatchInstallOffer) {
        prefs.edit {
            putBoolean(KEY_OFFER_SHOWN, true)
            putBoolean(WatchFaceNudgeManager.KEY_SHOWN, true)
        }
        Timber.i("Watch install offer shown (watch app installed: ${offer.watchAppInstalled})")
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.wear_install_offer_title)
            .setNegativeButton(R.string.later, null)
        if (offer.watchAppInstalled) {
            builder
                .setMessage(activity.getString(R.string.wear_install_offer_message_face_only, offer.watchName))
                .setPositiveButton(R.string.wear_install_offer_face) { _, _ -> open(FACE) }
        } else {
            builder
                .setMessage(activity.getString(R.string.wear_install_offer_message, offer.watchName))
                .setPositiveButton(R.string.wear_install_offer_app) { _, _ -> open(WATCH_APP) }
                .setNeutralButton(R.string.wear_install_offer_face) { _, _ -> open(FACE) }
        }
        builder.showBoundToHost(activity)
    }

    private fun open(target: StoreTarget) {
        activity.launchBoundToHost {
            val result = if (target === WATCH_APP) openWatchApp() else openWatchFace()
            render(result, target)
        }
    }

    private fun render(result: WatchFaceOpenResult, target: StoreTarget) {
        when (result) {
            is WatchFaceOpenResult.OpenedOnWatch ->
                toast(activity.getString(R.string.wear_watchface_opened, result.watchName))
            WatchFaceOpenResult.NoWatch -> {
                toast(activity.getString(target.noWatchText))
                openWebListing(target.webUrl)
            }
            WatchFaceOpenResult.WatchStoreUnavailable -> {
                toast(activity.getString(target.noStoreText))
                openWebListing(target.webUrl)
            }
            WatchFaceOpenResult.Failed ->
                toast(activity.getString(R.string.friendly_copy_error_generic))
        }
    }

    private fun openWebListing(url: String) {
        try {
            activity.startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the watch store listing")
            toast(activity.getString(R.string.settings_no_browser_for_docs))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    /** What a dead end says and where the phone falls back to, per listing. */
    private class StoreTarget(
        val name: String,
        @param:StringRes val noWatchText: Int,
        @param:StringRes val noStoreText: Int,
        val webUrl: String,
    )

    private companion object {
        const val KEY_OFFER_SHOWN = "wear_install_offer_shown"

        val WATCH_APP = StoreTarget(
            name = "app",
            noWatchText = R.string.wear_install_offer_app_no_watch,
            noStoreText = R.string.wear_install_offer_app_no_store,
            webUrl = WatchFaceLinks.WATCH_APP_WEB_URL,
        )

        val FACE = StoreTarget(
            name = "face",
            noWatchText = R.string.wear_watchface_no_watch,
            noStoreText = R.string.wear_watchface_no_store,
            webUrl = WatchFaceLinks.WEB_URL,
        )
    }
}
