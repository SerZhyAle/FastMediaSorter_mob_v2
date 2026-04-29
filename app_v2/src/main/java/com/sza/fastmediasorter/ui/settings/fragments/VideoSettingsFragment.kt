package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.databinding.FragmentSettingsVideoBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("SetTextI18n")
class VideoSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsVideoBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()
    private var isUpdatingFromSettings = false

    companion object {
        private const val KB_TO_BYTES = 1024L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // Support Videos
        binding.switchSupportVideos.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(supportVideos = isChecked))
            }
        }

        // Show video thumbnails
        binding.switchShowVideoThumbnails.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(showVideoThumbnails = isChecked))
            }
        }

        // Video size limits
        binding.etVideoSizeMin.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val minKb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(videoSizeMin = minKb * KB_TO_BYTES))
                }
            }
        })

        binding.etVideoSizeMax.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val maxKb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(videoSizeMax = maxKb * KB_TO_BYTES))
                }
            }
        })

        // Help button click handlers
        binding.iconHelpSupportVideos.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.support_video_description,
                com.sza.fastmediasorter.R.string.supported_video_formats
            )
        }

        binding.iconHelpVideoThumbnails.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_video_thumbnails_title,
                com.sza.fastmediasorter.R.string.tooltip_video_thumbnails_message
            )
        }

        binding.iconHelpVideoSizeLimits.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_video_size_limits_title,
                com.sza.fastmediasorter.R.string.tooltip_video_size_limits_message
            )
        }

        setupDefaultPlayerButton()
        setupSnapshotResourcePicker()

        // VR settings block — only visible in VR flavor (spec §5.7)
        if (BuildConfig.SUPPORT_VR_PLAYER) {
            setupVrSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            DefaultPlayerHelper.applyButtonState(
                it.btnSetDefaultVideoPlayer, requireContext(), R.string.settings_set_default_video_player
            )
        }
    }

    private fun setupDefaultPlayerButton() {
        DefaultPlayerHelper.applyButtonState(
            binding.btnSetDefaultVideoPlayer, requireContext(), R.string.settings_set_default_video_player
        )
        binding.btnSetDefaultVideoPlayer.setOnClickListener {
            DefaultPlayerHelper.showSetDefaultDialogForType(this, "video/*")
        }
    }

    private fun setupSnapshotResourcePicker() {
        // Open destination-only resource picker
        binding.btnSelectSnapshotResource.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.DestinationPickerDialog(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                getDestinationsUseCase = viewModel.getDestinationsUseCase,
                currentSelection = viewModel.settings.value.videoSnapshotResourceId,
                title = getString(R.string.select_snapshot_destination),
                allowClear = true,
                onResourceSelected = { resource ->
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(videoSnapshotResourceId = resource?.id))
                }
            ).show()
        }

        binding.btnClearSnapshotResource.setOnClickListener {
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(videoSnapshotResourceId = null))
        }

        binding.iconHelpVideoSnapshot.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_video_snapshot_title,
                R.string.tooltip_video_snapshot_message
            )
        }

        // Frame format selector: PNG or JPG (85% quality)
        // isUpdatingFromSettings guard prevents feedback loop when check() is called from observeData
        binding.rgSnapshotFormat.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingFromSettings) {
                val format = if (checkedId == R.id.rbSnapshotJpg) "JPG" else "PNG"
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(videoSnapshotFormat = format))
            }
        }
    }

    /**
     * VR settings block — only shown in VR flavor (spec §5.7).
     * Controls auto-detection, forced format, rendering mode, and format caching.
     */
    private fun setupVrSettings() {
        binding.layoutVrSettings.visibility = View.VISIBLE

        binding.iconHelpVrSettings.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.settings_vr_help_title,
                R.string.settings_vr_help_message
            )
        }

        // Auto-detect format switch
        binding.switchVrAutoDetect.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(vrAutoDetectFormat = isChecked))
            }
        }

        // Forced flat format spinner
        val forcedPlatFormatValues = resources.getStringArray(R.array.vr_forced_format_values)
        binding.spinnerVrForcedFormat.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings && position in forcedPlatFormatValues.indices) {
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(vrForcedPlatFormat = forcedPlatFormatValues[position]))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Forced spherical format spinner
        val forcedSphericalFormatValues = resources.getStringArray(R.array.vr_forced_spherical_format_values)
        binding.spinnerVrForcedSphericalFormat.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings && position in forcedSphericalFormatValues.indices) {
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(vrForcedSphericalFormat = forcedSphericalFormatValues[position]))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Rendering mode spinner
        val renderingModeValues = resources.getStringArray(R.array.vr_rendering_mode_values)
        binding.spinnerVrRenderingMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings && position in renderingModeValues.indices) {
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(vrRenderingMode = renderingModeValues[position]))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Remember file format switch
        binding.switchVrRememberFormat.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(vrRememberFileFormat = isChecked))
            }
        }

        // Auto-immersive on stereo content switch
        binding.switchVrAutoImmersive.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(vrAutoImmersive = isChecked))
            }
        }

        // Show VR FPS switch
        binding.switchVrShowFps.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(vrShowFps = isChecked))
            }
        }

        // S0021: Show FPS over flat (non-immersive) player
        binding.switchPlayerShowFps.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(playerShowFps = isChecked))
            }
        }
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
                    isUpdatingFromSettings = true
                    
                    val isAllFilesEnabled = settings.allFiles
                    
                    // When All Files is enabled, force switch ON and disable it
                    binding.switchSupportVideos.isChecked = isAllFilesEnabled || settings.supportVideos
                    binding.switchSupportVideos.isEnabled = !isAllFilesEnabled
                    
                    binding.switchShowVideoThumbnails.isChecked = settings.showVideoThumbnails

                    val minKb = settings.videoSizeMin / KB_TO_BYTES
                    val maxKb = settings.videoSizeMax / KB_TO_BYTES
                    
                    if (binding.etVideoSizeMin.text.toString() != minKb.toString()) {
                        binding.etVideoSizeMin.setText(getString(com.sza.fastmediasorter.R.string.string_format, minKb.toString()))
                    }
                    if (binding.etVideoSizeMax.text.toString() != maxKb.toString()) {
                        binding.etVideoSizeMax.setText(getString(com.sza.fastmediasorter.R.string.string_format, maxKb.toString()))
                    }

                    // Video snapshot resource: show name or "not selected" hint
                    if (settings.videoSnapshotResourceId != null) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val resource = viewModel.resourceRepository.getResourceById(settings.videoSnapshotResourceId)
                            binding.tvSelectedSnapshotResource.text = resource?.name
                                ?: getString(com.sza.fastmediasorter.R.string.resource_not_found)
                        }
                        binding.btnClearSnapshotResource.visibility = android.view.View.VISIBLE
                    } else {
                        binding.tvSelectedSnapshotResource.setText(com.sza.fastmediasorter.R.string.video_snapshot_resource_not_set)
                        binding.btnClearSnapshotResource.visibility = android.view.View.GONE
                    }

                    // Snapshot format radio button: reflects videoSnapshotFormat setting
                    binding.rgSnapshotFormat.check(
                        if (settings.videoSnapshotFormat == "JPG") R.id.rbSnapshotJpg else R.id.rbSnapshotPng
                    )

                    // VR settings — update spinners and switches (only if VR block is visible)
                    if (BuildConfig.SUPPORT_VR_PLAYER) {
                        binding.switchVrAutoDetect.isChecked = settings.vrAutoDetectFormat

                        val forcedFormatValues = resources.getStringArray(R.array.vr_forced_format_values)
                        val forcedIdx = forcedFormatValues.indexOf(settings.vrForcedPlatFormat).coerceAtLeast(0)
                        binding.spinnerVrForcedFormat.setSelection(forcedIdx)

                        val forcedSphericalFormatValues = resources.getStringArray(R.array.vr_forced_spherical_format_values)
                        val forcedSphericalIdx = forcedSphericalFormatValues.indexOf(settings.vrForcedSphericalFormat).coerceAtLeast(0)
                        binding.spinnerVrForcedSphericalFormat.setSelection(forcedSphericalIdx)

                        val renderingModeValues = resources.getStringArray(R.array.vr_rendering_mode_values)
                        val modeIdx = renderingModeValues.indexOf(settings.vrRenderingMode).coerceAtLeast(0)
                        binding.spinnerVrRenderingMode.setSelection(modeIdx)

                        binding.switchVrRememberFormat.isChecked = settings.vrRememberFileFormat
                        binding.switchVrAutoImmersive.isChecked = settings.vrAutoImmersive
                        binding.switchVrShowFps.isChecked = settings.vrShowFps
                        val vrGloballyEnabled = !settings.disable3dVr
                        binding.switchVrShowFps.isEnabled = vrGloballyEnabled
                        binding.tvVrShowFpsDisabledHint.visibility = if (vrGloballyEnabled) View.GONE else View.VISIBLE
                        binding.switchPlayerShowFps.isChecked = settings.playerShowFps
                    }

                    isUpdatingFromSettings = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
