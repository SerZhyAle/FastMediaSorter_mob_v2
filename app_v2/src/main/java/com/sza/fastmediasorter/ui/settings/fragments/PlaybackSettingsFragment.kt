package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.util.getApplicationInfoCompat
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
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsPlaybackBinding
import com.sza.fastmediasorter.domain.model.SortMode
import android.widget.LinearLayout
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.domain.usecase.IsShareTargetEnabledUseCase
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class PlaybackSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsPlaybackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject lateinit var shareTargetRegistry: ShareTargetRegistry
    @Inject lateinit var shareTargetAvailabilityResolver: ShareTargetAvailabilityResolver
    @Inject lateinit var isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase

    // S0452: dynamic "Send file to.." rows keyed by ShareTarget.id, refreshed in observeData.
    private val sendCommandRows = mutableMapOf<String, SettingsToggleRow>()

    private var isUpdatingFromSettings = false

    // S0439: player rotation toggle is hidden on devices without an orientation sensor.
    private val hasAccelerometer: Boolean by lazy {
        requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
    }

    companion object {
        private const val PREFS_NAME = "playback_sections_state"
        private const val KEY_SORTING_EXPANDED = "section_sorting_expanded"
        private const val KEY_FILE_OPS_EXPANDED = "section_file_ops_expanded"
        private const val KEY_PLAYER_UI_EXPANDED = "section_player_ui_expanded"
        private const val KEY_TOUCH_ZONES_EXPANDED = "section_touch_zones_expanded"
        private const val KEY_SEND_COMMANDS_EXPANDED = "section_send_commands_expanded"
    }

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupViews()
            setupSendCommandsGroup()
            setupExpandableSections()
        } catch (e: Exception) {
            timber.log.Timber.tag("PlaybackSettings").e(e, "Error setting up views")
            Toast.makeText(context, getString(R.string.error_init_settings), Toast.LENGTH_LONG).show()
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

        // Switches (migrated to SettingsToggleRow under S0258)
        binding.rowPlayToEnd.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(playToEndInSlideshow = isChecked))
        }

        binding.rowAllowRename.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowRename = isChecked))
        }

        binding.rowAllowDelete.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(allowDelete = isChecked))
        }

        binding.rowConfirmDelete.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(confirmDelete = isChecked))
        }

        binding.rowHideSystemUiInFullscreen.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(hideSystemUiInFullscreen = isChecked))
        }

        // S0439: player-scope Follow OS auto-rotate - listener persists the player flag.
        // Visibility is reactive (accelerometer present AND the program-wide toggle off) - set in the settings observer.
        if (hasAccelerometer) {
            binding.rowFollowSystemRotationPlayer.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(playerFollowSystemRotation = isChecked))
            }
        }

        binding.rowShowCommandPanel.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultShowCommandPanel = isChecked))
        }

        binding.rowDetailedErrors.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showDetailedErrors = isChecked))
        }

        binding.rowShowPlayerHint.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showPlayerHintOnFirstRun = isChecked))
        }

        binding.rowAlwaysShowTouchZones.setOnCheckedChangeListener { isChecked ->
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

        // Help for touch zones is now inline on rowAlwaysShowTouchZones (folded by SettingsToggleRow).

        binding.btnShowHintNow.setOnClickListener {
            // Reset first-run flag to trigger hint on next PlayerActivity launch
            viewModel.resetPlayerFirstRun()
            Toast.makeText(
                requireContext(),
                R.string.hint_will_be_shown_next_time,
                Toast.LENGTH_SHORT
            ).show()
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

        // Help for command panel is now inline on rowShowCommandPanel (folded by SettingsToggleRow).

        // PiP is only supported on Android 12+ (API 31)
        binding.layoutPip.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        binding.rowEnablePip.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePictureInPicture = isChecked))
        }

        // Panel single-eye 3D crop (spec_panel-stereo-single-eye)
        binding.rowPanelStereoSingleEye.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(panelStereoSingleEye = isChecked))
        }

        // S0158: Big Buttons Mode - stored in PlayerLayoutModePrefs (SharedPreferences, not DataStore).
        // ADR-2: change takes effect on next player open; no restart required.
        isUpdatingFromSettings = true
        binding.rowBigButtonsMode.setCheckedSilently(PlayerLayoutModePrefs.isBigButtonsMode(requireContext()))
        isUpdatingFromSettings = false
        binding.rowBigButtonsMode.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            PlayerLayoutModePrefs.setBigButtonsMode(requireContext(), isChecked)
        }
        // Help for big buttons mode is now inline on rowBigButtonsMode (folded by SettingsToggleRow).
    }

    /**
     * S0452: build one toggle per registered ShareTarget into the "Send file to.." group.
     * An unavailable target (e.g. its app is not installed) is disabled and marked with a
     * non-color "Not installed" subtitle. With an empty registry this renders nothing.
     * S0463: each row also shows a description subtitle and a (?) help button.
     */
    private fun setupSendCommandsGroup() {
        Timber.d("S0474: send-commands group built with fast titles; labels resolved async")
        val container = binding.containerSendCommands
        container.removeAllViews()
        sendCommandRows.clear()
        val targets = shareTargetRegistry.all()
        // Hide the whole group while no target is registered, so users never see an empty section.
        binding.cardSendCommands.isVisible = targets.isNotEmpty()
        val current = viewModel.settings.value
        targets.forEach { target ->
            val row = SettingsToggleRow(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                // S0474: start with the fast declared title; the installed-app label (ADR-5 parity
                // with SendToBottomSheet) is resolved off the main thread below and applied after.
                setTitle(getString(target.titleRes))
                val available = shareTargetAvailabilityResolver.isAvailable(target)
                isEnabled = available
                // S0463: show the target's description when available; "Not installed" otherwise.
                val subtitleText: CharSequence? = if (available) {
                    target.subtitleRes?.let { getString(it) }
                } else {
                    getString(R.string.settings_send_command_unavailable)
                }
                setSubtitle(subtitleText)
                // S0463: wire up the (?) help button when the target declares a help message.
                val hm = target.helpMessageRes
                if (hm != null) setHelp(target.titleRes, hm)
                setCheckedSilently(isShareTargetEnabledUseCase(target.id, current))
                setOnCheckedChangeListener { isChecked ->
                    if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                    val s = viewModel.settings.value
                    val enabled = s.enabledShareTargets.toMutableSet()
                    val disabled = s.disabledShareTargets.toMutableSet()
                    if (isChecked) {
                        enabled.add(target.id)
                        disabled.remove(target.id)
                    } else {
                        disabled.add(target.id)
                        enabled.remove(target.id)
                    }
                    viewModel.updateSettings(
                        s.copy(enabledShareTargets = enabled, disabledShareTargets = disabled)
                    )
                }
            }
            container.addView(row)
            sendCommandRows[target.id] = row
        }
        // S0474: resolve installed-app labels off the main thread (PackageManager lookups must not
        // block the Playback tab open); apply resolved labels to existing rows on the main thread.
        viewLifecycleOwner.lifecycleScope.launch {
            val labels = withContext(Dispatchers.IO) {
                targets.associate { it.id to resolveShareTargetLabel(it) }
            }
            labels.forEach { (id, label) -> sendCommandRows[id]?.setTitle(label) }
        }
    }

    /**
     * S0463: resolves the display label for a settings toggle row.
     *
     * Package-backed targets (non-empty [ShareTarget.packages]) show the installed app's own
     * label via PackageManager — consistent with SendToBottomSheet (S0459 ADR-5) and avoids
     * hardcoded brand literals. Falls back to [ShareTarget.titleRes] when no package resolves.
     * Logical targets always use [ShareTarget.titleRes].
     */
    private fun resolveShareTargetLabel(target: ShareTarget): CharSequence {
        if (target.packages.isEmpty()) return getString(target.titleRes)
        val pm = requireContext().packageManager
        for (pkg in target.packages) {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfoCompat(pkg))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (label != null) return label
        }
        return getString(target.titleRes)
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
                    isUpdatingFromSettings = true

                    // Sort mode
                    binding.spinnerSortMode.setText(getSortModeName(settings.defaultSortMode), false)

                    // Slideshow interval
                    val currentSlideshow = binding.etSlideshowInterval.text.toString().toIntOrNull()
                    if (currentSlideshow != settings.slideshowInterval) {
                        binding.etSlideshowInterval.setText(getString(R.string.number_format, settings.slideshowInterval), false)
                    }

                    // Switches (only update if value changed; setCheckedSilently avoids listener re-entry)
                    if (binding.rowPlayToEnd.isChecked != settings.playToEndInSlideshow) {
                        binding.rowPlayToEnd.setCheckedSilently(settings.playToEndInSlideshow)
                    }
                    if (binding.rowAllowRename.isChecked != settings.allowRename) {
                        binding.rowAllowRename.setCheckedSilently(settings.allowRename)
                    }
                    if (binding.rowAllowDelete.isChecked != settings.allowDelete) {
                        binding.rowAllowDelete.setCheckedSilently(settings.allowDelete)
                    }
                    if (binding.rowConfirmDelete.isChecked != settings.confirmDelete) {
                        binding.rowConfirmDelete.setCheckedSilently(settings.confirmDelete)
                    }
                    if (binding.rowHideSystemUiInFullscreen.isChecked != settings.hideSystemUiInFullscreen) {
                        binding.rowHideSystemUiInFullscreen.setCheckedSilently(settings.hideSystemUiInFullscreen)
                    }
                    // S0439: player toggle visible only when accelerometer present AND the program-wide toggle is off.
                    binding.layoutFollowSystemRotationPlayer.isVisible =
                        hasAccelerometer && !settings.programFollowSystemRotation
                    if (binding.rowFollowSystemRotationPlayer.isChecked != settings.playerFollowSystemRotation) {
                        binding.rowFollowSystemRotationPlayer.setCheckedSilently(settings.playerFollowSystemRotation)
                    }
                    if (binding.rowShowCommandPanel.isChecked != settings.defaultShowCommandPanel) {
                        binding.rowShowCommandPanel.setCheckedSilently(settings.defaultShowCommandPanel)
                    }
                    if (binding.rowDetailedErrors.isChecked != settings.showDetailedErrors) {
                        binding.rowDetailedErrors.setCheckedSilently(settings.showDetailedErrors)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (binding.rowEnablePip.isChecked != settings.enablePictureInPicture) {
                            binding.rowEnablePip.setCheckedSilently(settings.enablePictureInPicture)
                        }
                    }
                    if (binding.rowPanelStereoSingleEye.isChecked != settings.panelStereoSingleEye) {
                        binding.rowPanelStereoSingleEye.setCheckedSilently(settings.panelStereoSingleEye)
                    }
                    if (binding.rowShowPlayerHint.isChecked != settings.showPlayerHintOnFirstRun) {
                        binding.rowShowPlayerHint.setCheckedSilently(settings.showPlayerHintOnFirstRun)
                    }
                    if (binding.rowAlwaysShowTouchZones.isChecked != settings.alwaysShowTouchZonesOverlay) {
                        binding.rowAlwaysShowTouchZones.setCheckedSilently(settings.alwaysShowTouchZonesOverlay)
                    }

                    // S0452: refresh dynamic send-command rows from effective enabled state.
                    sendCommandRows.forEach { (id, row) ->
                        val enabled = isShareTargetEnabledUseCase(id, settings)
                        if (row.isChecked != enabled) row.setCheckedSilently(enabled)
                    }

                    isUpdatingFromSettings = false
        }
    }

    private fun setupExpandableSections() {
        val savedStates = getSavedSectionStates()
        val sections = listOf(
            ExpandableSection(binding.headerSortingSlideshow, binding.containerSortingSlideshow, KEY_SORTING_EXPANDED, false),
            ExpandableSection(binding.headerFileOperations, binding.containerFileOperations, KEY_FILE_OPS_EXPANDED, false),
            ExpandableSection(binding.headerPlayerUI, binding.containerPlayerUI, KEY_PLAYER_UI_EXPANDED, false),
            ExpandableSection(binding.headerTouchZones, binding.containerTouchZones, KEY_TOUCH_ZONES_EXPANDED, false),
            ExpandableSection(binding.headerSendCommands, binding.containerSendCommands, KEY_SEND_COMMANDS_EXPANDED, false),
        )

        sections.forEach { section ->
            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                saveSectionState(section.prefKey, isExpanded)
            }
        }
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
                KEY_PLAYER_UI_EXPANDED to prefs.getBoolean(KEY_PLAYER_UI_EXPANDED, false),
                KEY_TOUCH_ZONES_EXPANDED to prefs.getBoolean(KEY_TOUCH_ZONES_EXPANDED, false),
                KEY_SEND_COMMANDS_EXPANDED to prefs.getBoolean(KEY_SEND_COMMANDS_EXPANDED, false)
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
