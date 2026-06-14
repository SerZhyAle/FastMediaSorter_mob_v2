package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsVideoBinding
import com.sza.fastmediasorter.ui.settings.exitAllFilesForManualSupportToggle
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("SetTextI18n")
class VideoSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsVideoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

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
        // Support Videos — help payload now folded into the row's helper icon (str_helpTitle/str_helpMessage)
        bindSwitch(binding.rowSupportVideos) { isChecked ->
            val current = viewModel.settings.value
            val updated = current
                .exitAllFilesForManualSupportToggle(isChecked)
                .copy(supportVideos = isChecked)
            viewModel.updateSettings(updated)
        }

        // Show video thumbnails — help payload folded into the row
        bindSwitch(binding.rowShowVideoThumbnails) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showVideoThumbnails = isChecked))
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

        // Free-standing help icon next to "Video size limit" label — not part of a switch row,
        // kept as standalone ImageButton with TooltipDialog wiring.
        binding.iconHelpVideoSizeLimits.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_video_size_limits_title,
                com.sza.fastmediasorter.R.string.tooltip_video_size_limits_message
            )
        }

        setupDefaultPlayerButton()
        setupSnapshotResourcePicker()
        setupPlayerExtras()
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
            val current = viewModel.settings.value
            if (!current.isPrimaryMediaPlayer) {
                viewModel.updateSettings(current.copy(isPrimaryMediaPlayer = true))
            }
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

        // Free-standing help icon next to "Video snapshot" section header — not part of a switch row,
        // kept as standalone ImageButton with TooltipDialog wiring.
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
     * S0251: the former VR-only block lost its forced-format controls (dead since S0241).
     * `rowPlayerShowFps` (S0021) lives on as a plain Video-section toggle, unconditional on every build.
     * `rowAllowSeparateWindow` (S0028) was relocated to General → Interface (bottom of section).
     */
    private fun setupPlayerExtras() {
        // S0021: Show FPS over flat (non-immersive) player
        bindSwitch(binding.rowPlayerShowFps) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(playerShowFps = isChecked))
        }
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                val isAllFilesEnabled = settings.allFiles

                // When All Files is enabled, force switch ON and disable it
                setSwitchChecked(binding.rowSupportVideos, isAllFilesEnabled || settings.supportVideos)

                setSwitchChecked(binding.rowShowVideoThumbnails, settings.showVideoThumbnails)

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

                // S0021: FPS overlay over flat player
                setSwitchChecked(binding.rowPlayerShowFps, settings.playerShowFps)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
