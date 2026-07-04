package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.TooltipCompat
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
    // S0774: true when a ScreenVideoRecordingController is bound (standard fms.screenCapture=on + noLegal).
    private val isScreenRecordingAvailable: Boolean,
    private val recordAudioPermissionLauncher: ActivityResultLauncher<String>,
    private val locationPermissionLauncher: ActivityResultLauncher<String>,
    private val isUpdatingFromSettings: () -> Boolean,
    private val pickDestination: (Long?, (MediaResource?) -> Unit) -> Unit,
    private val refreshLabel: (String?, Int, (CharSequence) -> Unit) -> Unit,
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
        // S0766: geotag is opt-in and persists only after ACCESS_FINE_LOCATION is granted (mic pattern):
        // enabling without the grant requests it; the fragment's launcher persists the flag on grant and
        // reverts the row on denial, so coordinates are never collected without explicit consent.
        binding.rowCameraGeotag.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            if (isChecked && ContextCompat.checkSelfPermission(
                    fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@setOnCheckedChangeListener
            }
            viewModel.updateSettings(viewModel.settings.value.copy(cameraGeotagEnabled = isChecked))
        }
        // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
        TooltipCompat.setTooltipText(binding.btnSelectCameraPhotosDest, binding.btnSelectCameraPhotosDest.contentDescription)
        Timber.d("S0842: camera-photos dest picker is icon-only")
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
        // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
        TooltipCompat.setTooltipText(binding.btnSelectVideoRecordingDest, binding.btnSelectVideoRecordingDest.contentDescription)
        Timber.d("S0842: video-recording dest picker is icon-only")
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
            // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
            TooltipCompat.setTooltipText(binding.btnSelectMicRecordingDest, binding.btnSelectMicRecordingDest.contentDescription)
            Timber.d("S0842: mic-recording dest picker is icon-only")
            binding.btnSelectMicRecordingDest.setOnClickListener {
                pickDestination(
                    viewModel.settings.value.micRecordingDestinationResourceId?.toLongOrNull()
                ) { resource ->
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(micRecordingDestinationResourceId = resource?.id?.toString()))
                }
            }
        }

        // ── Screen video recording (S0774) ──
        if (!isScreenRecordingAvailable) {
            binding.rowScreenRecordingEnabled.isVisible = false
            binding.layoutScreenRecordingDestSelector.isVisible = false
        } else {
            binding.rowScreenRecordingEnabled.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
                viewModel.updateSettings(viewModel.settings.value.copy(screenRecordingEnabled = isChecked))
                binding.layoutScreenRecordingDestSelector.isVisible = isChecked
            }
            // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
            TooltipCompat.setTooltipText(binding.btnSelectScreenRecordingDest, binding.btnSelectScreenRecordingDest.contentDescription)
            Timber.d("S0842: screen-recording dest picker is icon-only")
            binding.btnSelectScreenRecordingDest.setOnClickListener {
                pickDestination(
                    viewModel.settings.value.screenRecordingDestinationResourceId?.toLongOrNull()
                ) { resource ->
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(screenRecordingDestinationResourceId = resource?.id?.toString()))
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
        if (binding.rowCameraGeotag.isChecked != settings.cameraGeotagEnabled) {
            binding.rowCameraGeotag.setCheckedSilently(settings.cameraGeotagEnabled)
        }
        binding.layoutCameraToResourceOptions.isVisible = !settings.disableCameraCapture
        refreshLabel(
            settings.cameraPhotosDestinationResourceId,
            R.string.setting_camera_photos_destination_default_camera
        ) { binding.tvCameraPhotosDest.text = it }
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
            R.string.setting_video_recording_destination_default_movies
        ) { binding.tvVideoRecordingDest.text = it }
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
                R.string.setting_mic_recording_destination_default_downloads
            ) { binding.tvMicRecordingDest.text = it }
        }
        // Screen video recording rows (S0774, capability-gated).
        if (isScreenRecordingAvailable) {
            if (binding.rowScreenRecordingEnabled.isChecked != settings.screenRecordingEnabled) {
                binding.rowScreenRecordingEnabled.setCheckedSilently(settings.screenRecordingEnabled)
            }
            binding.layoutScreenRecordingDestSelector.isVisible = settings.screenRecordingEnabled
            refreshLabel(
                settings.screenRecordingDestinationResourceId,
                R.string.setting_screen_recording_destination_default_downloads
            ) { binding.tvScreenRecordingDest.text = it }
        }
    }

    /**
     * S0559: store-safe menu-screenshot test action. The button now lives inside the screen-gestures
     * card (after the gesture-action rows), not its own card, so its own visibility is gated here on
     * the launcher set: shown only when a [MenuScreenshotLauncher] is bound (standard + noLegal),
     * gone otherwise. On standard the enclosing gestures card is itself hidden, so the button never
     * appears there regardless.
     */
    fun setupScreenshotAction(launchers: Set<MenuScreenshotLauncher>, activity: Activity) {
        val launcher = launchers.firstOrNull()
        binding.btnTakeScreenshotNow.isVisible = launcher != null
        if (launcher != null) {
            binding.btnTakeScreenshotNow.setOnClickListener { launcher.launch(activity) }
        }
    }
}
