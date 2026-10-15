package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetBinaryFileBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelOff
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.model.transfer.PacketTooLarge
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevice
import com.sza.fastmediasorter.ui.browse.transfer.SendToDeviceActionManager
import com.sza.fastmediasorter.ui.common.dialog.AppDialog
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
        activity.lifecycleScope.launch {
            val devices = actionManager.targets().getOrDefault(emptyList())
            when {
                !actionManager.isChannelOn() -> toast(context.getString(R.string.drive_channel_off_hint))
                devices.isEmpty() -> toast(context.getString(R.string.drive_channel_send_no_devices))
                else -> pickTarget(activity, mediaFile, devices)
            }
        }
    }

    // S4231: one own device or any of them; the list is the device records on Drive (strategic 3.4).
    private fun pickTarget(activity: ComponentActivity, mediaFile: MediaFile, devices: List<OwnDriveDevice>) {
        val labels = listOf(context.getString(R.string.drive_channel_send_any)) + devices.map { it.displayName }
        AppDialog.singleChoice(
            owner = activity,
            context = activity,
            title = context.getString(R.string.drive_channel_send_title),
            items = labels,
            selectedIndex = 0,
            searchable = false
        ) { which ->
            val target = devices.getOrNull(which - 1)?.let { CrossDeviceTarget(it.deviceId, it.displayName) }
            send(activity, mediaFile, target)
        }
    }

    private fun send(activity: ComponentActivity, mediaFile: MediaFile, target: CrossDeviceTarget?) {
        toast(context.getString(R.string.cross_device_transfer_sending))
        activity.lifecycleScope.launch {
            val sent = actionManager.send(filePath = mediaFile.path, target = target)
            toast(outcomeText(sent.exceptionOrNull(), target))
            actionManager.consumeState()
        }
    }

    private fun outcomeText(error: Throwable?, target: CrossDeviceTarget?): String = when (error) {
        null -> target?.let { context.getString(R.string.drive_channel_send_waiting_for, it.displayName) }
            ?: context.getString(R.string.drive_channel_send_waiting_any)
        is DriveChannelOff -> context.getString(R.string.drive_channel_off_hint)
        is DriveSignInRequired -> context.getString(R.string.drive_channel_sign_in_required)
        is DriveDailyLimit -> context.getString(R.string.drive_channel_daily_limit)
        is DriveQueueFull -> context.getString(
            R.string.drive_channel_not_enough_space,
            Formatter.formatShortFileSize(context, error.freeBytes ?: 0L)
        )
        is PacketTooLarge -> context.getString(
            R.string.drive_channel_too_large,
            Formatter.formatShortFileSize(context, error.limitBytes)
        )
        else -> context.getString(R.string.cross_device_transfer_failed)
    }

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
