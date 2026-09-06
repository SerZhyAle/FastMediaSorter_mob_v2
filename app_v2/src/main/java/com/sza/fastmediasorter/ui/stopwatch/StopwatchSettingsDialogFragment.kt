package com.sza.fastmediasorter.ui.stopwatch

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogStopwatchSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
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
 */
@AndroidEntryPoint
class StopwatchSettingsDialogFragment : DialogFragment() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    private var _binding: DialogStopwatchSettingsBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding is only valid while the dialog exists" }

    /** Edited in place and written back only when the user confirms, so cancel really cancels. */
    private var draft: AppSettings? = null

    private val selectTrackLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onTrackChosen(uri)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStopwatchSettingsBinding.inflate(layoutInflater)
        bindStaticRows()

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

    private fun renderDraft(settings: AppSettings) {
        val index = AppSettings.STOPWATCH_PARTICIPANT_OPTIONS
            .indexOf(settings.stopwatchParticipantCount)
            .coerceAtLeast(0)
        binding.rowStopwatchParticipants.setSelection(index)
        binding.rowStopwatchMusic.setCheckedSilently(settings.stopwatchMusicEnabled)
        binding.rowStopwatchVolumeKeys.setCheckedSilently(settings.stopwatchVolumeKeysControl)
        binding.rowStopwatchTrack.setValue(trackLabel(settings.stopwatchMusicUri))
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
        runCatching { selectTrackLauncher.launch(arrayOf(AUDIO_MIME)) }
            .onFailure { error ->
                // No document provider answers the intent on some minimal builds; the dialog stays
                // usable and the track simply remains unchosen.
                Timber.w(error, "Stopwatch settings could not open the track picker")
            }
    }

    private fun onTrackChosen(uri: Uri) {
        // Without the persistable grant the uri stops resolving after a reboot, and the setting would
        // point at a track the tool can no longer open.
        runCatching {
            requireContext().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { error ->
            Timber.w(error, "Stopwatch settings could not persist read access to the chosen track")
        }
        draft = draft?.copy(stopwatchMusicUri = uri.toString(), stopwatchMusicEnabled = true)
        binding.rowStopwatchTrack.setValue(trackLabel(uri.toString()))
        binding.rowStopwatchMusic.setCheckedSilently(true)
    }

    private fun trackLabel(uri: String): CharSequence =
        if (uri.isEmpty()) {
            getString(R.string.stopwatch_settings_track_none)
        } else {
            uri.toUri().lastPathSegment ?: uri
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

        private const val AUDIO_MIME = "audio/*"

        fun newInstance(): StopwatchSettingsDialogFragment = StopwatchSettingsDialogFragment()
    }
}
