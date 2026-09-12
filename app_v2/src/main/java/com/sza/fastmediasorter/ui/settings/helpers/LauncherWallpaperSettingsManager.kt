package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensLabelFormatter
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S1101/S2076: owns the wallpaper source flows - the image pick, the CAMERA grant and the lens picker.
 *
 * S2730 detached it from any one binding. It used to also own the wallpaper dropdown inside the launcher
 * settings dialog, which made it unusable from the wallpaper screen that replaced that dropdown; the modes
 * offered by a surface are now that surface's business and only the source flows are shared, because they
 * are the part that costs a permission, a picker dialog and a camera enumeration.
 *
 * [onSelectionAbandoned] fires whenever a flow ends without a choice - a cancelled pick, a refused grant,
 * a device that lists no lens - so the caller can put its own control back on the stored mode. Without it
 * a surface would be left showing a mode the settings never received.
 */
class LauncherWallpaperSettingsManager(
    private val host: DialogFragment,
    private val hasCamera: () -> Boolean,
    private val launchImagePicker: () -> Unit,
    private val requestCameraPermission: () -> Unit,
    private val applyCameraLens: (lensId: String, isInstantPhoto: Boolean) -> Unit,
    private val onSelectionAbandoned: () -> Unit,
) {
    /** The modes this device can actually offer - the camera pair is dropped without camera hardware. */
    val offeredModes: List<String> by lazy {
        AppSettings.LAUNCHER_WALLPAPER_MODES.filter { mode ->
            mode !in CAMERA_MODES || hasCamera()
        }
    }

    /** Starts whatever the chosen mode needs before it can be stored; other modes need nothing. */
    fun beginSourceSelection(mode: String) {
        when (mode) {
            AppSettings.LAUNCHER_WALLPAPER_IMAGE -> launchImagePicker()
            AppSettings.LAUNCHER_WALLPAPER_CAMERA -> beginCameraSelection(false)
            AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> beginCameraSelection(true)
            else -> Unit
        }
    }

    /** True when [mode] cannot be applied until a source is chosen. */
    fun needsSource(mode: String): Boolean = mode in SOURCE_MODES

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) showCameraLensPicker() else onSelectionAbandoned()
    }

    private fun beginCameraSelection(isInstantPhoto: Boolean) {
        val granted = ContextCompat.checkSelfPermission(host.requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) showCameraLensPicker(isInstantPhoto) else requestCameraPermission()
    }

    private fun showCameraLensPicker(isInstantPhoto: Boolean = false) {
        val context = host.requireContext()
        host.viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching {
                    val provider = ProcessCameraProvider.getInstance(context).get()
                    CameraLensEnumerationManager().let { manager -> manager.select(manager.expand(provider)) }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Timber.e(error, "Launcher wallpaper: camera lenses could not be listed")
                    emptyList()
                }
            }
            if (entries.isEmpty()) {
                onSelectionAbandoned()
                return@launch
            }
            val labels = entries.map { CameraLensLabelFormatter().label(context, it, entries) }.toTypedArray()
            val selected = CameraLensEnumerationManager().initialLensIndex(entries)
            var chosen = selected
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.launcher_settings_wallpaper_camera_lens_title)
                .setSingleChoiceItems(labels, selected) { _, which -> chosen = which }
                .setPositiveButton(R.string.ok) { _, _ ->
                    entries.getOrNull(chosen)?.let { applyCameraLens(it.id, isInstantPhoto) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> onSelectionAbandoned() }
                .setOnCancelListener { onSelectionAbandoned() }
                .create()
                .showBoundTo(host.viewLifecycleOwner)
        }
    }

    companion object {
        /** The label a surface shows for a mode token. */
        fun labelOf(mode: String): Int = when (mode) {
            AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> R.string.launcher_settings_wallpaper_static_stripes
            AppSettings.LAUNCHER_WALLPAPER_NONE -> R.string.launcher_settings_wallpaper_none
            AppSettings.LAUNCHER_WALLPAPER_IMAGE -> R.string.launcher_settings_wallpaper_image
            AppSettings.LAUNCHER_WALLPAPER_CAMERA -> R.string.launcher_settings_wallpaper_camera
            AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> R.string.launcher_wallpaper_mode_instant_photo
            else -> R.string.launcher_settings_wallpaper_branded
        }

        /** S2730: the sentence that says what a mode does, shown beside its label on the wallpaper screen. */
        fun descriptionOf(mode: String): Int = when (mode) {
            AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> R.string.launcher_wallpaper_desc_static_stripes
            AppSettings.LAUNCHER_WALLPAPER_NONE -> R.string.launcher_wallpaper_desc_none
            AppSettings.LAUNCHER_WALLPAPER_IMAGE -> R.string.launcher_wallpaper_desc_image
            AppSettings.LAUNCHER_WALLPAPER_CAMERA -> R.string.launcher_wallpaper_desc_camera
            AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> R.string.launcher_wallpaper_desc_instant_photo
            else -> R.string.launcher_wallpaper_desc_branded
        }

        private val CAMERA_MODES = setOf(
            AppSettings.LAUNCHER_WALLPAPER_CAMERA,
            AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
        )

        private val SOURCE_MODES = CAMERA_MODES + AppSettings.LAUNCHER_WALLPAPER_IMAGE
    }
}
