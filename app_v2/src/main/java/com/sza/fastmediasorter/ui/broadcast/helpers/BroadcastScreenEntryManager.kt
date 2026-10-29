package com.sza.fastmediasorter.ui.broadcast.helpers

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import timber.log.Timber

/**
 * S4233: the idle-state entry that broadcasts the phone's own screen. The platform asks for consent on
 * every tap - the grant is good for one session and is never kept - and an accepted result goes to the
 * controller, which hands it to the screen service. A declined one leaves the screen idle.
 *
 * Owned by [BroadcastControlManager] like the screen's other launchers: registered on the activity's own
 * ActivityResultRegistry and dropped in onDetach, so the singleton never retains a destroyed activity.
 */
class BroadcastScreenEntryManager {

    private var consentLauncher: ActivityResultLauncher<Intent>? = null

    fun setup(
        controller: BroadcastSourceController,
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        preparedStartGuard: BroadcastPreparedStartGuard,
    ) {
        consentLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                controller.startScreen(result.resultCode, data)
            } else {
                Toast.makeText(activity, R.string.msg_operation_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnBroadcastScreen.setOnClickListener {
            preparedStartGuard.confirm(activity, BroadcastMode.VIDEO_ONLY) { requestConsent(activity) }
        }
    }

    private fun requestConsent(activity: AppCompatActivity) {
        val projectionManager = activity.getSystemService(MediaProjectionManager::class.java)
        if (projectionManager != null) {
            consentLauncher?.launch(consentIntent(projectionManager))
        } else {
            Timber.w("BroadcastScreenEntryManager: no MediaProjectionManager on this device")
        }
    }

    /**
     * The whole screen is what this source offers: from API 34 the dialog would otherwise default to
     * "a single app", and a stream of one app's window that goes black when the user leaves it reads as a
     * broken broadcast.
     */
    private fun consentIntent(manager: MediaProjectionManager): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }

    /** Idle and visible only where the controller can broadcast; hidden, not disabled, otherwise. */
    fun render(
        controller: BroadcastSourceController,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        val visible = controller.isAvailable && state !is BroadcastState.Live
        binding.btnBroadcastScreen.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun onDetach() {
        consentLauncher = null
    }
}
