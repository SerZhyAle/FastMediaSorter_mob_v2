package com.sza.fastmediasorter_v2.ui.settings

import android.content.Intent
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
        // Conversion constants
        private const val KB_TO_BYTES = 1024L
        private const val MB_TO_BYTES = 1024L * 1024L
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
            updateImageSizeVisibility(isChecked, current.supportGifs)
        }
        
        // Load full size images
        binding.switchLoadFullSizeImages.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(loadFullSizeImages = isChecked))
        }
        
        // Image size text fields (in KB)
        binding.etImageSizeMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etImageSizeMin.text.toString()
                val kb = text.toLongOrNull() ?: 1
                val bytes = kb * KB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.imageSizeMin) {
                    viewModel.updateSettings(current.copy(imageSizeMin = bytes))
                }
            }
        }
        
        binding.etImageSizeMax.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etImageSizeMax.text.toString()
                val kb = text.toLongOrNull() ?: 10240 // 10MB default
                val bytes = kb * KB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.imageSizeMax) {
                    viewModel.updateSettings(current.copy(imageSizeMax = bytes))
                }
            }
        }
        
        // Support GIFs
        binding.switchSupportGifs.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportGifs = isChecked))
            updateImageSizeVisibility(current.supportImages, isChecked)
        }
        
        // Support Videos
        binding.switchSupportVideos.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportVideos = isChecked))
            updateVideoSizeVisibility(isChecked)
        }
        
        // Video size text fields (in MB)
        binding.etVideoSizeMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etVideoSizeMin.text.toString()
                val mb = text.toLongOrNull() ?: 1
                val bytes = mb * MB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.videoSizeMin) {
                    viewModel.updateSettings(current.copy(videoSizeMin = bytes))
                }
            }
        }
        
        binding.etVideoSizeMax.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etVideoSizeMax.text.toString()
                val mb = text.toLongOrNull() ?: 1024 // 1GB default
                val bytes = mb * MB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.videoSizeMax) {
                    viewModel.updateSettings(current.copy(videoSizeMax = bytes))
                }
            }
        }
        
        // Support Audio
        binding.switchSupportAudio.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportAudio = isChecked))
            updateAudioSizeVisibility(isChecked)
        }
        
        // Audio size text fields (in MB)
        binding.etAudioSizeMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etAudioSizeMin.text.toString()
                val mb = text.toLongOrNull() ?: 1
                val bytes = mb * MB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.audioSizeMin) {
                    viewModel.updateSettings(current.copy(audioSizeMin = bytes))
                }
            }
        }
        
        binding.etAudioSizeMax.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etAudioSizeMax.text.toString()
                val mb = text.toLongOrNull() ?: 100 // 100MB default
                val bytes = mb * MB_TO_BYTES
                val current = viewModel.settings.value
                if (bytes != current.audioSizeMax) {
                    viewModel.updateSettings(current.copy(audioSizeMax = bytes))
                }
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Update switches (only if changed)
                    if (binding.switchSupportImages.isChecked != settings.supportImages) {
                        binding.switchSupportImages.isChecked = settings.supportImages
                    }
                    if (binding.switchLoadFullSizeImages.isChecked != settings.loadFullSizeImages) {
                        binding.switchLoadFullSizeImages.isChecked = settings.loadFullSizeImages
                    }
                    if (binding.switchSupportGifs.isChecked != settings.supportGifs) {
                        binding.switchSupportGifs.isChecked = settings.supportGifs
                    }
                    if (binding.switchSupportVideos.isChecked != settings.supportVideos) {
                        binding.switchSupportVideos.isChecked = settings.supportVideos
                    }
                    if (binding.switchSupportAudio.isChecked != settings.supportAudio) {
                        binding.switchSupportAudio.isChecked = settings.supportAudio
                    }
                    
                    // Update image size text fields (in KB)
                    val imageMinKB = (settings.imageSizeMin / KB_TO_BYTES).toString()
                    val imageMaxKB = (settings.imageSizeMax / KB_TO_BYTES).toString()
                    if (binding.etImageSizeMin.text.toString() != imageMinKB) {
                        binding.etImageSizeMin.setText(imageMinKB)
                    }
                    if (binding.etImageSizeMax.text.toString() != imageMaxKB) {
                        binding.etImageSizeMax.setText(imageMaxKB)
                    }
                    
                    // Update video size text fields (in MB)
                    val videoMinMB = (settings.videoSizeMin / MB_TO_BYTES).toString()
                    val videoMaxMB = (settings.videoSizeMax / MB_TO_BYTES).toString()
                    if (binding.etVideoSizeMin.text.toString() != videoMinMB) {
                        binding.etVideoSizeMin.setText(videoMinMB)
                    }
                    if (binding.etVideoSizeMax.text.toString() != videoMaxMB) {
                        binding.etVideoSizeMax.setText(videoMaxMB)
                    }
                    
                    // Update audio size text fields (in MB)
                    val audioMinMB = (settings.audioSizeMin / MB_TO_BYTES).toString()
                    val audioMaxMB = (settings.audioSizeMax / MB_TO_BYTES).toString()
                    if (binding.etAudioSizeMin.text.toString() != audioMinMB) {
                        binding.etAudioSizeMin.setText(audioMinMB)
                    }
                    if (binding.etAudioSizeMax.text.toString() != audioMaxMB) {
                        binding.etAudioSizeMax.setText(audioMaxMB)
                    }
                    
                    // Update visibility
                    updateImageSizeVisibility(settings.supportImages, settings.supportGifs)
                    updateVideoSizeVisibility(settings.supportVideos)
                    updateAudioSizeVisibility(settings.supportAudio)
                }
            }
        }
    }
    
    private fun updateImageSizeVisibility(supportImages: Boolean, supportGifs: Boolean) {
        // Show size limit if either static images or GIFs are enabled
        val visible = supportImages || supportGifs
        binding.tvImageSizeLabel.isVisible = visible
        binding.layoutImageSizeInputs.isVisible = visible
    }
    
    private fun updateVideoSizeVisibility(visible: Boolean) {
        binding.tvVideoSizeLabel.isVisible = visible
        binding.layoutVideoSizeInputs.isVisible = visible
    }
    
    private fun updateAudioSizeVisibility(visible: Boolean) {
        binding.tvAudioSizeLabel.isVisible = visible
        binding.layoutAudioSizeInputs.isVisible = visible
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
        // Sort mode dropdown
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
        
        // Slideshow interval text field
        binding.etSlideshowInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etSlideshowInterval.text.toString()
                val seconds = text.toIntOrNull() ?: 5
                val clampedSeconds = seconds.coerceIn(1, 3600)
                if (seconds != clampedSeconds) {
                    binding.etSlideshowInterval.setText(clampedSeconds.toString())
                }
                val current = viewModel.settings.value
                if (clampedSeconds != current.slideshowInterval) {
                    viewModel.updateSettings(current.copy(slideshowInterval = clampedSeconds))
                }
            }
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
        
        binding.switchShowCommandPanel.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultShowCommandPanel = isChecked))
        }
        
        binding.switchDetailedErrors.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }
        
        binding.switchShowPlayerHint.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showPlayerHintOnFirstRun = isChecked))
        }
        
        binding.switchShowVideoThumbnails.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showVideoThumbnails = isChecked))
        }
        
        binding.btnShowHintNow.setOnClickListener {
            // Reset first-run flag to trigger hint on next PlayerActivity launch
            viewModel.resetPlayerFirstRun()
            Toast.makeText(
                requireContext(),
                "Touch zones hint will be shown next time you open media player",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Icon size text field
        binding.etIconSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etIconSize.text.toString()
                val size = text.toIntOrNull() ?: 96
                val clampedSize = size.coerceIn(32, 256)
                if (size != clampedSize) {
                    binding.etIconSize.setText(clampedSize.toString())
                }
                val current = viewModel.settings.value
                if (clampedSize != current.defaultIconSize) {
                    viewModel.updateSettings(current.copy(defaultIconSize = clampedSize))
                }
            }
        }
    }
    
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    // Sort mode
                    binding.spinnerSortMode.setText(getSortModeName(settings.defaultSortMode), false)
                    
                    // Slideshow interval
                    val currentSlideshow = binding.etSlideshowInterval.text.toString().toIntOrNull()
                    if (currentSlideshow != settings.slideshowInterval) {
                        binding.etSlideshowInterval.setText(settings.slideshowInterval.toString())
                    }
                    
                    // Switches (only update if value changed)
                    if (binding.switchPlayToEnd.isChecked != settings.playToEndInSlideshow) {
                        binding.switchPlayToEnd.isChecked = settings.playToEndInSlideshow
                    }
                    if (binding.switchAllowRename.isChecked != settings.allowRename) {
                        binding.switchAllowRename.isChecked = settings.allowRename
                    }
                    if (binding.switchAllowDelete.isChecked != settings.allowDelete) {
                        binding.switchAllowDelete.isChecked = settings.allowDelete
                    }
                    if (binding.switchConfirmDelete.isChecked != settings.confirmDelete) {
                        binding.switchConfirmDelete.isChecked = settings.confirmDelete
                    }
                    if (binding.switchGridMode.isChecked != settings.defaultGridMode) {
                        binding.switchGridMode.isChecked = settings.defaultGridMode
                    }
                    if (binding.switchShowCommandPanel.isChecked != settings.defaultShowCommandPanel) {
                        binding.switchShowCommandPanel.isChecked = settings.defaultShowCommandPanel
                    }
                    if (binding.switchDetailedErrors.isChecked != settings.showDetailedErrors) {
                        binding.switchDetailedErrors.isChecked = settings.showDetailedErrors
                    }
                    if (binding.switchShowPlayerHint.isChecked != settings.showPlayerHintOnFirstRun) {
                        binding.switchShowPlayerHint.isChecked = settings.showPlayerHintOnFirstRun
                    }
                    if (binding.switchShowVideoThumbnails.isChecked != settings.showVideoThumbnails) {
                        binding.switchShowVideoThumbnails.isChecked = settings.showVideoThumbnails
                    }
                    
                    // Icon size
                    val currentIconSize = binding.etIconSize.text.toString().toIntOrNull()
                    if (currentIconSize != settings.defaultIconSize) {
                        binding.etIconSize.setText(settings.defaultIconSize.toString())
                    }
                }
            }
        }
    }
    
    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> "Manual Order"
            SortMode.NAME_ASC -> "Name (A-Z)"
            SortMode.NAME_DESC -> "Name (Z-A)"
            SortMode.DATE_ASC -> "Date (Old first)"
            SortMode.DATE_DESC -> "Date (New first)"
            SortMode.SIZE_ASC -> "Size (Small first)"
            SortMode.SIZE_DESC -> "Size (Large first)"
            SortMode.TYPE_ASC -> "Type (A-Z)"
            SortMode.TYPE_DESC -> "Type (Z-A)"
            SortMode.RANDOM -> "Random"
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
                
                // Check if resources are available for adding destinations
                launch {
                    updateAddDestinationVisibility()
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
    
    private suspend fun updateAddDestinationVisibility() {
        val availableResources = viewModel.getWritableNonDestinationResources()
        val hasResources = availableResources.isNotEmpty()
        
        binding.btnAddDestination.isVisible = hasResources
        binding.tvNoResourcesMessage.isVisible = !hasResources
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
    ) : ListAdapter<MediaResource, DestinationsAdapter.ViewHolder>(DestinationDiffCallback) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDestinationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), position)
        }
        
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
    
    private object DestinationDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}

