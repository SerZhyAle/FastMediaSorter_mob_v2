package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * noLegal APK install action for the Browse binary-file bottom sheet.
 *
 * Uses system install UI (ACTION_INSTALL_PACKAGE + EXTRA_RETURN_RESULT).
 * Silent install via session API is forbidden — S0183 §3, S0156 ADR-4.
 *
 * S0183: APK install from Browse (noLegal only).
 */
@Singleton
class BrowseApkInstallHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BrowseBinaryFileMenuAction {

    // ActivityResultLaunchers — re-registered on each Activity.onCreate via registerLaunchers().
    private var installLauncher: ActivityResultLauncher<Intent>? = null
    private var settingsLauncher: ActivityResultLauncher<Intent>? = null

    // Weak reference to the current Activity — used for UI (AlertDialog, Toast) only.
    // ApplicationContext is used for PackageManager and FileProvider.
    private var activityRef: WeakReference<Activity> = WeakReference(null)

    // File waiting for permission grant from Settings.
    private var pendingFile: MediaFile? = null

    override fun registerLaunchers(activity: ComponentActivity) {
        activityRef = WeakReference(activity)

        settingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // User returned from "Install unknown apps" Settings screen.
            if (context.packageManager.canRequestPackageInstalls()) {
                Timber.d("S0183: permission granted via Settings — triggering install")
                pendingFile?.let { triggerInstall(it) }
            } else {
                Timber.d("S0183: permission still denied after Settings return")
            }
            pendingFile = null
        }

        installLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val act = activityRef.get() ?: return@registerForActivityResult
            val msgRes = when (result.resultCode) {
                Activity.RESULT_OK -> {
                    Timber.d("S0183: APK install completed — RESULT_OK")
                    R.string.s0183_apk_install_success
                }
                Activity.RESULT_CANCELED -> {
                    Timber.d("S0183: APK install cancelled by user — RESULT_CANCELED")
                    R.string.s0183_apk_install_cancelled
                }
                else -> {
                    Timber.w("S0183: APK install failed — resultCode=${result.resultCode}")
                    R.string.s0183_apk_install_failed
                }
            }
            Toast.makeText(act, msgRes, Toast.LENGTH_SHORT).show()
        }
    }

    override fun bind(view: View, mediaFile: MediaFile, onDismiss: () -> Unit) {
        if (!mediaFile.name.substringAfterLast('.', "").equals("apk", ignoreCase = true)) {
            return
        }

        // The button exists only in the noLegal layout overlay, so market flavors never ship this UI.
        val btnInstall = view.findViewById<View>(R.id.btnInstallApk) ?: return
        btnInstall.visibility = View.VISIBLE
        btnInstall.setOnClickListener {
            showInstallMenu(mediaFile, onDismiss)
        }
    }

    private fun showInstallMenu(file: MediaFile, onDismiss: () -> Unit) {
        Timber.d("S0183: showInstallMenu — entry point, file=${file.name}")
        // Dismiss the bottom sheet immediately — install flow continues independently.
        onDismiss()

        val act = activityRef.get()
        if (act == null || act.isFinishing || act.isDestroyed) {
            Timber.w("S0183: showInstallMenu called with no valid activity — ignoring")
            return
        }

        if (context.packageManager.canRequestPackageInstalls()) {
            Timber.d("S0183: permission already granted — triggering install directly")
            triggerInstall(file)
        } else {
            Timber.d("S0183: REQUEST_INSTALL_PACKAGES not granted — showing rationale")
            pendingFile = file
            AlertDialog.Builder(act)
                .setTitle(R.string.s0183_apk_install_rationale_title)
                .setMessage(R.string.s0183_apk_install_rationale_message)
                .setNegativeButton(R.string.s0183_apk_install_rationale_btn_cancel) { dialog, _ ->
                    dialog.dismiss()
                    pendingFile = null
                }
                .setPositiveButton(R.string.s0183_apk_install_rationale_btn_settings) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    settingsLauncher?.launch(intent)
                }
                .show()
        }
    }

    private fun triggerInstall(file: MediaFile) {
        val act = activityRef.get()
        try {
            val apkFile = File(file.path)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            // ACTION_INSTALL_PACKAGE is deprecated since API 25 and has no handler on Android 14+.
            // Modern path: ACTION_VIEW with the APK MIME type — the system PackageInstaller activity
            // registers for this intent in its manifest and shows the standard install confirmation UI.
            // EXTRA_RETURN_RESULT is honoured by PackageInstallerActivity regardless of the action.
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            Timber.d("S0183: launching ACTION_VIEW(apk) for ${file.name}")
            installLauncher?.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "S0183: failed to launch APK install for ${file.name}")
            act?.let { Toast.makeText(it, R.string.s0183_apk_install_failed, Toast.LENGTH_SHORT).show() }
        }
    }
}
