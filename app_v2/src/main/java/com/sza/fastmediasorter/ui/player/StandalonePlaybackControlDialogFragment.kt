package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogPlaybackControlBinding
import com.sza.fastmediasorter.domain.model.MediaType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Standalone variant of the video Control dialog.
 *
 * Kept separate from PlaybackControlDialogFragment because standalone video owns a different
 * player stack (StandaloneViewManager) and cannot rely on PlayerActivity/VideoPlayerManager.
 */
class StandalonePlaybackControlDialogFragment : DialogFragment() {

    private enum class ControlSection(val buttonId: Int) {
        VOLUME(R.id.rbSectionVolume),
        AUDIO(R.id.rbSectionAudio),
        SUBTITLES(R.id.rbSectionSubtitles),
        HUE(R.id.rbSectionHue),
        BRIGHTNESS(R.id.rbSectionBrightness),
        SPEED(R.id.rbSectionSpeed)
    }

    private var _binding: DialogPlaybackControlBinding? = null
    private val binding: DialogPlaybackControlBinding
        get() = _binding!!

    private val speedSteps = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val hueProgressCenter = 180
    private val brightnessProgressCenter = 50

    private val prefs by lazy {
        requireContext().getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val currentMediaType: MediaType
        get() = standaloneActivity().currentMediaType() ?: MediaType.VIDEO

    private val activeSections: List<ControlSection>
        get() = if (currentMediaType == MediaType.AUDIO) {
            listOf(ControlSection.VOLUME, ControlSection.SPEED)
        } else {
            listOf(
                ControlSection.VOLUME,
                ControlSection.AUDIO,
                ControlSection.SUBTITLES,
                ControlSection.HUE,
                ControlSection.BRIGHTNESS,
                ControlSection.SPEED
            )
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
        setupSectionNavigation(savedInstanceState?.getInt(STATE_SELECTED_TAB) ?: prefs.getInt(PlaybackControlPreferences.KEY_LAST_TAB, 0))
        setupVolumeTab()
        setupAudioTab()
        setupSubtitleTab()
        setupHueTab()
        setupBrightnessTab()
        setupSpeedTab()
        // Standalone dialog uses live-apply controls too, so the button exists to make dismissal
        // explicit instead of relying on outside taps or the system back gesture.
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
        val allButtonIds = listOf(
            R.id.rbSectionVolume,
            R.id.rbSectionAudio,
            R.id.rbSectionSubtitles,
            R.id.rbSectionStereo,
            R.id.rbSectionHue,
            R.id.rbSectionBrightness,
            R.id.rbSectionSpeed
        )
        allButtonIds.forEach { id ->
            binding.root.findViewById<RadioButton>(id).isVisible = activeSections.any { it.buttonId == id }
        }
        binding.rbSectionStereo.isVisible = false

        val safeIndex = selectedIndex.coerceIn(0, activeSections.lastIndex)
        val selectedSection = activeSections.getOrElse(safeIndex) { ControlSection.VOLUME }
        binding.radioGroupPlaybackSections.check(selectedSection.buttonId)
        updateVisibleSection(selectedSection)

        binding.radioGroupPlaybackSections.setOnCheckedChangeListener { _, checkedId ->
            val section = activeSections.firstOrNull { it.buttonId == checkedId } ?: return@setOnCheckedChangeListener
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

        binding.seekVolume.max = maxVolume
        binding.seekVolume.progress = currentVolume
        updateVolumeLabel(currentVolume, maxVolume)

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, progress, 0)
                if (progress > 0) {
                    prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME, progress).apply()
                }
                updateVolumeLabel(progress, maxVolume)
                binding.btnMuteToggle.setText(
                    if (progress == 0) R.string.playback_control_unmute else R.string.playback_control_mute
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnMuteToggle.setText(if (currentVolume == 0) R.string.playback_control_unmute else R.string.playback_control_mute)
        binding.btnMuteToggle.setOnClickListener {
            val liveVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (liveVolume == 0) {
                val restoreVolume = prefs.getInt(
                    PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME,
                    (maxVolume / 2).coerceAtLeast(1)
                )
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, restoreVolume, 0)
                binding.seekVolume.progress = restoreVolume
                updateVolumeLabel(restoreVolume, maxVolume)
                binding.btnMuteToggle.setText(R.string.playback_control_mute)
            } else {
                prefs.edit().putInt(PlaybackControlPreferences.KEY_LAST_NON_ZERO_VOLUME, liveVolume).apply()
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                binding.seekVolume.progress = 0
                updateVolumeLabel(0, maxVolume)
                binding.btnMuteToggle.setText(R.string.playback_control_unmute)
            }
        }
    }