class GeneralSettingsFragment : Fragment() {

    private var _binding: com.sza.fastmediasorter_v2.databinding.FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()
    
    // Flag to prevent infinite loop when programmatically updating spinner
    private var isUpdatingSpinner = false

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
        setupVersionInfo()
        setupViews()
        observeData()
    }
    
    private fun setupVersionInfo() {
        val versionInfo = "${com.sza.fastmediasorter_v2.BuildConfig.VERSION_NAME} | Build ${com.sza.fastmediasorter_v2.BuildConfig.VERSION_CODE} | sza@ukr.net"
        binding.tvVersionInfo.text = versionInfo
    }

    private fun setupViews() {
        // Language Spinner
        val languages = resources.getStringArray(com.sza.fastmediasorter_v2.R.array.languages)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        binding.spinnerLanguage.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Skip if we're programmatically updating the spinner
                if (isUpdatingSpinner) return
                
                val newLanguageCode = when (position) {
                    0 -> "en"
                    1 -> "ru"
                    2 -> "uk"
                    else -> "en"
                }
                
                // Check if language actually changed compared to current settings
                val currentSettings = viewModel.settings.value
                if (newLanguageCode != currentSettings.language) {
                    // Update settings
                    viewModel.updateSettings(currentSettings.copy(language = newLanguageCode))
                    
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
        
        // User Guide Button
        binding.btnUserGuide.setOnClickListener {
            val intent = Intent(requireContext(), com.sza.fastmediasorter_v2.ui.welcome.WelcomeActivity::class.java)
            startActivity(intent)
        }
        
        // Permissions Buttons
        binding.btnLocalFilesPermission.setOnClickListener {
            requestStoragePermissions()
        }
        
        binding.btnNetworkPermission.setOnClickListener {
            // Network permissions (INTERNET, ACCESS_NETWORK_STATE) are granted automatically
            // They are declared in AndroidManifest.xml and don't require runtime permissions
            Toast.makeText(
                requireContext(), 
                "Network permissions are already granted automatically", 
                Toast.LENGTH_SHORT
            ).show()
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
                        // Set flag to prevent triggering onItemSelected
                        isUpdatingSpinner = true
                        binding.spinnerLanguage.setSelection(languagePosition, false)
                        // Reset flag after a short delay to allow UI to settle
                        binding.spinnerLanguage.post {
                            isUpdatingSpinner = false
                        }
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
                // Language already saved in DataStore by updateSettings()
                // Sync to SharedPreferences for app restart
                LocaleHelper.saveLanguage(requireActivity(), languageCode)
                // Restart app
                LocaleHelper.restartApp(requireActivity())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                // User declined restart - revert spinner to current active language
                // (the language that was loaded at app start, not the new selection)
                val currentLanguage = LocaleHelper.getLanguage(requireContext())
                val currentPosition = when (currentLanguage) {
                    "en" -> 0
                    "ru" -> 1
                    "uk" -> 2
                    else -> 0
                }
                isUpdatingSpinner = true
                binding.spinnerLanguage.setSelection(currentPosition, false)
                binding.spinnerLanguage.post { isUpdatingSpinner = false }
                
                // Revert settings to current active language
                val currentSettings = viewModel.settings.value
                viewModel.updateSettings(currentSettings.copy(language = currentLanguage))
                
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Request MANAGE_EXTERNAL_STORAGE
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:${requireContext().packageName}")
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general settings
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(requireContext(), "Storage permissions already granted", Toast.LENGTH_SHORT).show()
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Android 6-10 (API 23-29): Request READ/WRITE_EXTERNAL_STORAGE
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            
            val needsPermission = permissions.any { 
                androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), it) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED 
            }
            
            if (needsPermission) {
                requestPermissions(permissions, 100)
            } else {
                Toast.makeText(requireContext(), "Storage permissions already granted", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 5.x and below: permissions granted at install time
            Toast.makeText(requireContext(), "Storage permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
