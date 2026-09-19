package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetBinaryFileBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.transfer.SendToDeviceActionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the "send to my device (Drive)" entry of the file action sheet.
 *
 * The upload itself lives in [SendToDeviceActionManager]; this class only binds the row and reports
 * the outcome, so the sheet stays free of transfer logic (CLAUDE.md Rule 3).
 */
@Singleton
class BrowseSendToDeviceMenuAction @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionManager: SendToDeviceActionManager
) : BrowseBinaryFileMenuAction {

    // UI only - the upload runs on the activity's scope and must not outlive it.
    private var activityRef: WeakReference<ComponentActivity> = WeakReference(null)

    override fun registerLaunchers(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    override fun bind(view: View, mediaFile: MediaFile, onDismiss: () -> Unit) {
        val button = BottomSheetBinaryFileBinding.bind(view).btnSendToDevice
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            onDismiss()
            startSend(mediaFile)
        }
    }

    private fun startSend(mediaFile: MediaFile) {
        val activity = activityRef.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        Toast.makeText(context, R.string.cross_device_transfer_sending, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val sent = actionManager.send(
                filePath = mediaFile.path,
                senderDeviceName = Build.MODEL ?: UNKNOWN_DEVICE_NAME
            )
            val message = if (sent.isSuccess) {
                R.string.cross_device_transfer_sent
            } else {
                R.string.cross_device_transfer_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            actionManager.consumeState()
        }
    }

    private companion object {
        const val UNKNOWN_DEVICE_NAME = "Android"
    }
}
