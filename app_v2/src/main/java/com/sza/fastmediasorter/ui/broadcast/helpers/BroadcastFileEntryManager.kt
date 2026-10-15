package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import timber.log.Timber

/**
 * S4234: the idle-state entry that broadcasts a stored file. The system document picker hands the
 * picked uri to the controller; the button is shown only where capture exists at all and only while
 * no session is on the air - a file session renders through the same live layout as a captured one,
 * and while one runs the entry has nothing to offer.
 *
 * Owned by [BroadcastControlManager] like the screen's other launchers: registered on the
 * activity's own ActivityResultRegistry and dropped in onDetach, so the singleton never retains
 * a destroyed activity.
 */
class BroadcastFileEntryManager {

    private var filePickerLauncher: ActivityResultLauncher<Array<String>>? = null

    fun setup(
        controller: BroadcastSourceController,
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
    ) {
        filePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                Timber.d("BroadcastFileEntryManager: file picked, handing it to the controller")
                controller.startFile(uri)
            }
        }
        binding.btnBroadcastFile.setOnClickListener {
            filePickerLauncher?.launch(arrayOf(MIME_AUDIO, MIME_VIDEO))
        }
    }

    /** Idle and visible only where the controller can broadcast; hidden, not disabled, otherwise. */
    fun render(
        controller: BroadcastSourceController,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        val visible = controller.isAvailable && state !is BroadcastState.Live
        binding.btnBroadcastFile.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun onDetach() {
        filePickerLauncher = null
    }

    private companion object {
        const val MIME_AUDIO = "audio/*"
        const val MIME_VIDEO = "video/*"
    }
}
