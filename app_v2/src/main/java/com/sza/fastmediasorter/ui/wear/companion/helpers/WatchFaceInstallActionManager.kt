package com.sza.fastmediasorter.ui.wear.companion.helpers

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WatchFaceLinks
import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.wear.companion.WatchFaceInstallViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

/**
 * S4009: the "Get the watch face" action, identical in the Operations Wear card and the companion
 * window.
 *
 * Construct it from the host's `onViewCreated` or later: it starts collecting on the fragment's view
 * lifecycle straight away, and the host must be a Hilt entry point for the view model to resolve.
 * When the watch cannot take the request, the web listing opens on the phone instead, so every
 * dead end still leaves the user one step from the face.
 */
class WatchFaceInstallActionManager(private val fragment: Fragment) {

    private val viewModel = ViewModelProvider(fragment)[WatchFaceInstallViewModel::class.java]

    init {
        fragment.collectOnLifecycle(viewModel.events) { render(it) }
    }

    fun install() {
        viewModel.install()
    }

    private fun render(result: WatchFaceOpenResult) {
        when (result) {
            is WatchFaceOpenResult.OpenedOnWatch ->
                toast(fragment.getString(R.string.wear_watchface_opened, result.watchName))
            WatchFaceOpenResult.NoWatch -> {
                toast(fragment.getString(R.string.wear_watchface_no_watch))
                openWebListing()
            }
            WatchFaceOpenResult.WatchStoreUnavailable -> {
                toast(fragment.getString(R.string.wear_watchface_no_store))
                openWebListing()
            }
            WatchFaceOpenResult.Failed ->
                toast(fragment.getString(R.string.friendly_copy_error_generic))
        }
    }

    private fun openWebListing() {
        try {
            fragment.startActivity(SupportIntentFactory.openUrl(WatchFaceLinks.WEB_URL))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the watch face listing")
            toast(fragment.getString(R.string.settings_no_browser_for_docs))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