    private fun setupAudioTab() {
        val trackManager = standaloneActivity().standaloneTrackSelectionManager() ?: return
        val tracks = trackManager.getAvailableAudioTracks()
        binding.groupAudioTracks.removeAllViews()
        binding.tvAudioEmpty.isVisible = tracks.isEmpty()

        tracks.forEach { track ->
            val button = RadioButton(requireContext()).apply {
                text = track.label
                isChecked = track.isSelected
                minHeight = (48 * resources.displayMetrics.density).roundToInt()
                setOnClickListener {
                    trackManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                    setupAudioTab()
                }
            }
            binding.groupAudioTracks.addView(button)
        }
    }

    private fun setupSubtitleTab() {
        val trackManager = standaloneActivity().standaloneTrackSelectionManager() ?: return
        val tracks = trackManager.getAvailableSubtitleTracks()
        binding.groupSubtitleTracks.removeAllViews()
        binding.tvSubtitleEmpty.isVisible = tracks.isEmpty()

        val offButton = RadioButton(requireContext()).apply {
            text = getString(R.string.subtitle_off)
            isChecked = tracks.none { it.isSelected }
            minHeight = (48 * resources.displayMetrics.density).roundToInt()
            setOnClickListener {
                trackManager.selectSubtitleTrack(-1, -1)
                setupSubtitleTab()
            }
        }
        binding.groupSubtitleTracks.addView(offButton)

        tracks.forEach { track ->
            val button = RadioButton(requireContext()).apply {
                text = track.label
                isChecked = track.isSelected
                minHeight = (48 * resources.displayMetrics.density).roundToInt()
                setOnClickListener {
                    trackManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                    setupSubtitleTab()
                }
            }
            binding.groupSubtitleTracks.addView(button)
        }
    }

    private fun setupHueTab() {
        val viewManager = standaloneActivity().standaloneViewManager()
        val savedHue = prefs.getFloat(
            PlaybackControlPreferences.KEY_HUE_DEGREES,
            viewManager.getHueAdjustmentDegrees()
        )
        binding.seekHue.max = hueProgressCenter * 2
        binding.seekHue.progress = hueToProgress(savedHue)
        updateHueLabel(savedHue)
        viewManager.setHueAdjustmentDegrees(savedHue)

        binding.seekHue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val hueDegrees = progressToHue(progress)
                prefs.edit().putFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, hueDegrees).apply()
                viewManager.setHueAdjustmentDegrees(hueDegrees)
                updateHueLabel(hueDegrees)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetHue.setOnClickListener { resetHue() }
    }

    private fun setupBrightnessTab() {
        val viewManager = standaloneActivity().standaloneViewManager()
        val brightnessProgress = prefs.getInt(
            PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
            viewManager.getBrightnessProgress()
        )
        binding.seekBrightness.progress = brightnessProgress
        updateBrightnessLabel(viewManager.getBrightnessPercentOffset())
        viewManager.setBrightnessProgress(brightnessProgress)

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.edit().putInt(PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT, progress).apply()
                viewManager.setBrightnessProgress(progress)
                updateBrightnessLabel(viewManager.getBrightnessPercentOffset())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetBrightness.setOnClickListener { resetBrightness() }
    }

    private fun setupSpeedTab() {
        val viewManager = standaloneActivity().standaloneViewManager()
        val currentSpeed = viewManager.getPlaybackSpeed(currentMediaType)
        val selectedIndex = speedSteps.indices.minByOrNull { index -> abs(speedSteps[index] - currentSpeed) } ?: 3
        binding.seekSpeed.max = speedSteps.size - 1
        binding.seekSpeed.progress = selectedIndex
        updateSpeedLabel(speedSteps[selectedIndex])

        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val speed = speedSteps.getOrElse(progress) { 1.0f }
                viewManager.setPlaybackSpeed(currentMediaType, speed)
                updateSpeedLabel(speed)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnResetSpeed.setOnClickListener { resetSpeed() }
    }

    private fun resetHue() {
        prefs.edit().putFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, 0f).apply()
        binding.seekHue.progress = hueToProgress(0f)
        standaloneActivity().standaloneViewManager().setHueAdjustmentDegrees(0f)
        updateHueLabel(0f)
    }

    private fun resetBrightness() {
        prefs.edit().putInt(PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT, brightnessProgressCenter).apply()
        binding.seekBrightness.progress = brightnessProgressCenter
        standaloneActivity().standaloneViewManager().setBrightnessProgress(brightnessProgressCenter)
        updateBrightnessLabel(standaloneActivity().standaloneViewManager().getBrightnessPercentOffset())
    }

    private fun resetSpeed() {
        val defaultSpeed = 1.0f
        val defaultIndex = speedSteps.indexOf(defaultSpeed).coerceAtLeast(0)
        binding.seekSpeed.progress = defaultIndex
        standaloneActivity().standaloneViewManager().setPlaybackSpeed(currentMediaType, defaultSpeed)
        updateSpeedLabel(defaultSpeed)
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

    private fun standaloneActivity(): StandalonePlayerActivity = requireActivity() as StandalonePlayerActivity

    companion object {
        const val TAG = "StandalonePlaybackControlDialog"
        private const val STATE_SELECTED_TAB = "selected_tab"
    }
}