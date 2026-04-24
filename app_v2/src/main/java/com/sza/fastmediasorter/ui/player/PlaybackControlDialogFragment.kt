package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.databinding.DialogPlaybackControlBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

class PlaybackControlDialogFragment : DialogFragment() {

    private enum class StereoFamily {
        FLAT,
        SPHERICAL,
    }

    private enum class ControlSection(val buttonId: Int) {
        VOLUME(R.id.rbSectionVolume),
        AUDIO(R.id.rbSectionAudio),
        SUBTITLES(R.id.rbSectionSubtitles),
        STEREO(R.id.rbSectionStereo),
        HUE(R.id.rbSectionHue),
        BRIGHTNESS(R.id.rbSectionBrightness),
        SPEED(R.id.rbSectionSpeed)
    }

    private var _binding: DialogPlaybackControlBinding? = null
    private val binding: DialogPlaybackControlBinding
        get() = _binding!!

    private val speedSteps = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    private val hueProgressCenter = 180
    private val brightnessProgressCenter = 50
    private var isUpdatingStereoControls = false

    private val prefs by lazy {
        requireContext().getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val currentMediaType: MediaType?
        get() = host().currentMediaType.value

    private val activeSections: List<ControlSection>
        get() = when (currentMediaType) {
            MediaType.AUDIO -> listOf(ControlSection.VOLUME, ControlSection.SPEED)
            else -> buildList {
                add(ControlSection.VOLUME)
                add(ControlSection.AUDIO)
                add(ControlSection.SUBTITLES)
                // 3D/Stereo tab: shown in VR flavor (unless kill-switch is ON), AND in any flavor
                // when the current file has stereo content detected (SBS/OU image or video).
                val disable3dVr = arguments?.getBoolean(ARG_DISABLE_3D_VR, false) ?: false
                val currentStereo = host().stereoMode.value
                val isStereoContent = currentStereo != StereoMode.AUTO &&
                    currentStereo != StereoMode.MONO &&
                    currentStereo != StereoMode.UNKNOWN
                if (!disable3dVr && (BuildConfig.SUPPORT_VR_PLAYER || isStereoContent)) {
                    add(ControlSection.STEREO)
                }
                add(ControlSection.HUE)
                add(ControlSection.BRIGHTNESS)
                add(ControlSection.SPEED)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pass 0 so the dialog inherits the host Activity's DayNight theme (Theme.FastMediaSorter)
        // instead of forcing the light-only ThemeOverlay_Material3_MaterialAlertDialog overlay.
        setStyle(STYLE_NORMAL, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlaybackControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("PlaybackControlDialog: onViewCreated mediaType=$currentMediaType")
        setupSectionNavigation(savedInstanceState?.getInt(STATE_SELECTED_TAB) ?: prefs.getInt(PlaybackControlPreferences.KEY_LAST_TAB, 0))
        setupVolumeTab()
        if (currentMediaType == MediaType.VIDEO) {
            setupAudioTab()
            setupSubtitleTab()
            setupStereoSection()
            setupHueTab()
            setupBrightnessTab()
        }
        setupSpeedTab()
        // All changes are applied immediately from the controls, so the explicit action here is
        // only to close the dialog in an obvious way for touch users.
        binding.btnClosePlaybackControl.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95f).roundToInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, selectedSectionIndex())
    }

    private fun setupSectionNavigation(selectedIndex: Int) {
        ControlSection.entries.forEach { section ->
            binding.root.findViewById<RadioButton>(section.buttonId).isVisible = activeSections.contains(section)
        }

        val safeIndex = selectedIndex.coerceIn(0, activeSections.lastIndex.coerceAtLeast(0))
        val selectedSection = activeSections.getOrElse(safeIndex) { ControlSection.VOLUME }
        binding.radioGroupPlaybackSections.check(selectedSection.buttonId)
        updateVisibleSection(selectedSection)

        binding.radioGroupPlaybackSections.setOnCheckedChangeListener { _, checkedId ->
            val section = activeSections.firstOrNull { it.buttonId == checkedId } ?: return@setOnCheckedChangeListener
            Timber.d("PlaybackControlDialog: tab selected → $section")
            prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_TAB, activeSections.indexOf(section)).apply()
            updateVisibleSection(section)
        }
    }

