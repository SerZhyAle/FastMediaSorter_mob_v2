package com.sza.fastmediasorter_v2.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlin.math.roundToInt
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.util.LocaleHelper
import com.sza.fastmediasorter_v2.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter_v2.databinding.FragmentSettingsMediaBinding
import com.sza.fastmediasorter_v2.databinding.FragmentSettingsPlaybackBinding
import com.sza.fastmediasorter_v2.databinding.ItemDestinationBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.ui.dialog.ColorPickerDialog
import kotlinx.coroutines.launch
import kotlin.math.pow

class MediaSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsMediaBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()

    companion object {
        // Image size: 0-1GB, slider 0-100
        private const val IMAGE_MIN_SIZE = 0L
        private const val IMAGE_MAX_SIZE = 1073741824L // 1GB
        
        // Video size: 0-1TB, slider 0-100
        private const val VIDEO_MIN_SIZE = 0L
        private const val VIDEO_MAX_SIZE = 1099511627776L // 1TB
        
        // Audio size: 0-10GB, slider 0-100
        private const val AUDIO_MIN_SIZE = 0L
        private const val AUDIO_MAX_SIZE = 10737418240L // 10GB
        
        // Convert slider position (0-100) to file size using exponential scale
        private fun sliderToSize(value: Float, minSize: Long, maxSize: Long): Long {
            val normalized = value / 100f
            val size = minSize + (maxSize - minSize) * normalized.pow(2)
            return size.toLong()
        }
        
        // Convert file size to slider position (0-100)
        private fun sizeToSlider(size: Long, minSize: Long, maxSize: Long): Float {
            val normalized = (size - minSize).toDouble() / (maxSize - minSize)
            val sliderValue = (normalized.pow(0.5) * 100).coerceIn(0.0, 100.0)
            return sliderValue.roundToInt().toFloat()
        }
        
        // Format size for display
        private fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1073741824 -> "%.1f GB".format(bytes / 1073741824.0)
                bytes >= 1048576 -> "%.1f MB".format(bytes / 1048576.0)
                bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsMediaBinding.inflate(inflater, container, false)
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
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportImages = isChecked))
            updateImageSizeVisibility(isChecked)
        }
        
        binding.sliderImageSize.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val minSize = sliderToSize(values[0], IMAGE_MIN_SIZE, IMAGE_MAX_SIZE)
            val maxSize = sliderToSize(values[1], IMAGE_MIN_SIZE, IMAGE_MAX_SIZE)
            
            binding.tvImageSizeRange.text = "${formatSize(minSize)} - ${formatSize(maxSize)}"
            
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(
                imageSizeMin = minSize,
                imageSizeMax = maxSize
            ))
        }
        
        // Support GIFs
        binding.switchSupportGifs.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportGifs = isChecked))
        }
        
        // Support Videos
        binding.switchSupportVideos.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportVideos = isChecked))
            updateVideoSizeVisibility(isChecked)
        }
        
        binding.sliderVideoSize.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val minSize = sliderToSize(values[0], VIDEO_MIN_SIZE, VIDEO_MAX_SIZE)
            val maxSize = sliderToSize(values[1], VIDEO_MIN_SIZE, VIDEO_MAX_SIZE)
            
            binding.tvVideoSizeRange.text = "${formatSize(minSize)} - ${formatSize(maxSize)}"
            
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(
                videoSizeMin = minSize,
                videoSizeMax = maxSize
            ))
        }
        
        // Support Audio
        binding.switchSupportAudio.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportAudio = isChecked))
            updateAudioSizeVisibility(isChecked)
        }
        
        binding.sliderAudioSize.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val minSize = sliderToSize(values[0], AUDIO_MIN_SIZE, AUDIO_MAX_SIZE)
            val maxSize = sliderToSize(values[1], AUDIO_MIN_SIZE, AUDIO_MAX_SIZE)
            
            binding.tvAudioSizeRange.text = "${formatSize(minSize)} - ${formatSize(maxSize)}"
            
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(
                audioSizeMin = minSize,
                audioSizeMax = maxSize
            ))
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Update switches
                    binding.switchSupportImages.isChecked = settings.supportImages
                    binding.switchSupportGifs.isChecked = settings.supportGifs
                    binding.switchSupportVideos.isChecked = settings.supportVideos
                    binding.switchSupportAudio.isChecked = settings.supportAudio
                    
                    // Update image size slider
                    val imageMinSlider = sizeToSlider(settings.imageSizeMin, IMAGE_MIN_SIZE, IMAGE_MAX_SIZE)
                    val imageMaxSlider = sizeToSlider(settings.imageSizeMax, IMAGE_MIN_SIZE, IMAGE_MAX_SIZE)
                    binding.sliderImageSize.values = listOf(imageMinSlider, imageMaxSlider)
                    binding.tvImageSizeRange.text = "${formatSize(settings.imageSizeMin)} - ${formatSize(settings.imageSizeMax)}"
                    
                    // Update video size slider
                    val videoMinSlider = sizeToSlider(settings.videoSizeMin, VIDEO_MIN_SIZE, VIDEO_MAX_SIZE)
                    val videoMaxSlider = sizeToSlider(settings.videoSizeMax, VIDEO_MIN_SIZE, VIDEO_MAX_SIZE)
                    binding.sliderVideoSize.values = listOf(videoMinSlider, videoMaxSlider)
                    binding.tvVideoSizeRange.text = "${formatSize(settings.videoSizeMin)} - ${formatSize(settings.videoSizeMax)}"
                    
                    // Update audio size slider
                    val audioMinSlider = sizeToSlider(settings.audioSizeMin, AUDIO_MIN_SIZE, AUDIO_MAX_SIZE)
                    val audioMaxSlider = sizeToSlider(settings.audioSizeMax, AUDIO_MIN_SIZE, AUDIO_MAX_SIZE)
                    binding.sliderAudioSize.values = listOf(audioMinSlider, audioMaxSlider)
                    binding.tvAudioSizeRange.text = "${formatSize(settings.audioSizeMin)} - ${formatSize(settings.audioSizeMax)}"
                    
                    // Update visibility
                    updateImageSizeVisibility(settings.supportImages)
                    updateVideoSizeVisibility(settings.supportVideos)
                    updateAudioSizeVisibility(settings.supportAudio)
                }
            }
        }
    }
    
    private fun updateImageSizeVisibility(visible: Boolean) {
        binding.tvImageSizeLabel.isVisible = visible
        binding.tvImageSizeRange.isVisible = visible
        binding.sliderImageSize.isVisible = visible
    }
    
    private fun updateVideoSizeVisibility(visible: Boolean) {
        binding.tvVideoSizeLabel.isVisible = visible
        binding.tvVideoSizeRange.isVisible = visible
        binding.sliderVideoSize.isVisible = visible
    }
    
    private fun updateAudioSizeVisibility(visible: Boolean) {
        binding.tvAudioSizeLabel.isVisible = visible
        binding.tvAudioSizeRange.isVisible = visible
        binding.sliderAudioSize.isVisible = visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PlaybackSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsPlaybackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupViews() {
        // Sort mode spinner
        val sortModes = arrayOf(
            "Name (A-Z)", "Name (Z-A)", 
            "Date (Old first)", "Date (New first)",
            "Size (Small first)", "Size (Large first)",
            "Type (A-Z)", "Type (Z-A)"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortModes)
        binding.spinnerSortMode.setAdapter(adapter)
        binding.spinnerSortMode.setOnItemClickListener { _, _, position, _ ->
            val current = viewModel.settings.value
            val sortMode = SortMode.entries[position]
            viewModel.updateSettings(current.copy(defaultSortMode = sortMode))
        }
        
        // Slideshow interval slider
        binding.sliderSlideshow.addOnChangeListener { slider, value, _ ->
            val seconds = value.toInt()
            binding.tvSlideshowValue.text = formatDuration(seconds)
            
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(slideshowInterval = seconds))
        }
        
        // Switches
        binding.switchPlayToEnd.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(playToEndInSlideshow = isChecked))
        }
        
        binding.switchAllowRename.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowRename = isChecked))
        }
        
        binding.switchAllowDelete.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowDelete = isChecked))
        }
        
        binding.switchConfirmDelete.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmDelete = isChecked))
        }
        
        binding.switchGridMode.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultGridMode = isChecked))
        }
        
        binding.switchFullScreen.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(fullScreenMode = isChecked))
        }
        
        binding.switchDetailedErrors.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }
        
        // Icon size slider
        binding.sliderIconSize.addOnChangeListener { slider, value, _ ->
            val size = value.toInt()
            binding.tvIconSize.text = "${size}px"
            
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultIconSize = size))
        }
    }
    
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Sort mode
                    binding.spinnerSortMode.setText(getSortModeName(settings.defaultSortMode), false)
                    
                    // Slideshow interval
                    binding.sliderSlideshow.value = settings.slideshowInterval.toFloat()
                    binding.tvSlideshowValue.text = formatDuration(settings.slideshowInterval)
                    
                    // Switches
                    binding.switchPlayToEnd.isChecked = settings.playToEndInSlideshow
                    binding.switchAllowRename.isChecked = settings.allowRename
                    binding.switchAllowDelete.isChecked = settings.allowDelete
                    binding.switchConfirmDelete.isChecked = settings.confirmDelete
                    binding.switchGridMode.isChecked = settings.defaultGridMode
                    binding.switchFullScreen.isChecked = settings.fullScreenMode
                    binding.switchDetailedErrors.isChecked = settings.showDetailedErrors
                    
                    // Icon size
                    binding.sliderIconSize.value = settings.defaultIconSize.toFloat()
                    binding.tvIconSize.text = "${settings.defaultIconSize}px"
                }
            }
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> {
                val min = seconds / 60
                val sec = seconds % 60
                if (sec == 0) "$min min" else "$min min $sec sec"
            }
            else -> {
                val hours = seconds / 3600
                val min = (seconds % 3600) / 60
                if (min == 0) "$hours h" else "$hours h $min min"
            }
        }
    }
    
    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.NAME_ASC -> "Name (A-Z)"
            SortMode.NAME_DESC -> "Name (Z-A)"
            SortMode.DATE_ASC -> "Date (Old first)"
            SortMode.DATE_DESC -> "Date (New first)"
            SortMode.SIZE_ASC -> "Size (Small first)"
            SortMode.SIZE_DESC -> "Size (Large first)"
            SortMode.TYPE_ASC -> "Type (A-Z)"
            SortMode.TYPE_DESC -> "Type (Z-A)"
        }
    }
}

class DestinationsSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsDestinationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private lateinit var adapter: DestinationsAdapter
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsDestinationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupViews() {
        // Copying switches
        binding.switchEnableCopying.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableCopying = isChecked))
            updateCopyOptionsVisibility(isChecked)
        }
        
        binding.switchGoToNextAfterCopy.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(goToNextAfterCopy = isChecked))
        }
        
        binding.switchOverwriteOnCopy.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(overwriteOnCopy = isChecked))
        }
        
        // Moving switches
        binding.switchEnableMoving.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableMoving = isChecked))
            updateMoveOptionsVisibility(isChecked)
        }
        
        binding.switchOverwriteOnMove.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(overwriteOnMove = isChecked))
        }
        
        // RecyclerView setup
        adapter = DestinationsAdapter(
            onMoveUp = { position -> moveDestination(position, -1) },
            onMoveDown = { position -> moveDestination(position, 1) },
            onDelete = { position -> deleteDestination(position) },
            onColorClick = { resource -> showColorPicker(resource) }
        )
        binding.rvDestinations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDestinations.adapter = adapter
        
        // Add button
        binding.btnAddDestination.setOnClickListener {
            showAddDestinationDialog()
        }
    }
    
    private fun showAddDestinationDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val availableResources = viewModel.getWritableNonDestinationResources()
            
            if (availableResources.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No writable resources available for destinations",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            
            val items = availableResources.map { "${it.name} (${it.path})" }.toTypedArray()
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Destination")
                .setItems(items) { dialog, which ->
                    val selectedResource = availableResources[which]
                    viewModel.addDestination(selectedResource)
                    Toast.makeText(
                        requireContext(),
                        "Destination added: ${selectedResource.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showColorPicker(resource: MediaResource) {
        ColorPickerDialog.newInstance(
            initialColor = resource.destinationColor,
            onColorSelected = { color ->
                viewModel.updateDestinationColor(resource, color)
            }
        ).show(parentFragmentManager, "ColorPickerDialog")
    }
    
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        // Update switches
                        binding.switchEnableCopying.isChecked = settings.enableCopying
                        binding.switchGoToNextAfterCopy.isChecked = settings.goToNextAfterCopy
                        binding.switchOverwriteOnCopy.isChecked = settings.overwriteOnCopy
                        binding.switchEnableMoving.isChecked = settings.enableMoving
                        binding.switchOverwriteOnMove.isChecked = settings.overwriteOnMove
                        
                        updateCopyOptionsVisibility(settings.enableCopying)
                        updateMoveOptionsVisibility(settings.enableMoving)
                    }
                }
                
                launch {
                    viewModel.destinations.collect { destinations ->
                        adapter.submitList(destinations)
                    }
                }
            }
        }
    }
    
    private fun updateCopyOptionsVisibility(enabled: Boolean) {
        binding.layoutCopyOptions.isVisible = enabled
    }
    
    private fun updateMoveOptionsVisibility(enabled: Boolean) {
        binding.layoutMoveOptions.isVisible = enabled
    }
    
    private fun moveDestination(position: Int, direction: Int) {
        val destinations = viewModel.destinations.value
        if (position < 0 || position >= destinations.size) return
        
        val resource = destinations[position]
        viewModel.moveDestination(resource, direction)
    }
    
    private fun deleteDestination(position: Int) {
        val destinations = viewModel.destinations.value
        if (position < 0 || position >= destinations.size) return
        
        val resource = destinations[position]
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove Destination")
            .setMessage("Remove '${resource.name}' from destinations?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeDestination(resource)
                Toast.makeText(
                    requireContext(),
                    "Destination removed: ${resource.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    inner class DestinationsAdapter(
        private val onMoveUp: (Int) -> Unit,
        private val onMoveDown: (Int) -> Unit,
        private val onDelete: (Int) -> Unit,
        private val onColorClick: (MediaResource) -> Unit
    ) : RecyclerView.Adapter<DestinationsAdapter.ViewHolder>() {
        
        private var destinations: List<MediaResource> = emptyList()
        
        fun submitList(list: List<MediaResource>) {
            destinations = list
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDestinationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(destinations[position], position)
        }
        
        override fun getItemCount() = destinations.size
        
        inner class ViewHolder(private val binding: ItemDestinationBinding) : 
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(resource: MediaResource, position: Int) {
                val order = resource.destinationOrder ?: -1
                binding.tvDestinationNumber.text = order.toString()
                binding.tvDestinationName.text = resource.name
                binding.tvDestinationPath.text = resource.path
                
                // Set color indicator from database
                binding.viewColorIndicator.setBackgroundColor(resource.destinationColor)
                binding.viewColorIndicator.setOnClickListener {
                    onColorClick(resource)
                }
                
                // Move up button
                binding.btnMoveUp.isEnabled = position > 0
                binding.btnMoveUp.setOnClickListener { onMoveUp(position) }
                
                // Move down button
                binding.btnMoveDown.isEnabled = position < itemCount - 1
                binding.btnMoveDown.setOnClickListener { onMoveDown(position) }
                
                // Delete button
                binding.btnDelete.setOnClickListener { onDelete(position) }
            }
        }
    }
}

