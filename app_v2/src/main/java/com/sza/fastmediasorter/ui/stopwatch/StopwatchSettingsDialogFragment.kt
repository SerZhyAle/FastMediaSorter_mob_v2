package com.sza.fastmediasorter.ui.stopwatch

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogStopwatchSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The stopwatch's own settings window (strategic S1411 §5.1, ADR-5 and ADR-6).
 *
 * Unlike the calculator's dialog it is reachable from two places - the tool's own gear button and a row
 * on the Settings screen's Operations tab (§6.4) - so it takes its state from the shared settings
 * repository rather than from a host screen's ViewModel, and its catalog entry carries a host key.
 *
 * The track row opens the in-app music picker (S2792): the system document picker's audio filter is
 * only as good as the OEM file provider, and the owner's device showed every file for a choice that
 * must be music and nothing else.
 */
@AndroidEntryPoint
class StopwatchSettingsDialogFragment : DialogFragment() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    @Inject lateinit var musicTrackRepository: MusicTrackRepository

    private var _binding: DialogStopwatchSettingsBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding is only valid while the dialog exists" }

    /** Edited in place and written back only when the user confirms, so cancel really cancels. */
    private var draft: AppSettings? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStopwatchSettingsBinding.inflate(layoutInflater)
        bindStaticRows()
        childFragmentManager.setFragmentResultListener(MusicPickerDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val uri = bundle.getString(MusicPickerDialogFragment.KEY_URI)
            if (!uri.isNullOrBlank()) {
                onTrackChosen(uri.toUri(), bundle.getString(MusicPickerDialogFragment.KEY_TITLE).orEmpty())
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stopwatch_settings_title)
            .setView(binding.root)
            .create()

        binding.btnStopwatchSettingsCancel.setOnClickListener { dialog.dismiss() }
        binding.btnStopwatchSettingsApply.setOnClickListener {
            applyAndDismiss(dialog)
        }
        loadDraft()
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindStaticRows() {
        binding.rowStopwatchParticipants.setEntries(
            AppSettings.STOPWATCH_PARTICIPANT_OPTIONS.map { count -> count.toString() },
        )
        // A build with no audio reaches none of the accompaniment code (Rule 14), so the rows that
        // would configure it are absent rather than present and dead.
        val audio = mediaCapabilities.supportsAudio
        binding.rowStopwatchMusic.isVisible = audio
        binding.rowStopwatchTrack.isVisible = audio
        binding.rowStopwatchTrack.setOnRowClickListener { openTrackPicker() }
    }

    /**
     * The rows are filled from the stored settings once the first snapshot arrives. Until then they
     * show their layout defaults, which is a fraction of a frame and never an editable wrong value:
     * the confirm button writes [draft], and [draft] does not exist before the load completes.
     */
    private fun loadDraft() {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            draft = settings
            renderDraft(settings)
            bindListeners()
        }
    }

    private suspend fun trackLabelFor(uri: String): CharSequence {
        if (uri.isEmpty()) {
            return getString(R.string.stopwatch_settings_track_none)
        }
        // A MediaStore pick resolves back to its title; a legacy SAF uri keeps the file name it
        // always showed, because the media store knows nothing about it.
        val title = musicTrackRepository.titleOf(uri.toUri())
        return title ?: uri.toUri().lastPathSegment ?: uri
    }

    private fun renderDraft(settings: AppSettings) {
        val index = AppSettings.STOPWATCH_PARTICIPANT_OPTIONS
            .indexOf(settings.stopwatchParticipantCount)
            .coerceAtLeast(0)
        binding.rowStopwatchParticipants.setSelection(index)
        binding.rowStopwatchMusic.setCheckedSilently(settings.stopwatchMusicEnabled)
        binding.rowStopwatchVolumeKeys.setCheckedSilently(settings.stopwatchVolumeKeysControl)
        lifecycleScope.launch {
            binding.rowStopwatchTrack.setValue(trackLabelFor(settings.stopwatchMusicUri))
        }
    }

    private fun bindListeners() {
        binding.rowStopwatchParticipants.setOnItemSelectedListener { index ->
            val count = AppSettings.STOPWATCH_PARTICIPANT_OPTIONS.getOrNull(index) ?: return@setOnItemSelectedListener
            draft = draft?.copy(stopwatchParticipantCount = count)
        }
        binding.rowStopwatchMusic.setOnCheckedChangeListener { checked ->
            draft = draft?.copy(stopwatchMusicEnabled = checked)
        }
        binding.rowStopwatchVolumeKeys.setOnCheckedChangeListener { checked ->
            draft = draft?.copy(stopwatchVolumeKeysControl = checked)
        }
    }

    private fun openTrackPicker() {
        runCatching {
            MusicPickerDialogFragment.newInstance()
                .show(childFragmentManager, MusicPickerDialogFragment.TAG)
        }.onFailure { error ->
            Timber.w(error, "Stopwatch settings could not open the music picker")
        }
    }

    /**
     * A MediaStore uri is stable and already readable by this app, so unlike the old SAF pick it
     * needs no persistable grant - taking one on a media uri simply fails.
     */
    private fun onTrackChosen(uri: Uri, title: String) {
        draft = draft?.copy(stopwatchMusicUri = uri.toString(), stopwatchMusicEnabled = true)
        binding.rowStopwatchTrack.setValue(
            title.ifBlank { uri.lastPathSegment ?: uri.toString() },
        )
        binding.rowStopwatchMusic.setCheckedSilently(true)
    }

    private fun applyAndDismiss(dialog: Dialog) {
        val edited = draft
        if (edited == null) {
            dialog.dismiss()
            return
        }
        lifecycleScope.launch {
            settingsRepository.updateSettings(edited)
            dialog.dismiss()
        }
    }

    companion object {
        const val TAG = "StopwatchSettingsDialog"

        fun newInstance(): StopwatchSettingsDialogFragment = StopwatchSettingsDialogFragment()
    }
}
