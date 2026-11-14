package com.sza.fastmediasorter.ui.settings.helpers

import android.net.Uri
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsToolsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.stopwatch.MusicPickerDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S4622: the stopwatch group of the Tools page. Each row writes its own field at once; the stopwatch
 * reads the settings flow, so a value changed here reaches it when it resumes.
 */
class ToolsStopwatchBlockManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsToolsBinding,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val musicTrackRepository: MusicTrackRepository,
) {

    fun bind() {
        binding.rowStopwatchParticipants.setEntries(
            AppSettings.STOPWATCH_PARTICIPANT_OPTIONS.map { count -> count.toString() },
        )
        // A build with no audio reaches none of the accompaniment code (Rule 14), so the rows that
        // would configure it are absent rather than present and dead.
        val audio = mediaCapabilities.supportsAudio
        binding.rowStopwatchMusic.isVisible = audio
        binding.rowStopwatchTrack.isVisible = audio
        binding.rowStopwatchTrack.setOnRowClickListener { openTrackPicker() }
        fragment.childFragmentManager.setFragmentResultListener(
            MusicPickerDialogFragment.REQUEST_KEY,
            fragment.viewLifecycleOwner,
        ) { _, bundle ->
            val uri = bundle.getString(MusicPickerDialogFragment.KEY_URI)
            if (!uri.isNullOrBlank()) {
                onTrackChosen(uri.toUri(), bundle.getString(MusicPickerDialogFragment.KEY_TITLE).orEmpty())
            }
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            render(settingsRepository.getSettings().first())
            bindListeners()
        }
    }

    private suspend fun render(settings: AppSettings) {
        val index = AppSettings.STOPWATCH_PARTICIPANT_OPTIONS
            .indexOf(settings.stopwatchParticipantCount)
            .coerceAtLeast(0)
        binding.rowStopwatchParticipants.setSelection(index)
        binding.rowStopwatchMusic.setCheckedSilently(settings.stopwatchMusicEnabled)
        binding.rowStopwatchVolumeKeys.setCheckedSilently(settings.stopwatchVolumeKeysControl)
        binding.rowStopwatchTrack.setValue(trackLabelFor(settings.stopwatchMusicUri))
    }

    private suspend fun trackLabelFor(uri: String): CharSequence {
        if (uri.isEmpty()) {
            return fragment.getString(R.string.stopwatch_settings_track_none)
        }
        // A MediaStore pick resolves back to its title; a legacy SAF uri keeps the file name it
        // always showed, because the media store knows nothing about it.
        return musicTrackRepository.titleOf(uri.toUri()) ?: uri.toUri().lastPathSegment ?: uri
    }

    private fun bindListeners() {
        binding.rowStopwatchParticipants.setOnItemSelectedListener { index ->
            val count = AppSettings.STOPWATCH_PARTICIPANT_OPTIONS.getOrNull(index)
                ?: return@setOnItemSelectedListener
            update { it.copy(stopwatchParticipantCount = count) }
        }
        binding.rowStopwatchMusic.setOnCheckedChangeListener { checked ->
            update { it.copy(stopwatchMusicEnabled = checked) }
        }
        binding.rowStopwatchVolumeKeys.setOnCheckedChangeListener { checked ->
            update { it.copy(stopwatchVolumeKeysControl = checked) }
        }
    }

    private fun openTrackPicker() {
        runCatching {
            MusicPickerDialogFragment.newInstance()
                .show(fragment.childFragmentManager, MusicPickerDialogFragment.TAG)
        }.onFailure { error ->
            Timber.w(error, "Tools page could not open the stopwatch music picker")
        }
    }

    /**
     * A MediaStore uri is stable and already readable by this app, so unlike the old SAF pick it
     * needs no persistable grant - taking one on a media uri simply fails.
     */
    private fun onTrackChosen(uri: Uri, title: String) {
        update { it.copy(stopwatchMusicUri = uri.toString(), stopwatchMusicEnabled = true) }
        binding.rowStopwatchTrack.setValue(title.ifBlank { uri.lastPathSegment ?: uri.toString() })
        binding.rowStopwatchMusic.setCheckedSilently(true)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.updateSettings { latest -> transform(latest) }
        }
    }
}
