package com.sza.fastmediasorter.ui.settings

import android.Manifest
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherWallpaperSettingsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.usecase.launcher.IsCameraWallpaperAvailableUseCase
import com.sza.fastmediasorter.ui.settings.helpers.LauncherWallpaperScreenManager
import com.sza.fastmediasorter.ui.settings.helpers.LauncherWallpaperSettingsManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S2730: the desktop wallpaper's own screen, split out of the single row it used to occupy inside
 * [LauncherSettingsDialogFragment].
 *
 * A full-screen dialog rather than a section of `SettingsActivity`: that activity is shared with every
 * build variant, while the wallpaper exists only where the launcher source set is mounted, and the
 * neighbouring launcher dialog already holds that seam through [LauncherModeContract.isAvailableInBuild].
 *
 * Every control applies immediately, as the launcher settings dialog does - the confirm button only
 * dismisses.
 */
@AndroidEntryPoint
class LauncherWallpaperSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogLauncherWallpaperSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    @Inject
    lateinit var isCameraWallpaperAvailable: IsCameraWallpaperAvailableUseCase

    // Holds the binding, so it is released with the view rather than with the fragment.
    private var screenManager: LauncherWallpaperScreenManager? = null
    private var sourceManager: LauncherWallpaperSettingsManager? = null

    // Guards render() writes so a slider or a radio never bounces back into a settings update.
    private var isUpdatingFromSettings = false

    private val pickWallpaperImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                screenManager?.render(viewModel.settings.value)
                return@registerForActivityResult
            }
            viewModel.applyLauncherWallpaperImage(uri)
        }

    private val requestCameraForWallpaper =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            sourceManager?.onCameraPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_FastMediaSorter_Dialog_FullScreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogLauncherWallpaperSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Defensive: never leave a functional launcher surface reachable on a build without the launcher.
        if (!launcherModeContract.isAvailableInBuild) {
            dismiss()
            return
        }
        Timber.d("S2730: wallpaper screen opened mode=${viewModel.settings.value.launcherWallpaperMode}")
        binding.btnClose.setOnClickListener { dismiss() }
        val source = LauncherWallpaperSettingsManager(
            host = this,
            hasCamera = isCameraWallpaperAvailable::hasHardware,
            launchImagePicker = { pickWallpaperImage.launch(WALLPAPER_MIME_TYPES) },
            requestCameraPermission = { requestCameraForWallpaper.launch(Manifest.permission.CAMERA) },
            applyCameraLens = { lensId, isInstantPhoto ->
                if (isInstantPhoto) {
                    viewModel.applyLauncherWallpaperInstantPhoto(lensId)
                } else {
                    viewModel.applyLauncherWallpaperCamera(lensId)
                }
            },
            onSelectionAbandoned = { screenManager?.render(viewModel.settings.value) },
        )
        sourceManager = source
        screenManager = LauncherWallpaperScreenManager(
            host = this,
            binding = binding,
            sourceManager = source,
            currentSettings = { viewModel.settings.value },
            isUpdating = { isUpdatingFromSettings },
            applyMode = viewModel::applyLauncherWallpaperMode,
            applyTuning = ::applyTuning,
            applyPalette = { palette ->
                viewModel.updateSettings(viewModel.settings.value.withLauncher { copy(animationPalette = palette) })
            },
        ).also { it.setup() }

        viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            screenManager?.render(settings)
            isUpdatingFromSettings = false
        }
        viewLifecycleOwner.collectOnLifecycle(viewModel.launcherWallpaperImportFailed) {
            // The stored mode never changed, so the chosen row has to be walked back off "My image".
            screenManager?.render(viewModel.settings.value)
            Snackbar.make(
                binding.root,
                R.string.launcher_settings_wallpaper_import_failed,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * One write per edit: a slider moves one value and the other two are read back from the settings the
     * screen is already rendering, so two sliders dragged in quick succession cannot overwrite each other.
     */
    private fun applyTuning(intensity: Float?, speed: Float?, density: Float?) {
        Timber.d("S2730: wallpaper tuning write intensity=$intensity speed=$speed density=$density")
        val settings = viewModel.settings.value
        viewModel.updateSettings(
            settings.withLauncher {
                copy(
                    wallpaperIntensity = intensity ?: wallpaperIntensity,
                    wallpaperAnimationSpeed = speed ?: wallpaperAnimationSpeed,
                    wallpaperParticleDensity = density ?: wallpaperParticleDensity,
                )
            }
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        screenManager?.release()
        screenManager = null
        sourceManager = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LauncherWallpaperSettingsDialog"

        private val WALLPAPER_MIME_TYPES = arrayOf("image/*")

        fun newInstance(): LauncherWallpaperSettingsDialogFragment =
            LauncherWallpaperSettingsDialogFragment()
    }
}
