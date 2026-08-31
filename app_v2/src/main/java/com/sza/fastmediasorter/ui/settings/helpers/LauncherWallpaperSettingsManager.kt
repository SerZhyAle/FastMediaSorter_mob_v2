package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensLabelFormatter
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Owns the wallpaper row and its camera-specific recovery flow. */
class LauncherWallpaperSettingsManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherSettingsBinding,
    private val hasCamera: () -> Boolean,
    private val currentSettings: () -> AppSettings,
    private val isUpdating: () -> Boolean,
    private val applyMode: (String) -> Unit,
    private val launchImagePicker: () -> Unit,
    private val requestCameraPermission: () -> Unit,
    private val applyCameraLens: (lensId: String, isInstantPhoto: Boolean) -> Unit,
) {
    private val offeredModes: List<String> by lazy {
        AppSettings.LAUNCHER_WALLPAPER_MODES.filter { mode ->
            mode !in CAMERA_MODES || hasCamera()
        }
    }

    fun setupRow() {
        binding.rowLauncherWallpaper.setEntries(offeredModes.map { host.getText(labelOf(it)) })
        binding.rowLauncherWallpaper.setOnItemSelectedListener { index ->
            if (isUpdating()) return@setOnItemSelectedListener
            when (val mode = offeredModes.getOrElse(index) { AppSettings.LAUNCHER_WALLPAPER_BRANDED }) {
                AppSettings.LAUNCHER_WALLPAPER_IMAGE -> launchImagePicker()
                AppSettings.LAUNCHER_WALLPAPER_CAMERA -> beginCameraSelection(false)
                AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> beginCameraSelection(true)
                else -> applyMode(mode)
            }
        }
    }

    fun render(settings: AppSettings) {
        val index = offeredModes.indexOf(settings.launcherWallpaperMode).coerceAtLeast(0)
        binding.rowLauncherWallpaper.setSelection(index)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) showCameraLensPicker() else render(currentSettings())
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
                render(currentSettings())
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
                .setNegativeButton(R.string.cancel) { _, _ -> render(currentSettings()) }
                .setOnCancelListener { render(currentSettings()) }
                .create()
                .showBoundTo(host.viewLifecycleOwner)
        }
    }

    private fun labelOf(mode: String): Int = when (mode) {
        AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> R.string.launcher_settings_wallpaper_static_stripes
        AppSettings.LAUNCHER_WALLPAPER_NONE -> R.string.launcher_settings_wallpaper_none
        AppSettings.LAUNCHER_WALLPAPER_IMAGE -> R.string.launcher_settings_wallpaper_image
        AppSettings.LAUNCHER_WALLPAPER_CAMERA -> R.string.launcher_settings_wallpaper_camera
        AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> R.string.launcher_wallpaper_mode_instant_photo
        else -> R.string.launcher_settings_wallpaper_branded
    }

    private companion object {
        val CAMERA_MODES = setOf(
            AppSettings.LAUNCHER_WALLPAPER_CAMERA,
            AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
        )
    }
}