class GeneralSettingsFragment : Fragment() {

    private var _binding: com.sza.fastmediasorter_v2.databinding.FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.sza.fastmediasorter_v2.databinding.FragmentSettingsGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        // Language Spinner
        val languages = resources.getStringArray(com.sza.fastmediasorter_v2.R.array.languages)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        // Set initial language selection
        val currentLanguage = LocaleHelper.getLanguage(requireContext())
        binding.spinnerLanguage.setSelection(LocaleHelper.getLanguageIndex(currentLanguage), false)
        
        binding.spinnerLanguage.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLanguageCode = when (position) {
                    0 -> "en"
                    1 -> "ru"
                    2 -> "uk"
                    else -> "en"
                }
                
                // Check if language actually changed
                val oldLanguageCode = LocaleHelper.getLanguage(requireContext())
                if (newLanguageCode != oldLanguageCode) {
                    // Update settings
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(language = newLanguageCode))
                    
                    // Show restart dialog
                    showRestartDialog(newLanguageCode)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        
        // Prevent Sleep
        binding.switchPreventSleep.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(preventSleep = isChecked))
        }
        
        // Small Controls
        binding.switchSmallControls.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showSmallControls = isChecked))
        }
        
        // Default User
        binding.etDefaultUser.setText(viewModel.settings.value.defaultUser)
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newUser = binding.etDefaultUser.text.toString()
                if (current.defaultUser != newUser) {
                    viewModel.updateSettings(current.copy(defaultUser = newUser))
                }
            }
        }
        
        // Default Password
        binding.etDefaultPassword.setText(viewModel.settings.value.defaultPassword)
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newPassword = binding.etDefaultPassword.text.toString()
                if (current.defaultPassword != newPassword) {
                    viewModel.updateSettings(current.copy(defaultPassword = newPassword))
                }
            }
        }
        
        // Permissions Buttons
        binding.btnLocalFilesPermission.setOnClickListener {
            Toast.makeText(requireContext(), "Permission request functionality - TODO", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnNetworkPermission.setOnClickListener {
            Toast.makeText(requireContext(), "Permission request functionality - TODO", Toast.LENGTH_SHORT).show()
        }
        
        // Log Buttons
        binding.btnShowLog.setOnClickListener {
            showLogDialog(fullLog = true)
        }
        
        binding.btnShowSessionLog.setOnClickListener {
            showLogDialog(fullLog = false)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Update language spinner
                    val languagePosition = when (settings.language) {
                        "en" -> 0
                        "ru" -> 1
                        "uk" -> 2
                        else -> 0
                    }
                    if (binding.spinnerLanguage.selectedItemPosition != languagePosition) {
                        binding.spinnerLanguage.setSelection(languagePosition)
                    }
                    
                    // Update switches
                    if (binding.switchPreventSleep.isChecked != settings.preventSleep) {
                        binding.switchPreventSleep.isChecked = settings.preventSleep
                    }
                    if (binding.switchSmallControls.isChecked != settings.showSmallControls) {
                        binding.switchSmallControls.isChecked = settings.showSmallControls
                    }
                }
            }
        }
    }

    private fun showLogDialog(fullLog: Boolean) {
        val logText = if (fullLog) {
            getFullLog()
        } else {
            getSessionLog()
        }
        
        // Inflate custom view with small text
        val dialogView = layoutInflater.inflate(
            com.sza.fastmediasorter_v2.R.layout.dialog_log_view,
            null
        )
        val textView = dialogView.findViewById<TextView>(com.sza.fastmediasorter_v2.R.id.tvLogText)
        textView.text = logText
        
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle(if (fullLog) "Application Log" else "Current Session Log")
            .setView(dialogView)
            .setPositiveButton("Copy to Clipboard") { _, _ ->
                copyToClipboard(logText)
            }
            .setNegativeButton("Close", null)
            .create()
        
        dialog.show()
    }

    private fun getFullLog(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            
            val log = StringBuilder()
            var lineCount = 0
            val maxLines = 512
            
            // Read last 512 lines
            val lines = bufferedReader.readLines()
            val startIndex = maxOf(0, lines.size - maxLines)
            
            for (i in startIndex until lines.size) {
                log.append(lines[i]).append("\n")
                lineCount++
            }
            
            bufferedReader.close()
            
            if (log.isEmpty()) {
                "No log entries found"
            } else {
                "Last $lineCount lines of log:\n\n$log"
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    private fun getSessionLog(): String {
        return try {
            val packageName = requireContext().packageName
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            
            val log = StringBuilder()
            var lineCount = 0
            
            bufferedReader.forEachLine { line ->
                // Filter only lines from current app
                if (line.contains(packageName, ignoreCase = true) || 
                    line.contains("FastMediaSorter", ignoreCase = true)) {
                    log.append(line).append("\n")
                    lineCount++
                }
            }
            
            bufferedReader.close()
            
            if (log.isEmpty()) {
                "No log entries found for current session"
            } else {
                "Current session log ($lineCount lines):\n\n$log"
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showRestartDialog(languageCode: String) {
        val languageName = LocaleHelper.getLanguageName(languageCode)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(getString(R.string.restart_app_message, languageName))
            .setPositiveButton(R.string.restart) { _, _ ->
                // Save language and restart app
                LocaleHelper.changeLanguage(requireActivity(), languageCode)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                // Revert spinner to previous language
                val oldLanguageCode = LocaleHelper.getLanguage(requireContext())
                binding.spinnerLanguage.setSelection(LocaleHelper.getLanguageIndex(oldLanguageCode), false)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
