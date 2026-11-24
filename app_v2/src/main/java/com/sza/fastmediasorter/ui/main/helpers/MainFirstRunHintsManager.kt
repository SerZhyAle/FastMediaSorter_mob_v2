package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.sza.fastmediasorter.core.util.UriPathResolver
import com.sza.fastmediasorter.databinding.ViewMainFirstRunHintsBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import timber.log.Timber

/**
 * Shows the main screen's first-run hints (S4860): a legend naming the Quick Sort destination marker
 * while any destination row is listed, and a first-step card with an Add button until the user adds a
 * resource of their own.
 *
 * The card latches off in preferences the first time a user-added resource is seen, so deleting that
 * resource later does not bring the card back (owner decision Q3: "then never again").
 */
class MainFirstRunHintsManager(
    private val activity: AppCompatActivity,
    private val hints: ViewMainFirstRunHintsBinding,
    private val onAddResource: () -> Unit,
) {

    private val prefs by lazy { activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val downloadsPath: String by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    fun setup() {
        hints.btnFirstStepAdd.setOnClickListenerDebounced { onAddResource() }
    }

    fun render(resources: List<MediaResource>, isTableShowing: Boolean) {
        if (isTableShowing) {
            hints.tvDestinationLegend.isVisible = false
            hints.cardFirstStepHint.isVisible = false
            return
        }
        hints.tvDestinationLegend.isVisible = MainFirstRunHintsPolicy.showDestinationLegend(resources)
        hints.cardFirstStepHint.isVisible = shouldShowFirstStepCard(resources)
        Timber.d("S4860: hints legend=${hints.tvDestinationLegend.isVisible} card=${hints.cardFirstStepHint.isVisible}")
    }

    private fun shouldShowFirstStepCard(resources: List<MediaResource>): Boolean {
        // An empty list already shows the empty-state Add prompt; a second invitation would repeat it.
        if (prefs.getBoolean(KEY_FIRST_STEP_DONE, false) || !MainFirstRunHintsPolicy.hasAnyResource(resources)) {
            return false
        }
        val userAdded = MainFirstRunHintsPolicy.hasUserAddedResource(resources, downloadsPath, ::resolvePath)
        if (userAdded) {
            prefs.edit { putBoolean(KEY_FIRST_STEP_DONE, true) }
            Timber.i("MainFirstRunHintsManager: first user-added resource seen, first-step card retired")
        }
        return !userAdded
    }

    private fun resolvePath(path: String): String? = UriPathResolver.getPath(activity, Uri.parse(path))

    private companion object {
        const val PREFS_NAME = "fms_prefs"
        const val KEY_FIRST_STEP_DONE = "main_first_step_hint_done"
    }
}
