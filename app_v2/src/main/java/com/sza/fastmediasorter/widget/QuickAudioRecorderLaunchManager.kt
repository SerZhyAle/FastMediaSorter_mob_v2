package com.sza.fastmediasorter.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.permissions.permissionRationaleShort

/**
 * S0349 - decides what a Quick Audio Recorder widget tap should do, keeping all logic out of the
 * thin [QuickAudioRecorderActivity] trampoline (Rule 3).
 *
 * Toggle semantics:
 * - recording active -> stop and save;
 * - idle + permission granted -> start;
 * - idle + permission missing -> request it; on permanent denial route the user to app settings
 *   with a clear message (no silent failure, S0349 §4.2).
 */
class QuickAudioRecorderLaunchManager(
    private val activity: Activity,
    private val requestPermission: () -> Unit,
    private val finish: () -> Unit,
) {

    fun handleToggle() {
        if (QuickAudioRecorderService.isRecording) {
            QuickAudioRecorderService.stop(activity)
            finish()
            return
        }
        if (hasRecordAudioPermission()) {
            QuickAudioRecorderService.start(activity)
            finish()
        } else {
            requestPermission()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            QuickAudioRecorderService.start(activity)
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
        ) {
            toast(activity.permissionRationaleShort(Manifest.permission.RECORD_AUDIO))
        } else {
            toast(activity.getString(R.string.quick_recorder_permission_settings))
            openAppSettings()
        }
        finish()
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