    private fun selectedSectionIndex(): Int {
        val checkedId = binding.radioGroupPlaybackSections.checkedRadioButtonId
        return activeSections.indexOfFirst { it.buttonId == checkedId }.coerceAtLeast(0)
    }

    private fun updateVisibleSection(section: ControlSection?) {
        binding.sectionVolume.isVisible = false
        binding.sectionAudio.isVisible = false
        binding.sectionSubtitles.isVisible = false
        binding.sectionStereo3d.isVisible = false
        binding.sectionHue.isVisible = false
        binding.sectionBrightness.isVisible = false
        binding.sectionSpeed.isVisible = false

        when (section) {
            ControlSection.VOLUME -> binding.sectionVolume.isVisible = true
            ControlSection.AUDIO -> binding.sectionAudio.isVisible = true
            ControlSection.SUBTITLES -> binding.sectionSubtitles.isVisible = true
            ControlSection.STEREO -> binding.sectionStereo3d.isVisible = true
            ControlSection.HUE -> binding.sectionHue.isVisible = true
            ControlSection.BRIGHTNESS -> binding.sectionBrightness.isVisible = true
            ControlSection.SPEED -> binding.sectionSpeed.isVisible = true
            null -> binding.sectionVolume.isVisible = true
        }
    }

    private fun setupVolumeTab() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val halfVolume = (maxVolume / 2).coerceAtLeast(1)

        binding.seekVolume.max = maxVolume
        binding.seekVolume.progress = currentVolume
        updateVolumeLabel(currentVolume, maxVolume)
        syncMuteToggleUi(currentVolume)

        if (maxVolume == 0) {
            binding.seekVolume.isEnabled = false
            binding.btnMuteToggle.isEnabled = false
            binding.btnVolumeHalf.isEnabled = false
            binding.btnVolumeMax.isEnabled = false
            return
        }

