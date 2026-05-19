package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsImagesBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("SetTextI18n")
class ImagesSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsImagesBinding? = null
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
        _binding = FragmentSettingsImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // Support Images - help payload folded into the row
        bindSwitch(binding.rowSupportImages) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportImages = isChecked))
        }

        // Support GIFs
        bindSwitch(binding.rowSupportGifs) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportGifs = isChecked))
        }

        // Load Full Size Images - help payload folded into the row
        bindSwitch(binding.rowLoadFullSizeImages) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(loadFullSizeImages = isChecked))
        }

        // Crop Images to Fullscreen - help payload folded into the row
        bindSwitch(binding.rowCropImagesToFullscreen) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(cropImagesToFullscreen = isChecked))
        }

        // Dynamic Background Extension - help payload folded into the row
        bindSwitch(binding.rowDynamicBackground) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(dynamicBackgroundExtension = isChecked))
        }

        // Image Size Min
        binding.etImageSizeMin.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val minKb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(imageSizeMin = minKb * KB_TO_BYTES))
                }
            }
        })

        // Image Size Max
        binding.etImageSizeMax.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingFromSettings && !s.isNullOrBlank()) {
                    val maxKb = s.toString().toLongOrNull() ?: 0L
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(imageSizeMax = maxKb * KB_TO_BYTES))
                }
            }
        })

        // Slideshow background music toggle - help payload folded into the row
        bindSwitch(binding.rowSlideshowBackgroundMusic) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableSlideshowBackgroundMusic = isChecked))
            binding.layoutMusicSourceSelector.isVisible = isChecked
        }

        // Select music source button
        binding.btnSelectMusicSource.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.ResourcePickerDialog(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                getResourcesUseCase = viewModel.getResourcesUseCase,
                currentSelection = viewModel.settings.value.slideshowMusicResourceId,
                title = getString(com.sza.fastmediasorter.R.string.select_music_source),
                allowClear = true,
                onResourceSelected = { resource ->
                    val current = viewModel.settings.value
                    val updated = current.copy(slideshowMusicResourceId = resource?.id)
                    viewModel.updateSettings(updated)
                }
            ).show()
        }

        setupDefaultPlayerButton()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            DefaultPlayerHelper.applyButtonState(
                it.btnSetDefaultImageViewer, requireContext(), R.string.settings_set_default_image_viewer
            )
        }
    }

    private fun setupDefaultPlayerButton() {
        DefaultPlayerHelper.applyButtonState(
            binding.btnSetDefaultImageViewer, requireContext(), R.string.settings_set_default_image_viewer
        )
        binding.btnSetDefaultImageViewer.setOnClickListener {
            DefaultPlayerHelper.showSetDefaultDialogForType(this, "image/*")
        }
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                setSwitchChecked(binding.rowSupportImages, settings.supportImages)
                setSwitchChecked(binding.rowSupportGifs, settings.supportGifs)
                setSwitchChecked(binding.rowLoadFullSizeImages, settings.loadFullSizeImages)
                setSwitchChecked(binding.rowCropImagesToFullscreen, settings.cropImagesToFullscreen)
                setSwitchChecked(binding.rowDynamicBackground, settings.dynamicBackgroundExtension)

                val minKb = settings.imageSizeMin / KB_TO_BYTES
                val maxKb = settings.imageSizeMax / KB_TO_BYTES

                if (binding.etImageSizeMin.text.toString() != minKb.toString()) {
                    binding.etImageSizeMin.setText(getString(com.sza.fastmediasorter.R.string.string_format, minKb.toString()))
                }
                if (binding.etImageSizeMax.text.toString() != maxKb.toString()) {
                    binding.etImageSizeMax.setText(getString(com.sza.fastmediasorter.R.string.string_format, maxKb.toString()))
                }

                // Slideshow background music
                setSwitchChecked(binding.rowSlideshowBackgroundMusic, settings.enableSlideshowBackgroundMusic)
                binding.layoutMusicSourceSelector.isVisible = settings.enableSlideshowBackgroundMusic

                // Update selected music source text
                if (settings.slideshowMusicResourceId != null) {
                    // Load resource name from repository
                    viewLifecycleOwner.lifecycleScope.launch {
                        val resource = viewModel.resourceRepository.getResourceById(settings.slideshowMusicResourceId)
                        binding.tvSelectedMusicSource.text = resource?.name
                            ?: getString(com.sza.fastmediasorter.R.string.resource_not_found)
                    }
                } else {
                    binding.tvSelectedMusicSource.setText(com.sza.fastmediasorter.R.string.no_music_source_selected)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
