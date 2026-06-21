package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import timber.log.Timber

/**
 * Owns the capture subgroup of the Operations tab: camera-photos, video-recording, and
 * microphone-recording rows with their destination selectors. The camera/video master toggles
 * persist inverted (`disableCameraCapture` / `disableVideoCapture`); microphone rows are gated by
 * [MediaCapabilities.supportsMicRecording]. The shared destination picker and label resolver are
 * injected from the fragment.
 */
class OperationsCaptureManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val mediaCapabilities: MediaCapabilities,
    private val recordAudioPermissionLauncher: ActivityResultLauncher<String>,
    private val isUpdatingFromSettings: () -> Boolean,
    private val pickDestination: (Long?, (MediaResource?) -> Unit) -> Unit,
    private val refreshLabel: (String?, TextView, Int) -> Unit,
    private val fragment: Fragment,
) {

    fun setup() {
        // ── Camera Photos ──
        binding.rowCameraToResourceEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            // Reuse the existing negative persistence flags instead of duplicating camera settings keys.
            viewModel.updateSettings(current.copy(disableCameraCapture = !isChecked))
            binding.layoutCameraToResourceOptions.isVisible = isChecked
        }
        binding.rowCameraAskFilename.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(skipCameraFilenameDialog = !isChecked))
        }
        binding.rowCameraOpenForEditing.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cameraCaptureOpenForEditing = isChecked))
        }
        binding.rowCameraCopyToClipboard.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cameraCaptureCopyToClipboard = isChecked))
        }
        binding.btnSelectCameraPhotosDest.setOnClickListener {
            pickDestination(
                viewModel.settings.value.cameraPhotosDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(cameraPhotosDestinationResourceId = resource?.id?.toString()))
            }
        }

        // ── Video recording ──
        // Master toggle persists inverted (disableVideoCapture), mirroring the camera-photos pattern.
        binding.rowVideoCaptureEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(disableVideoCapture = !isChecked))
            binding.layoutVideoCaptureOptions.isVisible = isChecked
        }
        binding.rowVideoCaptureOpenInPlayer.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(videoCaptureOpenInPlayer = isChecked))
        }
        binding.btnSelectVideoRecordingDest.setOnClickListener {
            pickDestination(
                viewModel.settings.value.videoRecordingDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(videoRecordingDestinationResourceId = resource?.id?.toString()))
            }
        }

        // ── Microphone recording ──
        if (!mediaCapabilities.supportsMicRecording) {
            binding.rowMicRecordingEnabled.isVisible = false
            binding.rowMicRecordingAskFilename.isVisible = false
            binding.layoutMicRecordingDestSelector.isVisible = false
        } else {
            binding.rowMicRecordingEnabled.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
                if (isChecked) {
                    if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@setOnCheckedChangeListener
                    }
                    viewModel.updateSettings(viewModel.settings.value.copy(micRecordingEnabled = true))
                } else {
                    viewModel.updateSettings(viewModel.settings.value.copy(micRecordingEnabled = false))
                }
                binding.rowMicRecordingAskFilename.isVisible = isChecked
            }
            binding.rowMicRecordingAskFilename.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(micRecordingAskFilename = isChecked))
            }
            binding.btnSelectMicRecordingDest.setOnClickListener {
                pickDestination(
                    viewModel.settings.value.micRecordingDestinationResourceId?.toLongOrNull()
                ) { resource ->
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(micRecordingDestinationResourceId = resource?.id?.toString()))
                }
            }
        }
    }

    /** Applies the latest settings to the capture rows (camera photos, video, microphone). */
    fun render(settings: AppSettings) {
        // Camera Photos rows (inverted master/ask-filename flags).
        if (binding.rowCameraToResourceEnabled.isChecked != !settings.disableCameraCapture) {
            binding.rowCameraToResourceEnabled.setCheckedSilently(!settings.disableCameraCapture)
        }
        if (binding.rowCameraAskFilename.isChecked != !settings.skipCameraFilenameDialog) {
            binding.rowCameraAskFilename.setCheckedSilently(!settings.skipCameraFilenameDialog)
        }
        if (binding.rowCameraOpenForEditing.isChecked != settings.cameraCaptureOpenForEditing) {
            binding.rowCameraOpenForEditing.setCheckedSilently(settings.cameraCaptureOpenForEditing)
        }
        if (binding.rowCameraCopyToClipboard.isChecked != settings.cameraCaptureCopyToClipboard) {
            binding.rowCameraCopyToClipboard.setCheckedSilently(settings.cameraCaptureCopyToClipboard)
        }
        binding.layoutCameraToResourceOptions.isVisible = !settings.disableCameraCapture
        refreshLabel(
            settings.cameraPhotosDestinationResourceId,
            binding.tvCameraPhotosDest,
            R.string.setting_camera_photos_destination_default_camera
        )
        // Video recording rows (master toggle inverted).
        if (binding.rowVideoCaptureEnabled.isChecked != !settings.disableVideoCapture) {
            binding.rowVideoCaptureEnabled.setCheckedSilently(!settings.disableVideoCapture)
        }
        if (binding.rowVideoCaptureOpenInPlayer.isChecked != settings.videoCaptureOpenInPlayer) {
            binding.rowVideoCaptureOpenInPlayer.setCheckedSilently(settings.videoCaptureOpenInPlayer)
        }
        binding.layoutVideoCaptureOptions.isVisible = !settings.disableVideoCapture
        refreshLabel(
            settings.videoRecordingDestinationResourceId,
            binding.tvVideoRecordingDest,
            R.string.setting_video_recording_destination_default_movies
        )
        // Microphone recording rows (feature-gated).
        if (mediaCapabilities.supportsMicRecording) {
            if (binding.rowMicRecordingEnabled.isChecked != settings.micRecordingEnabled) {
                binding.rowMicRecordingEnabled.setCheckedSilently(settings.micRecordingEnabled)
            }
            if (binding.rowMicRecordingAskFilename.isChecked != settings.micRecordingAskFilename) {
                binding.rowMicRecordingAskFilename.setCheckedSilently(settings.micRecordingAskFilename)
            }
            binding.rowMicRecordingAskFilename.isVisible = settings.micRecordingEnabled
            refreshLabel(
                settings.micRecordingDestinationResourceId,
                binding.tvMicRecordingDest,
                R.string.setting_mic_recording_destination_default_downloads
            )
        }
    }

    /**
     * S0559: store-safe menu screenshot action. The card is shown only when a [MenuScreenshotLauncher]
     * is bound (standard + noLegal); it stays gone on flavors without the capture engine, where the
     * injected set is empty.
     */
    fun setupScreenshotAction(launchers: Set<MenuScreenshotLauncher>, activity: Activity) {
        val launcher = launchers.firstOrNull()
        binding.groupMenuScreenshot.isVisible = launcher != null
        if (launcher != null) {
            binding.btnTakeScreenshotNow.setOnClickListener { launcher.launch(activity) }
        }
    }
}
