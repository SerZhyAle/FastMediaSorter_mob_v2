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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.util.ApkInstallFailure
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * noLegal APK install action for the Browse binary-file bottom sheet.
 *
 * Uses system install UI (ACTION_INSTALL_PACKAGE + EXTRA_RETURN_RESULT).
 * Silent install via session API is forbidden - S0183 §3, S0156 ADR-4.
 *
 * S0183: APK install from Browse (noLegal only).
 * S0266: cloud APK paths are downloaded into the cache directory under their real `.apk` name
 *        before the system installer is invoked - no FileOperationProgressDialog, only a Toast.
 */
@Singleton
class BrowseApkInstallHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudFileOperationHandler: CloudFileOperationHandler,
) : BrowseBinaryFileMenuAction {

    // ActivityResultLaunchers - re-registered on each Activity.onCreate via registerLaunchers().
    private var installLauncher: ActivityResultLauncher<Intent>? = null
    private var settingsLauncher: ActivityResultLauncher<Intent>? = null

    // Weak reference to the current Activity - used for UI (AlertDialog, Toast, lifecycleScope) only.
    // ApplicationContext is used for PackageManager and FileProvider.
    private var activityRef: WeakReference<ComponentActivity> = WeakReference(null)

    // File waiting for permission grant from Settings.
    private var pendingFile: MediaFile? = null

    override fun registerLaunchers(activity: ComponentActivity) {
        activityRef = WeakReference(activity)

        settingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // User returned from "Install unknown apps" Settings screen.
            if (context.packageManager.canRequestPackageInstalls()) {
                pendingFile?.let { triggerInstall(it) }
            }
            pendingFile = null
        }

        installLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val act = activityRef.get() ?: return@registerForActivityResult
            val msgRes = when (result.resultCode) {
                Activity.RESULT_OK -> R.string.s0183_apk_install_success
                Activity.RESULT_CANCELED -> R.string.s0183_apk_install_cancelled
                else -> {
                    // S1686: resultCode is RESULT_FIRST_USER for every refusal and names no cause; the
                    // legacy status beside it does, and it is what a user-supplied log needs to carry.
                    val legacyStatus = result.data?.getIntExtra(
                        ApkInstallFailure.EXTRA_INSTALL_RESULT,
                        ApkInstallFailure.NO_STATUS
                    ) ?: ApkInstallFailure.NO_STATUS
                    val failure = ApkInstallFailure.fromLegacyStatus(legacyStatus)
                    Timber.w(
                        "APK install failed - resultCode=${result.resultCode}, " +
                            "installResult=$legacyStatus ($failure)"
                    )
                    ApkInstallFailureMapper.messageRes(failure)
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
        // Dismiss the bottom sheet immediately - install flow continues independently.
        onDismiss()

        val act = activityRef.get()
        if (act == null || act.isFinishing || act.isDestroyed) {
            Timber.w("showInstallMenu called with no valid activity - ignoring")
            return
        }

        if (context.packageManager.canRequestPackageInstalls()) {
            triggerInstall(file)
        } else {
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
                .showBoundToHost(act)
        }
    }

    private fun triggerInstall(file: MediaFile) {
        // S0266: cloud sources need a real local file before PackageInstaller can run.
        if (file.path.startsWith("cloud://")) {
            downloadAndInstallFromCloud(file)
        } else {
            launchSystemInstaller(File(file.path), file.name)
        }
    }

    /**
     * S0266: noLegal-only cloud APK install. Downloads the APK silently into cacheDir under its
     * real `.apk` name and immediately invokes the system installer. Single Toast at start, no
     * FileOperationProgressDialog. Operations occur on lifecycleScope of the current Activity.
     */
    private fun downloadAndInstallFromCloud(file: MediaFile) {
        val act = activityRef.get()
        if (act == null || act.isFinishing || act.isDestroyed) {
            return
        }
        Toast.makeText(act, R.string.s0266_apk_download_preparing, Toast.LENGTH_SHORT).show()

        val cacheApkDir = File(context.cacheDir, "apk_install").apply { mkdirs() }
        val cacheApkFile = File(cacheApkDir, file.name)

        act.lifecycleScope.launch {
            val downloaded = withContext(Dispatchers.IO) {
                runCatching {
                    cloudFileOperationHandler.downloadFromCloudToPublic(
                        cloudPath = file.path,
                        destPath = cacheApkDir.absolutePath,
                        fileName = file.name,
                    )
                }.getOrElse { e ->
                    // S1212: leaving the screen cancels this scope - normal teardown, not a download
                    // failure. Swallowing it logged an E-level stack and degraded to `false`, which
                    // then reported "install failed" for an operation the user simply walked away from.
                    if (e is CancellationException) throw e
                    Timber.e(e, "cloud APK download threw")
                    false
                }
            }
            // The installer is an activity start, so it must not fire while the host is stopped.
            act.lifecycle.withStarted {
                if (downloaded && cacheApkFile.exists() && cacheApkFile.length() > 0L) {
                    launchSystemInstaller(cacheApkFile, file.name)
                } else {
                    Timber.w("cloud APK download reported failure for ${file.name}")
                    Toast.makeText(act, R.string.s0183_apk_install_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchSystemInstaller(apkFile: File, fileName: String) {
        val act = activityRef.get()
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            // ACTION_INSTALL_PACKAGE is deprecated since API 25 and has no handler on Android 14+.
            // Modern path: ACTION_VIEW with the APK MIME type - the system PackageInstaller activity
            // registers for this intent in its manifest and shows the standard install confirmation UI.
            // EXTRA_RETURN_RESULT is honoured by PackageInstallerActivity regardless of the action.
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            installLauncher?.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "failed to launch APK install for $fileName")
            act?.let { Toast.makeText(it, R.string.s0183_apk_install_failed, Toast.LENGTH_SHORT).show() }
        }
    }
}
