package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.databinding.FragmentSettingsVideoBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

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
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    isUpdatingFromSettings = true
                    
                    val isAllFilesEnabled = settings.allFiles
                    
                    // When All Files is enabled, force switch ON and disable it
                    binding.switchSupportVideos.isChecked = isAllFilesEnabled || settings.supportVideos
                    binding.switchSupportVideos.isEnabled = !isAllFilesEnabled
                    
                    binding.switchShowVideoThumbnails.isChecked = settings.showVideoThumbnails
                    
                    val minKb = settings.videoSizeMin / KB_TO_BYTES
                    val maxKb = settings.videoSizeMax / KB_TO_BYTES
                    
                    if (binding.etVideoSizeMin.text.toString() != minKb.toString()) {
                        binding.etVideoSizeMin.setText(minKb.toString())
                    }
                    if (binding.etVideoSizeMax.text.toString() != maxKb.toString()) {
                        binding.etVideoSizeMax.setText(maxKb.toString())
                    }
                    
                    isUpdatingFromSettings = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
