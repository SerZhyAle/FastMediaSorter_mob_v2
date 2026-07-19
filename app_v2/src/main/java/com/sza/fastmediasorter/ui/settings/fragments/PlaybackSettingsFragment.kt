package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.databinding.FragmentSettingsPlaybackBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.IsShareTargetEnabledUseCase
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.SettingsRowStackManager
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsPlaybackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject lateinit var shareTargetRegistry: ShareTargetRegistry
    @Inject lateinit var shareTargetAvailabilityResolver: ShareTargetAvailabilityResolver
    @Inject lateinit var isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase

    // S0838: same resolver the runtime «Send to..» menus use, so a configured receiver shows the
    // identical icon in Settings and in every menu (installed-app launcher icon, glyph fallback).
    @Inject lateinit var shareTargetIconResolver: ShareTargetIconResolver

    // S0452: dynamic "Send file to.." rows keyed by ShareTarget.id, refreshed in observeData.
    private val sendCommandRows = mutableMapOf<String, SettingsToggleRow>()

    private var isUpdatingFromSettings = false

    // S0439: player rotation toggle is hidden on devices without an orientation sensor.
    private val hasAccelerometer: Boolean by lazy {
        requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
    }

    // S0535: unified collapsible groups - one orchestrator + consolidated store replaces the
    // fragment-local section state machine (was a UI-layer business-logic violation).
    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }

    // S0577: background-audio block (moved from AudioSettingsFragment). The POST_NOTIFICATIONS
    // request (API 33+) is wired to the persistent-playback toggle.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = true))
            if (_binding == null) return@registerForActivityResult
            updateNotificationPermissionButtonVisibility()
        } else {
            val viewBinding = _binding ?: return@registerForActivityResult
            viewBinding.rowEnablePersistentAudioPlayback.setCheckedSilently(false)
            Snackbar.make(
                viewBinding.root,
                R.string.notification_permission_required_for_background,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as? ViewGroup)?.let(SettingsRowStackManager::stackNarrowPortraitRows)
        try {
            setupViews()
            setupBackgroundAudioSection()
            setupSendCommandsGroup()
            setupCollapsibleSections()
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
        // S0594: each visible entry maps to an explicit SortMode value (see PLAYBACK_SORT_MODES),
        // so the persisted value cannot drift from the displayed label if SortMode is reordered.
        val sortModeEntries: List<CharSequence> = PLAYBACK_SORT_MODES.map { getSortModeName(it) }
        binding.spinnerSortMode.setEntries(sortModeEntries)
        binding.spinnerSortMode.setOnItemSelectedListener { position ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val sortMode = PLAYBACK_SORT_MODES.getOrNull(position) ?: return@setOnItemSelectedListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(defaultSortMode = sortMode))
        }

        // S0567: Slideshow interval migrated to SettingsInputRow (numeric). The fixed-option dropdown
        // is dropped (ADR-1); the field stays free-form with the same 1..3600 clamp on commit.
        binding.etSlideshowInterval.text = getString(R.string.number_format, viewModel.settings.value.slideshowInterval)
        binding.etSlideshowInterval.setOnCommitListener { value ->
            val seconds = value.toString().toIntOrNull() ?: 5
            val clampedSeconds = seconds.coerceIn(1, 3600)
            if (seconds != clampedSeconds) {
                binding.etSlideshowInterval.text = getString(R.string.number_format, clampedSeconds)
            }
            val current = viewModel.settings.value
            if (clampedSeconds != current.slideshowInterval) {
                viewModel.updateSettings(current.copy(slideshowInterval = clampedSeconds))
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
        // Visibility is reactive (accelerometer present; independent of the program-wide toggle) - set in the settings observer.
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

        binding.rowSmallControls.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showSmallControls = isChecked))
        }

        binding.rowShowBlackScreenButton.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showBlackScreenButton = isChecked))
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

        // S0620: the switch reads "Disable 9-zone tracking", so checked == grid OFF.
        binding.rowDisableNineZone.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(nineZoneGridEnabled = !isChecked))
            applyNineZoneVisibility(gridEnabled = !isChecked)
        }

        // S0567: slideshow help folded into the SettingsInputRow (sir_showHelp); standalone icon removed.
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
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
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
     *
     * S0999: the rows lay out in [R.integer.settings_send_commands_columns] columns (2 in landscape
     * and on >=sw600dp, else 1). Rebuilt on rotation via [onConfigurationChanged] because
     * SettingsActivity handles config changes itself (no fragment recreation).
     */
    private fun setupSendCommandsGroup() {
        val container = binding.containerSendCommands
        container.removeAllViews()
        sendCommandRows.clear()
        val targets = shareTargetRegistry.all()
        // Hide the whole group while no target is registered, so users never see an empty section.
        binding.cardSendCommands.isVisible = targets.isNotEmpty()
        val columns = resources.getInteger(R.integer.settings_send_commands_columns)
        val targetCount = targets.size
        if (targets.isEmpty()) return
        val current = viewModel.settings.value
        val rows = targets.map { target ->
            buildSendCommandRow(target, current).also { sendCommandRows[target.id] = it }
        }
        // <=1 target OR a single-column config collapses to today's full-width vertical list.
        // min() also avoids empty weighted columns when a future bucket asks for more columns than rows.
        val effectiveColumns = minOf(columns, targetCount)
        if (effectiveColumns <= 1) {
            container.orientation = LinearLayout.VERTICAL
            rows.forEach(container::addView)
        } else {
            container.orientation = LinearLayout.HORIZONTAL
            distributeColumnMajor(container, rows, effectiveColumns)
        }
        upgradeSendCommandLabelsAndIcons(targets)
    }

    /**
     * S0452/S0463: build a single "Send file to.." toggle row for [target]. The row is
     * MATCH_PARENT-wide so it fills its parent (the container directly, or a weighted column - S0999).
     */
    private fun buildSendCommandRow(target: ShareTarget, current: AppSettings): SettingsToggleRow {
        return SettingsToggleRow(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            // S0474: start with the fast declared title; the installed-app label (ADR-5 parity
            // with SendToBottomSheet) is resolved off the main thread later and applied after.
            setTitle(getString(target.titleRes))
            // S0838: show each receiver's own glyph immediately; the installed-app launcher icon
            // (menu parity) is resolved off the main thread later and upgrades this in place.
            target.iconRes?.let { setIcon(it) }
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
    }

    /**
     * S0999: distribute [rows] across [columns] weighted vertical columns, column-major balanced -
     * fill the left column top-to-bottom first, then the next; the left column is longer by 1 when
     * the count is odd. Column-major keeps the vertical reading order (logical D-pad / TalkBack).
     */
    private fun distributeColumnMajor(
        container: LinearLayout,
        rows: List<SettingsToggleRow>,
        columns: Int,
    ) {
        val gap = resources.getDimensionPixelSize(R.dimen.dialog_field_spacing)
        val base = rows.size / columns
        val remainder = rows.size % columns
        var index = 0
        for (col in 0 until columns) {
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    SEND_COMMANDS_COLUMN_WEIGHT,
                ).apply { if (col > 0) marginStart = gap }
            }
            val count = base + if (col < remainder) 1 else 0
            repeat(count) {
                column.addView(rows[index])
                index++
            }
            container.addView(column)
        }
    }

    /**
     * S0474 + S0838: resolve installed-app labels AND launcher icons off the main thread
     * (PackageManager lookups must not block the Playback tab open); apply to existing rows on main.
     */
    private fun upgradeSendCommandLabelsAndIcons(targets: List<ShareTarget>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                targets.associate { t ->
                    t.id to (resolveShareTargetLabel(t) to shareTargetIconResolver.resolveIcon(t))
                }
            }
            resolved.forEach { (id, pair) ->
                sendCommandRows[id]?.let { row ->
                    row.setTitle(pair.first)
                    // Upgrade the glyph to the installed receiver app's launcher icon (menu parity);
                    // null keeps the glyph (logical or not-installed target).
                    pair.second?.let { icon -> row.setIcon(icon) }
                }
            }
        }
    }

    /**
     * S0999: SettingsActivity absorbs rotation via android:configChanges (no fragment recreation),
     * so rebuild the send-commands group here to pick up the per-orientation column count.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (_binding == null) return
        setupSendCommandsGroup()
    }

    /**
     * S0463: resolves the display label for a settings toggle row.
     *
     * Package-backed targets (non-empty [ShareTarget.packages]) show the installed app's own
     * label via PackageManager - consistent with SendToBottomSheet (S0459 ADR-5) and avoids
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

                    // S0594: select by explicit value lookup; a mode outside the visible subset yields
                    // -1 (unselected), preserving the legacy behaviour for modes the selector omits.
                    binding.spinnerSortMode.setSelection(PLAYBACK_SORT_MODES.indexOf(settings.defaultSortMode))

                    // Slideshow interval
                    val currentSlideshow = binding.etSlideshowInterval.text.toString().toIntOrNull()
                    if (currentSlideshow != settings.slideshowInterval) {
                        binding.etSlideshowInterval.text = getString(R.string.number_format, settings.slideshowInterval)
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
                    // Player toggle is independent of the program-wide toggle: shown whenever an accelerometer is present.
                    binding.layoutFollowSystemRotationPlayer.isVisible = hasAccelerometer
                    if (binding.rowFollowSystemRotationPlayer.isChecked != settings.playerFollowSystemRotation) {
                        binding.rowFollowSystemRotationPlayer.setCheckedSilently(settings.playerFollowSystemRotation)
                    }
                    if (binding.rowShowCommandPanel.isChecked != settings.defaultShowCommandPanel) {
                        binding.rowShowCommandPanel.setCheckedSilently(settings.defaultShowCommandPanel)
                    }
                    if (binding.rowShowBlackScreenButton.isChecked != settings.showBlackScreenButton) {
                        binding.rowShowBlackScreenButton.setCheckedSilently(settings.showBlackScreenButton)
                    }
                    if (binding.rowSmallControls.isChecked != settings.showSmallControls) {
                        binding.rowSmallControls.setCheckedSilently(settings.showSmallControls)
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
                    // S0620: switch shows the inverse of the stored flag (checked == grid disabled).
                    if (binding.rowDisableNineZone.isChecked != !settings.nineZoneGridEnabled) {
                        binding.rowDisableNineZone.setCheckedSilently(!settings.nineZoneGridEnabled)
                    }
                    applyNineZoneVisibility(gridEnabled = settings.nineZoneGridEnabled)

                    // S0452: refresh dynamic send-command rows from effective enabled state.
                    sendCommandRows.forEach { (id, row) ->
                        val enabled = isShareTargetEnabledUseCase(id, settings)
                        if (row.isChecked != enabled) row.setCheckedSilently(enabled)
                    }

                    // S0577: background-audio block (moved from Media/Audio).
                    if (BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
                        if (binding.rowEnablePersistentAudioPlayback.isChecked != settings.enablePersistentAudioPlayback) {
                            binding.rowEnablePersistentAudioPlayback.setCheckedSilently(settings.enablePersistentAudioPlayback)
                        }
                        if (binding.rowShowNowPlayingPanel.isChecked != settings.showNowPlayingPanel) {
                            binding.rowShowNowPlayingPanel.setCheckedSilently(settings.showNowPlayingPanel)
                        }
                        updateNotificationPermissionButtonVisibility()
                        val radioId = when (settings.backgroundAudioExitBehavior) {
                            BackgroundAudioExitBehavior.ALWAYS_STOP -> R.id.radioExitBehaviorAlwaysStop
                            BackgroundAudioExitBehavior.ALWAYS_CONTINUE -> R.id.radioExitBehaviorAlwaysContinue
                            BackgroundAudioExitBehavior.ASK -> R.id.radioExitBehaviorAsk
                        }
                        if (binding.radioGroupExitBehavior.checkedRadioButtonId != radioId) {
                            binding.radioGroupExitBehavior.check(radioId)
                        }
                        updateExitBehaviorVisibility()
                    }

                    isUpdatingFromSettings = false
        }
    }

    /**
     * S0620: when the 9-zone grid is disabled, hide every 9-zone-specific control in the block
     * (the hint/overlay toggles, the scheme button, the grid map + legend) and show the short
     * 3-zone explanation in their place. Re-show them when the grid is enabled.
     */
    private fun applyNineZoneVisibility(gridEnabled: Boolean) {
        binding.groupTouchZoneToggles.isVisible = gridEnabled
        binding.groupTouchZoneScheme.isVisible = gridEnabled
        binding.groupTouchZoneLegend.isVisible = gridEnabled
        binding.tvThreeZoneExplanation.isVisible = !gridEnabled
    }

    private fun setupCollapsibleSections() {
        sectionsManager.register(binding.headerSortingSlideshow, binding.containerSortingSlideshow, "playback__sorting")
        sectionsManager.register(binding.headerFileOperations, binding.containerFileOperations, "playback__file_ops")
        sectionsManager.register(binding.headerPlayerUI, binding.containerPlayerUI, "playback__player_ui")
        sectionsManager.register(binding.headerTouchZones, binding.containerTouchZones, "playback__touch_zones")
        sectionsManager.register(binding.headerSendCommands, binding.containerSendCommands, "playback__send_commands")
        sectionsManager.register(binding.headerBackgroundAudio, binding.containerBackgroundAudio, "playback__bg_audio")
    }

    // ── S0577: Background Audio Section (moved from AudioSettingsFragment) ──

    private fun setupBackgroundAudioSection() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
            // No background audio on lite/photos - hide the whole group.
            binding.cardBackgroundAudio.isVisible = false
            return
        }

        binding.rowEnablePersistentAudioPlayback.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                showBatteryOptimizationHintIfNeeded()
            }
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = isChecked))
            updateNotificationPermissionButtonVisibility()
            updateExitBehaviorVisibility()
        }

        binding.rowShowNowPlayingPanel.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showNowPlayingPanel = isChecked))
        }

        binding.btnNotificationPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }

        updateNotificationPermissionButtonVisibility()
        setupExitBehaviorSection()
    }

    private fun setupExitBehaviorSection() {
        binding.radioGroupExitBehavior.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            val behavior = when (checkedId) {
                R.id.radioExitBehaviorAlwaysStop -> BackgroundAudioExitBehavior.ALWAYS_STOP
                R.id.radioExitBehaviorAlwaysContinue -> BackgroundAudioExitBehavior.ALWAYS_CONTINUE
                else -> BackgroundAudioExitBehavior.ASK
            }
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(backgroundAudioExitBehavior = behavior))
        }
        updateExitBehaviorVisibility()
    }

    private fun updateExitBehaviorVisibility() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
        binding.layoutExitBehaviorSection.isVisible = binding.rowEnablePersistentAudioPlayback.isChecked
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateNotificationPermissionButtonVisibility() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
        binding.btnNotificationPermission.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isNotificationPermissionGranted() &&
                binding.rowEnablePersistentAudioPlayback.isChecked
    }

    private fun showBatteryOptimizationHintIfNeeded() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME_HINT, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_SHOWN_BATTERY_HINT, false)) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.battery_optimization_hint_title)
            .setMessage(R.string.battery_optimization_hint_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putBoolean(KEY_HAS_SHOWN_BATTERY_HINT, true).apply()
            }
            .setCancelable(false)
            .show()
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

    companion object {
        // S0594: explicit visible-option -> SortMode mapping for the Playback default-sort selector.
        // Only the media-type-agnostic modes are offered here (no artist/title/duration/dateTaken/type);
        // an explicit list keeps SortMode declaration order free for persistence concerns and removes the
        // fragile position<->enum-ordinal coupling (parity with BrowseSortDialogManager.DIALOG_SORT_ORDER).
        private val PLAYBACK_SORT_MODES = listOf(
            SortMode.RANDOM,
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.DATE_ASC,
            SortMode.DATE_DESC,
            SortMode.SIZE_ASC,
            SortMode.SIZE_DESC,
            SortMode.MANUAL,
        )

        // S0577: re-used from AudioSettingsFragment - same prefs file/key so the one-shot battery hint
        // is not re-shown after the block moved tabs.
        private const val PREFS_NAME_HINT = "playback_sections_state"
        private const val KEY_HAS_SHOWN_BATTERY_HINT = "has_shown_battery_hint_background_audio"

        // S0999: equal-width weight for each "Send file to.." column (named to stay detekt magic-number clean).
        private const val SEND_COMMANDS_COLUMN_WEIGHT = 1f
    }
}