        binding.btnVolumeHalf.setOnClickListener {
            applyVolumePreset(audioManager, halfVolume, maxVolume)
        }
        binding.btnVolumeMax.setOnClickListener {
            applyVolumePreset(audioManager, maxVolume, maxVolume)
        }

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, progress, 0)
                if (progress > 0) {
                    prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME, progress).apply()
                }
                updateVolumeLabel(progress, maxVolume)
                syncMuteToggleUi(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnMuteToggle.setOnClickListener {
            val liveVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (liveVolume == 0) {
                // Restore previously saved non-zero volume
                val restoreVolume = prefs.getInt(
                    PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME,
                    halfVolume
                )
                applyVolumePreset(audioManager, restoreVolume, maxVolume)
            } else {
                prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME, liveVolume).apply()
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                binding.seekVolume.progress = 0
                updateVolumeLabel(0, maxVolume)
                syncMuteToggleUi(0)
            }
        }
    }

    private fun applyVolumePreset(
        audioManager: android.media.AudioManager,
        volume: Int,
        maxVolume: Int
    ) {
        val targetVolume = volume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
        binding.seekVolume.progress = targetVolume
        if (targetVolume > 0) {
            // Quick presets should become the new restore target after the next mute toggle.
            prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME, targetVolume).apply()
        }
        updateVolumeLabel(targetVolume, maxVolume)
        syncMuteToggleUi(targetVolume)
    }

    private fun syncMuteToggleUi(volume: Int) {
        val isMuted = volume == 0
        binding.btnMuteToggle.isSelected = isMuted
        binding.btnMuteToggle.setText(
            if (isMuted) R.string.playback_control_unmute else R.string.playback_control_mute
        )
        ViewCompat.setStateDescription(
            binding.btnMuteToggle,
            getString(
                if (isMuted) R.string.playback_control_mute_active else R.string.playback_control_mute_inactive
            )
        )
    }

    private fun setupAudioTab() {
        val handle = host().videoPlayerHandle ?: return
        val tracks = handle.getAvailableAudioTracks()
        binding.groupAudioTracks.removeAllViews()
        binding.tvAudioEmpty.isVisible = tracks.isEmpty()

        tracks.forEach { track ->
            val button = RadioButton(requireContext()).apply {
                text = track.label
                isChecked = track.isSelected
                minHeight = (48 * resources.displayMetrics.density).roundToInt()
                // Audio/subtitle rows are added at runtime, so they need explicit focus flags to
                // participate in D-pad/gamepad navigation the same way as the XML-defined controls.
                isFocusable = true
                isFocusableInTouchMode = false
                setOnClickListener {
                    handle.selectAudioTrack(track.groupIndex, track.trackIndex)
                    refreshAudioTab()
                }
            }
            binding.groupAudioTracks.addView(button)
        }
    }

    private fun refreshAudioTab() {
        setupAudioTab()
    }

    private fun setupSubtitleTab() {
        val handle = host().videoPlayerHandle ?: return
        val tracks = handle.getAvailableSubtitleTracks()
        binding.groupSubtitleTracks.removeAllViews()
        binding.tvSubtitleEmpty.isVisible = tracks.isEmpty()

        val offButton = RadioButton(requireContext()).apply {
            text = getString(R.string.subtitle_off)
            isChecked = tracks.none { it.isSelected }
            minHeight = (48 * resources.displayMetrics.density).roundToInt()
            isFocusable = true
            isFocusableInTouchMode = false
            setOnClickListener {
                val groupIndex = tracks.firstOrNull()?.groupIndex ?: 0
                handle.selectSubtitleTrack(groupIndex, -1)
                refreshSubtitleTab()
            }
        }
        binding.groupSubtitleTracks.addView(offButton)

        tracks.forEach { track ->
            val button = RadioButton(requireContext()).apply {
                text = track.label
                isChecked = track.isSelected
                minHeight = (48 * resources.displayMetrics.density).roundToInt()
                isFocusable = true
                isFocusableInTouchMode = false
                setOnClickListener {
                    handle.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                    refreshSubtitleTab()
                }
            }
            binding.groupSubtitleTracks.addView(button)
        }
    }

    private fun refreshSubtitleTab() {
        setupSubtitleTab()
    }

    private fun setupStereoSection() {
        val currentMode = host().stereoMode.value
        bindStereoMode(currentMode)

        // Stereo mode is session-scoped in the ViewModel; switching it here immediately updates
        // the Media3 effects pipeline via the existing collector in PlayerManagerInitializer.
        binding.radioGroup3D.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingStereoControls) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radio3DSbs -> StereoMode.SBS_FULL
                R.id.radio3DOU -> StereoMode.OU
                R.id.radio3DMono -> StereoMode.MONO
                else -> StereoMode.AUTO
            }
            handleStereoModeSelection(mode)
        }

        binding.radioGroupSpherical3D.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingStereoControls) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radio360Mono -> StereoMode.EQUIRECT_360_MONO
                R.id.radio360Sbs -> StereoMode.EQUIRECT_360_SBS
                R.id.radio360Ou -> StereoMode.EQUIRECT_360_OU
                R.id.radioVr180 -> StereoMode.VR180_FISHEYE_SBS
                R.id.radioCylinder180 -> StereoMode.CYLINDER_180
                else -> StereoMode.AUTO
            }
            handleStereoModeSelection(mode)
        }

        binding.switchVrOverrideFormatType.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingStereoControls) return@setOnCheckedChangeListener
            updateStereoFamilyAvailability(resolveStereoFamily(host().stereoMode.value), isChecked)
        }

        // VR-specific controls (spec §5.8) — only visible in VR flavor
        if (BuildConfig.SUPPORT_VR_PLAYER) {
            setupVrStereoControls()
        }
    }

    /**
     * VR-only controls inside the 3D/Stereo tab (spec §5.8):
     * content type label, rendering mode chips, IPD slider.
     */
    private fun setupVrStereoControls() {
        // Content type label
        val contentTypeRes = when (currentMediaType) {
            MediaType.VIDEO -> R.string.playback_vr_content_type_video
            MediaType.IMAGE, MediaType.GIF -> R.string.playback_vr_content_type_photo
            else -> R.string.playback_vr_content_type_video
        }
        binding.tvVrContentType?.setText(contentTypeRes)
        binding.tvVrContentType?.isVisible = true

        // Rendering mode chips
        binding.tvVrRenderingModeLabel?.isVisible = true
        binding.chipGroupVrRenderingMode?.isVisible = true

        val currentRenderMode = prefs.getString(
            PlaybackControlPreferences.KEY_VR_RENDERING_MODE, "CINEMA"
        )
        when (currentRenderMode) {
            "FULL_STEREO" -> binding.chipRenderFullStereo?.isChecked = true
            else -> binding.chipRenderCinema?.isChecked = true
        }

        binding.chipGroupVrRenderingMode?.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when {
                checkedIds.contains(R.id.chipRenderFullStereo) -> "FULL_STEREO"
                else -> "CINEMA"
            }
            Timber.d("PlaybackControlDialog: VR rendering mode → $mode")
            prefs.edit().putString(PlaybackControlPreferences.KEY_VR_RENDERING_MODE, mode).apply()
        }

        // IPD slider (50-75mm range, mapped to 0-100 seekbar)
        binding.tvVrIpdLabel?.isVisible = true
        binding.seekVrIpd?.isVisible = true

        val savedIpd = prefs.getFloat(PlaybackControlPreferences.KEY_VR_IPD_MM, 63.0f)
        val ipdProgress = ((savedIpd - 50f) / 25f * 100f).toInt().coerceIn(0, 100)
        binding.seekVrIpd?.progress = ipdProgress
        updateIpdLabel(savedIpd)

        binding.seekVrIpd?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val ipdMm = 50f + (progress / 100f * 25f)
                Timber.d("PlaybackControlDialog: VR IPD → ${ipdMm}mm (progress=$progress)")
                prefs.edit().putFloat(PlaybackControlPreferences.KEY_VR_IPD_MM, ipdMm).apply()
                updateIpdLabel(ipdMm)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateIpdLabel(ipdMm: Float) {
        binding.tvVrIpdLabel?.text = getString(R.string.playback_vr_ipd_label, "%.1f".format(ipdMm))
    }

    private fun bindStereoMode(mode: StereoMode) {
        isUpdatingStereoControls = true
        binding.radioGroup3D.check(flatRadioIdFor(mode))
        binding.radioGroupSpherical3D.check(sphericalRadioIdFor(mode))
        binding.switchVrOverrideFormatType.isChecked = false
        updateStereoFamilyAvailability(resolveStereoFamily(mode), allowCrossFamilyOverride = false)
        updateStereoDetectedLabel(mode)
        isUpdatingStereoControls = false
    }

    private fun handleStereoModeSelection(mode: StereoMode) {
        Timber.d("PlaybackControlDialog: Stereo mode selected → $mode")

        host().rememberStereoModeIfEnabled(mode)
        host().setStereoMode(mode)

        val effectiveMode = host().stereoMode.value
        bindStereoMode(effectiveMode)

        if (mode != StereoMode.AUTO) {
            host().showMessage(
                getString(R.string.playback_settings_3d_manual_toast, stereoModeLabel(effectiveMode))
            )
        }
    }

    private fun updateStereoDetectedLabel(mode: StereoMode) {
        if (mode == StereoMode.AUTO) {
            binding.tvStereoDetected.isVisible = false
            return
        }
        binding.tvStereoDetected.isVisible = true
        binding.tvStereoDetected.text = getString(R.string.playback_settings_3d_current, stereoModeLabel(mode))
    }

    private fun updateStereoFamilyAvailability(activeFamily: StereoFamily, allowCrossFamilyOverride: Boolean) {
        val flatEnabled = allowCrossFamilyOverride || activeFamily == StereoFamily.FLAT
        val sphericalEnabled = allowCrossFamilyOverride || activeFamily == StereoFamily.SPHERICAL
        setGroupEnabled(binding.radioGroup3D, flatEnabled)
        setGroupEnabled(binding.radioGroupSpherical3D, sphericalEnabled)
    }

    private fun setGroupEnabled(group: ViewGroup, enabled: Boolean) {
        group.alpha = if (enabled) 1f else 0.5f
        group.isEnabled = enabled
        for (index in 0 until group.childCount) {
            group.getChildAt(index).isEnabled = enabled
        }
    }

    private fun resolveStereoFamily(mode: StereoMode): StereoFamily {
        if (mode.isSpherical()) return StereoFamily.SPHERICAL
        // Explicit flat modes (SBS/OU/MONO) always map to FLAT regardless of detectedStereoMode.
        // Only AUTO/UNKNOWN fall back to the detected result so the correct group is pre-selected.
        if (mode != StereoMode.AUTO && mode != StereoMode.UNKNOWN) return StereoFamily.FLAT
        val detectedMode = host().detectedStereoMode.value
        return if (detectedMode.isSpherical()) StereoFamily.SPHERICAL else StereoFamily.FLAT
    }

    private fun flatRadioIdFor(mode: StereoMode): Int = when (mode) {
        StereoMode.SBS_FULL, StereoMode.SBS_HALF -> R.id.radio3DSbs
        StereoMode.OU -> R.id.radio3DOU
        StereoMode.MONO -> R.id.radio3DMono
        else -> R.id.radio3DAuto
    }

    private fun sphericalRadioIdFor(mode: StereoMode): Int = when (mode) {
        StereoMode.EQUIRECT_360_MONO, StereoMode.EQUIRECT_180_MONO -> R.id.radio360Mono
        StereoMode.EQUIRECT_360_SBS, StereoMode.EQUIRECT_180_SBS -> R.id.radio360Sbs
        StereoMode.EQUIRECT_360_OU -> R.id.radio360Ou
        StereoMode.VR180_FISHEYE_SBS -> R.id.radioVr180
        StereoMode.CYLINDER_180 -> R.id.radioCylinder180
        else -> R.id.radio360Auto
    }

    private fun stereoModeLabel(mode: StereoMode): String = getString(
        when (mode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> R.string.playback_settings_3d_sbs
            StereoMode.OU -> R.string.playback_settings_3d_ou
            StereoMode.MONO -> R.string.playback_settings_3d_mono
            StereoMode.EQUIRECT_360_MONO, StereoMode.EQUIRECT_180_MONO -> R.string.playback_settings_3d_360_mono
            StereoMode.EQUIRECT_360_SBS, StereoMode.EQUIRECT_180_SBS -> R.string.playback_settings_3d_360_sbs
            StereoMode.EQUIRECT_360_OU -> R.string.playback_settings_3d_360_ou
            StereoMode.VR180_FISHEYE_SBS -> R.string.playback_settings_3d_vr180
            StereoMode.CYLINDER_180 -> R.string.playback_settings_3d_cylinder_180
            else -> R.string.playback_settings_3d_auto
        }
    )

    private fun setupHueTab() {
        val handle = host().videoPlayerHandle ?: return
        val savedHue = prefs.getFloat(
            PlaybackControlPreferences.KEY_HUE_DEGREES,
            handle.getHueAdjustmentDegrees()
        )
        binding.seekHue.max = hueProgressCenter * 2
        binding.seekHue.progress = hueToProgress(savedHue)
        updateHueLabel(savedHue)

        // Align the player with persisted state even if the dialog opens after player recreation.
        handle.setHueAdjustmentDegrees(savedHue)

        binding.seekHue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val hueDegrees = progressToHue(progress)
                Timber.d("PlaybackControlDialog: HUE slider → $hueDegrees° (progress=$progress)")
                prefs.edit().putFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, hueDegrees).apply()
                handle.setHueAdjustmentDegrees(hueDegrees)
                updateHueLabel(hueDegrees)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetHue.setOnClickListener {
            resetHue()
        }
    }

    private fun setupBrightnessTab() {
        val handle = host().videoPlayerHandle ?: return
        val brightnessProgress = prefs.getInt(
            PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
            handle.getBrightnessProgress()
        )
        binding.seekBrightness.progress = brightnessProgress
        updateBrightnessLabel(handle.getBrightnessPercentOffset())

        // Keep brightness state in the same Media3 chain as HUE so resets are deterministic.
        handle.setBrightnessProgress(brightnessProgress)

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Timber.d("PlaybackControlDialog: Brightness slider → progress=$progress")
                prefs.edit().putInt(PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT, progress).apply()
                handle.setBrightnessProgress(progress)
                updateBrightnessLabel(handle.getBrightnessPercentOffset())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetBrightness.setOnClickListener {
            resetBrightness()
        }
    }

    private fun setupSpeedTab() {
        val currentSpeed = host().videoPlayerHandle?.getPlaybackSpeed() ?: 1.0f
        val selectedIndex = speedSteps.indices.minByOrNull { index -> abs(speedSteps[index] - currentSpeed) } ?: 3
        // Drive max from speedSteps so XML and code stay in sync
        binding.seekSpeed.max = speedSteps.size - 1
        binding.seekSpeed.progress = selectedIndex
        updateSpeedLabel(speedSteps[selectedIndex])

        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val speed = speedSteps.getOrElse(progress) { 1.0f }
                Timber.d("PlaybackControlDialog: Speed slider → ${speed}x (progress=$progress)")
                host().videoPlayerHandle?.setPlaybackSpeed(speed)
                updateSpeedLabel(speed)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetSpeed.setOnClickListener {
            resetSpeed()
        }
    }

    private fun updateVolumeLabel(progress: Int, maxVolume: Int) {
        val percent = if (maxVolume == 0) 0 else (progress * 100f / maxVolume).roundToInt()
        binding.tvVolumeValue.text = getString(R.string.volume_level, percent)
    }

    private fun updateBrightnessLabel(progress: Int) {
        binding.tvBrightnessValue.text = getString(R.string.playback_control_brightness_value, progress)
    }

    private fun updateHueLabel(hueDegrees: Float) {
        binding.tvHueValue.text = getString(R.string.playback_control_hue_value, hueDegrees.toInt())
    }

    private fun updateSpeedLabel(speed: Float) {
        binding.tvSpeedValue.text = getString(R.string.playback_control_speed_value, speed)
    }

    private fun hueToProgress(hueDegrees: Float): Int =
        (hueDegrees.coerceIn(-180f, 180f) + hueProgressCenter).toInt()

    private fun progressToHue(progress: Int): Float =
        (progress.coerceIn(0, hueProgressCenter * 2) - hueProgressCenter).toFloat()

    private fun resetHue() {
        val handle = host().videoPlayerHandle ?: return
        prefs.edit().putFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, 0f).apply()
        binding.seekHue.progress = hueToProgress(0f)
        handle.setHueAdjustmentDegrees(0f)
        updateHueLabel(0f)
    }

    private fun resetBrightness() {
        val handle = host().videoPlayerHandle ?: return
        prefs.edit().putInt(PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT, brightnessProgressCenter).apply()
        binding.seekBrightness.progress = brightnessProgressCenter
        handle.setBrightnessProgress(brightnessProgressCenter)
        updateBrightnessLabel(handle.getBrightnessPercentOffset())
    }

    private fun resetSpeed() {
        val defaultSpeed = 1.0f
        val defaultIndex = speedSteps.indexOf(defaultSpeed).coerceAtLeast(0)
        binding.seekSpeed.progress = defaultIndex
        host().videoPlayerHandle?.setPlaybackSpeed(defaultSpeed)
        updateSpeedLabel(defaultSpeed)
    }

    /** Returns the host activity cast to [PlayerHostCapabilities]. Both [PlayerActivity] and
     * [StandalonePlayerActivity] implement this interface; if neither does, this will throw an
     * informative ClassCastException at the call site. */
    private fun host(): PlayerHostCapabilities = requireActivity() as PlayerHostCapabilities

    companion object {
        const val TAG = "PlaybackControlDialog"
        private const val STATE_SELECTED_TAB = "selected_tab"
        private const val ARG_DISABLE_3D_VR = "disable_3d_vr"

        fun newInstance(disable3dVrEnabled: Boolean = false): PlaybackControlDialogFragment =
            PlaybackControlDialogFragment().apply {
                arguments = android.os.Bundle().apply {
                    putBoolean(ARG_DISABLE_3D_VR, disable3dVrEnabled)
                }
            }
    }
}