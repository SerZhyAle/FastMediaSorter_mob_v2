package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.databinding.FragmentSettingsImagesBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

class ImagesSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsImagesBinding? = null
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
        _binding = FragmentSettingsImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // Support Images
        binding.switchSupportImages.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(supportImages = isChecked))
            }
        }

        // Support GIFs
        binding.switchSupportGifs.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(supportGifs = isChecked))
            }
        }

        // Load Full Size Images
        binding.switchLoadFullSizeImages.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(loadFullSizeImages = isChecked))
            }
        }

        // Help buttons
        binding.iconHelpSupportImages.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.support_images_description,
                com.sza.fastmediasorter.R.string.supported_image_formats
            )
        }
        
        binding.iconHelpFullSizeImages.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.load_full_size_images,
                com.sza.fastmediasorter.R.string.load_full_size_images_hint
            )
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

        // Slideshow background music toggle
        binding.switchSlideshowBackgroundMusic.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(enableSlideshowBackgroundMusic = isChecked))
                binding.layoutMusicSourceSelector.isVisible = isChecked
            }
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

        // Help icons
        binding.iconHelpSlideshowMusic.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_slideshow_music_title,
                com.sza.fastmediasorter.R.string.tooltip_slideshow_music_message
            )
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    isUpdatingFromSettings = true
                    
                    binding.switchSupportImages.isChecked = settings.supportImages
                    binding.switchSupportGifs.isChecked = settings.supportGifs
                    binding.switchLoadFullSizeImages.isChecked = settings.loadFullSizeImages

                    binding.switchLoadFullSizeImages.isChecked = settings.loadFullSizeImages

                    val minKb = settings.imageSizeMin / KB_TO_BYTES
                    val maxKb = settings.imageSizeMax / KB_TO_BYTES
                    
                    if (binding.etImageSizeMin.text.toString() != minKb.toString()) {
                        binding.etImageSizeMin.setText(minKb.toString())
                    }
                    if (binding.etImageSizeMax.text.toString() != maxKb.toString()) {
                        binding.etImageSizeMax.setText(maxKb.toString())
                    }

                    // Slideshow background music
                    binding.switchSlideshowBackgroundMusic.isChecked = settings.enableSlideshowBackgroundMusic
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
