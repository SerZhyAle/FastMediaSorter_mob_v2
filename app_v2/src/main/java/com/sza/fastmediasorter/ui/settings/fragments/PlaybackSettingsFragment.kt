package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsPlaybackBinding
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import kotlinx.coroutines.launch

@android.annotation.SuppressLint("SetTextI18n")
class PlaybackSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsPlaybackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private var isUpdatingFromSettings = false

    companion object {
        private const val PREFS_NAME = "playback_sections_state"
        private const val KEY_SORTING_EXPANDED = "section_sorting_expanded"
        private const val KEY_FILE_OPS_EXPANDED = "section_file_ops_expanded"
        private const val KEY_GRID_VIEW_EXPANDED = "section_grid_view_expanded"
        private const val KEY_PLAYER_UI_EXPANDED = "section_player_ui_expanded"
        private const val KEY_TOUCH_ZONES_EXPANDED = "section_touch_zones_expanded"
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupViews()
            setupExpandableSections()
        } catch (e: Exception) {
            timber.log.Timber.tag("PlaybackSettings").e(e, "Error setting up views")
            Toast.makeText(context, getString(R.string.error_init_settings, e.message), Toast.LENGTH_LONG).show()
        }
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
        
        // Slideshow interval dropdown (1,5,10,30,60,120,300 sec)
        val slideshowOptions = arrayOf("1", "5", "10", "30", "60", "120", "300")
        val slideshowAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slideshowOptions)
        binding.etSlideshowInterval.setAdapter(slideshowAdapter)
        binding.etSlideshowInterval.setText(getString(R.string.number_format, viewModel.settings.value.slideshowInterval), false)
        
        binding.etSlideshowInterval.setOnItemClickListener { _, _, position, _ ->
            val seconds = slideshowOptions[position].toInt()
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(slideshowInterval = seconds))
        }
        
        // Handle manual input
        binding.etSlideshowInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etSlideshowInterval.text.toString()
                val seconds = text.toIntOrNull() ?: 5
                val clampedSeconds = seconds.coerceIn(1, 3600)
                if (seconds != clampedSeconds) {
                    binding.etSlideshowInterval.setText(getString(R.string.number_format, clampedSeconds), false)
                }
                val current = viewModel.settings.value
                if (clampedSeconds != current.slideshowInterval) {
                    viewModel.updateSettings(current.copy(slideshowInterval = clampedSeconds))
                }
            }
        }
        
        // Switches
        binding.switchPlayToEnd.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(playToEndInSlideshow = isChecked))
        }
        
        binding.switchAllowRename.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowRename = isChecked))
        }
        
        binding.switchAllowDelete.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowDelete = isChecked))
        }
        
        binding.switchConfirmDelete.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmDelete = isChecked))
        }
        
        binding.switchGridMode.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultGridMode = isChecked))
        }
        
        binding.switchHideGridActionButtons.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(hideGridActionButtons = isChecked))
        }
        
        binding.switchHideSystemUiInFullscreen.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(hideSystemUiInFullscreen = isChecked))
        }
        
        binding.switchShowCommandPanel.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultShowCommandPanel = isChecked))
        }
        
        binding.switchDetailedErrors.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }
        
        binding.switchShowPlayerHint.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showPlayerHintOnFirstRun = isChecked))
        }
        
        binding.switchAlwaysShowTouchZones.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(alwaysShowTouchZonesOverlay = isChecked))
        }
        
        binding.iconHelpSlideshow.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_slideshow_title,
                R.string.tooltip_slideshow_message
            )
        }
        
        binding.iconHelpTouchZones.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_touch_zones_title,
                R.string.tooltip_touch_zones_message
            )
        }
        
        binding.btnShowHintNow.setOnClickListener {
            // Reset first-run flag to trigger hint on next PlayerActivity launch
            viewModel.resetPlayerFirstRun()
            Toast.makeText(
                requireContext(),
                R.string.hint_will_be_shown_next_time,
                Toast.LENGTH_SHORT
            ).show()
        }

        // Phase 7: Hide Default Player section for flavors that don't support it
        binding.cardDefaultPlayer.isVisible = BuildConfig.SUPPORTS_DEFAULT_PLAYER

        if (BuildConfig.SUPPORTS_DEFAULT_PLAYER) {
            binding.switchPrimaryMediaPlayer.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyPrimaryPlayerState(requireContext(), isChecked)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(isPrimaryMediaPlayer = isChecked))
            }

            binding.switchAcceptSharedFiles.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                DefaultPlayerManager.applyShareReceiverState(requireContext(), isChecked)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(acceptSharedFiles = isChecked))
            }
        }

        binding.btnResetPlaybackSection.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_playback_section_title)
                .setMessage(R.string.reset_playback_section_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.resetPlaybackSection()
                    Toast.makeText(
                        requireContext(),
                        R.string.reset_playback_section_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        
        // Icon size dropdown (24-1024px)
        val iconSizeOptions = arrayOf("24", "32", "48", "64", "96", "128", "160", "192", "256", "320", "384", "512", "768", "1024")
        val iconSizeAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, iconSizeOptions)
        binding.etIconSize.setAdapter(iconSizeAdapter)
        binding.etIconSize.setText(getString(R.string.number_format, viewModel.settings.value.defaultIconSize), false)
        
        binding.etIconSize.setOnItemClickListener { _, _, position, _ ->
            val size = iconSizeOptions[position].toInt()
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultIconSize = size))
        }
        
        binding.iconHelpGridSize.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_grid_size_title,
                R.string.tooltip_grid_size_message
            )
        }

        binding.iconHelpCommandPanel.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_command_panel_title,
                R.string.tooltip_command_panel_message
            )
        }

        // PiP is only supported on Android 12+ (API 31)
        binding.layoutPip.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        binding.switchEnablePip.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePictureInPicture = isChecked))
        }

        // Handle manual input
        binding.etIconSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etIconSize.text.toString()
                val size = text.toIntOrNull() ?: 96
                val clampedSize = size.coerceIn(24, 1024)
                if (size != clampedSize) {
                    binding.etIconSize.setText(getString(R.string.number_format, clampedSize), false)
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
                    isUpdatingFromSettings = true
                    // Sort mode
                    binding.spinnerSortMode.setText(getSortModeName(settings.defaultSortMode), false)
                    
                    // Slideshow interval
                    val currentSlideshow = binding.etSlideshowInterval.text.toString().toIntOrNull()
                    if (currentSlideshow != settings.slideshowInterval) {
                        binding.etSlideshowInterval.setText(getString(R.string.number_format, settings.slideshowInterval), false)
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
                    if (binding.switchHideGridActionButtons.isChecked != settings.hideGridActionButtons) {
                        binding.switchHideGridActionButtons.isChecked = settings.hideGridActionButtons
                    }
                    if (binding.switchHideSystemUiInFullscreen.isChecked != settings.hideSystemUiInFullscreen) {
                        binding.switchHideSystemUiInFullscreen.isChecked = settings.hideSystemUiInFullscreen
                    }
                    if (binding.switchShowCommandPanel.isChecked != settings.defaultShowCommandPanel) {
                        binding.switchShowCommandPanel.isChecked = settings.defaultShowCommandPanel
                    }
                    if (binding.switchDetailedErrors.isChecked != settings.showDetailedErrors) {
                        binding.switchDetailedErrors.isChecked = settings.showDetailedErrors
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (binding.switchEnablePip.isChecked != settings.enablePictureInPicture) {
                            binding.switchEnablePip.isChecked = settings.enablePictureInPicture
                        }
                    }
                    if (binding.switchShowPlayerHint.isChecked != settings.showPlayerHintOnFirstRun) {
                        binding.switchShowPlayerHint.isChecked = settings.showPlayerHintOnFirstRun
                    }
                    if (binding.switchAlwaysShowTouchZones.isChecked != settings.alwaysShowTouchZonesOverlay) {
                        binding.switchAlwaysShowTouchZones.isChecked = settings.alwaysShowTouchZonesOverlay
                    }
                    
                    // Icon size
                    val currentIconSize = binding.etIconSize.text.toString().toIntOrNull()
                    if (currentIconSize != settings.defaultIconSize) {
                        binding.etIconSize.setText(getString(R.string.number_format, settings.defaultIconSize), false)
                    }

                    // Phase 5+6: Default Player toggles
                    if (BuildConfig.SUPPORTS_DEFAULT_PLAYER) {
                        if (binding.switchPrimaryMediaPlayer.isChecked != settings.isPrimaryMediaPlayer) {
                            binding.switchPrimaryMediaPlayer.isChecked = settings.isPrimaryMediaPlayer
                        }
                        if (binding.switchAcceptSharedFiles.isChecked != settings.acceptSharedFiles) {
                            binding.switchAcceptSharedFiles.isChecked = settings.acceptSharedFiles
                        }
                    }

                    isUpdatingFromSettings = false
                }
            }
        }
    }
    
    private fun setupExpandableSections() {
        // Restore saved states or default to collapsed
        val savedStates = getSavedSectionStates()
        
        bindSectionToggle(
            binding.headerSortingSlideshow, 
            binding.containerSortingSlideshow, 
            getString(R.string.settings_category_sorting_slideshow),
            KEY_SORTING_EXPANDED,
            savedStates[KEY_SORTING_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerFileOperations, 
            binding.containerFileOperations, 
            getString(R.string.settings_category_file_operations),
            KEY_FILE_OPS_EXPANDED,
            savedStates[KEY_FILE_OPS_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerGridView, 
            binding.containerGridView, 
            getString(R.string.settings_category_grid_view),
            KEY_GRID_VIEW_EXPANDED,
            savedStates[KEY_GRID_VIEW_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerPlayerUI, 
            binding.containerPlayerUI, 
            getString(R.string.settings_category_player_ui),
            KEY_PLAYER_UI_EXPANDED,
            savedStates[KEY_PLAYER_UI_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerTouchZones, 
            binding.containerTouchZones, 
            getString(R.string.settings_category_touch_zones),
            KEY_TOUCH_ZONES_EXPANDED,
            savedStates[KEY_TOUCH_ZONES_EXPANDED] ?: false
        )
    }

    private fun bindSectionToggle(
        header: android.widget.TextView, 
        content: View, 
        title: String,
        prefKey: String,
        initiallyExpanded: Boolean
    ) {
        // Set initial state
        content.isVisible = initiallyExpanded
        updateHeader(header, title, initiallyExpanded)

        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
            saveSectionState(prefKey, expanded)
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = getString(R.string.string_format_two_args, prefix, title)
    }
    
    /**
     * Get saved section states from SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations during fragment creation.
     */
    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_SORTING_EXPANDED to prefs.getBoolean(KEY_SORTING_EXPANDED, false),
                KEY_FILE_OPS_EXPANDED to prefs.getBoolean(KEY_FILE_OPS_EXPANDED, false),
                KEY_GRID_VIEW_EXPANDED to prefs.getBoolean(KEY_GRID_VIEW_EXPANDED, false),
                KEY_PLAYER_UI_EXPANDED to prefs.getBoolean(KEY_PLAYER_UI_EXPANDED, false),
                KEY_TOUCH_ZONES_EXPANDED to prefs.getBoolean(KEY_TOUCH_ZONES_EXPANDED, false)
            )
        }
    }
    
    /**
     * Save section expanded state to SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations.
     */
    private fun saveSectionState(key: String, expanded: Boolean) {
        StrictModeHelper.allowDiskWrites {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, expanded)
                .apply()
        }
    }

    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> getString(R.string.sort_mode_random)
        }
    }
}
