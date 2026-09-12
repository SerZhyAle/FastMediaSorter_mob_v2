package com.sza.fastmediasorter.ui.stopwatch

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogMusicPickerBinding
import com.sza.fastmediasorter.domain.model.stopwatch.MusicTrackOption
import com.sza.fastmediasorter.domain.usecase.stopwatch.LoadMusicTracksUseCase
import com.sza.fastmediasorter.ui.stopwatch.helpers.MusicTrackAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The in-app music chooser of the stopwatch settings (S2792).
 *
 * Replaces the system document picker, whose audio MIME filter is only as good as the OEM file
 * provider and showed the whole tree on the owner's device. Lists MediaStore audio only, and hands
 * the choice to the parent settings dialog through the Fragment Result API - the two fragments hold
 * no references to each other, so the parent's own rotation safety is untouched.
 */
@AndroidEntryPoint
class MusicPickerDialogFragment : DialogFragment() {

    @Inject lateinit var loadMusicTracks: LoadMusicTracksUseCase

    private var _binding: DialogMusicPickerBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding is only valid while the dialog exists" }

    private lateinit var adapter: MusicTrackAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMusicPickerBinding.inflate(layoutInflater)
        adapter = MusicTrackAdapter(onTrackClick = ::pick)
        binding.listMusicTracks.adapter = adapter
        binding.listMusicTracks.layoutManager = LinearLayoutManager(requireContext())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stopwatch_music_picker_title)
            .setView(binding.root)
            .create()
        loadTracks()
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** On demand and off the frame: the query runs in the repository's IO scope and dies with the dialog. */
    private fun loadTracks() {
        lifecycleScope.launch {
            val tracks = loadMusicTracks()
            adapter.submitList(tracks)
            binding.textMusicPickerEmpty.isVisible = tracks.isEmpty()
        }
    }

    private fun pick(track: MusicTrackOption) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                KEY_URI to track.uri.toString(),
                KEY_TITLE to track.title,
            ),
        )
        dismiss()
    }

    companion object {
        const val TAG = "MusicPickerDialog"
        const val REQUEST_KEY = "stopwatch_music_pick"
        const val KEY_URI = "picked_uri"
        const val KEY_TITLE = "picked_title"

        fun newInstance(): MusicPickerDialogFragment = MusicPickerDialogFragment()
    }
}
