package com.sza.fastmediasorter.ui.launcher.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherYoutubeChannelBinding
import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.usecase.youtube.ResolveYouTubeChannelUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S2032: collects a channel address or an `@handle`, resolves it keylessly and reports the confirmed
 * channel back encoded.
 *
 * Strategic §6.1 fixed this form by the precedent of the weather cell, which is why the shape is that
 * dialog's rather than a new one. Cancelling and a channel that does not resolve both publish nothing,
 * so no cell is placed - strategic §11 criterion 2 requires exactly that.
 *
 * Like the weather dialog, one instance serves both entry points: placing a new cell (no cell id) and
 * re-pointing an existing one (with a cell id). The host decides which, not the dialog.
 */
@AndroidEntryPoint
class LauncherYouTubeChannelDialogFragment : DialogFragment() {

    @Inject
    lateinit var resolveChannel: ResolveYouTubeChannelUseCase

    private var _binding: DialogLauncherYoutubeChannelBinding? = null
    private val binding get() = _binding!!

    private var cellId: Long = NO_CELL_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        cellId = requireArguments().getLong(ARG_CELL_ID, NO_CELL_ID)
        // Registered here rather than in showChannel so a recreated fragment is listening again before
        // the restored confirmation picker resumes and replays its pick.
        parentFragmentManager.setFragmentResultListener(KEY_CHANNEL, this) { _, bundle ->
            bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)?.let(::publish)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogLauncherYoutubeChannelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnYouTubeChannelCancel.setOnClickListener { dismiss() }
        binding.btnYouTubeChannelSearch.setOnClickListener { runSearch() }
        binding.editYouTubeChannelQuery.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        // The query field consumes Enter through its editor action, so search stays wired there;
        // the delegate adds the Escape dismissal route and focus movement.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun runSearch() {
        val query = binding.editYouTubeChannelQuery.text?.toString().orEmpty()
        showStatus(R.string.launcher_youtube_channel_searching)
        binding.btnYouTubeChannelSearch.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val channel = resolveChannel(query)
            _binding?.let { safeBinding ->
                safeBinding.btnYouTubeChannelSearch.isEnabled = true
                if (channel == null) {
                    showStatus(R.string.launcher_youtube_channel_empty)
                } else {
                    showChannel(channel)
                }
            }
        }
    }

    /**
     * One option rather than a list: the resolve answers with exactly one channel, and confirming it is
     * what strategic §6.1 asks for - the user sees what was found before the cell is placed.
     */
    private fun showChannel(channel: YouTubeChannel) {
        SearchableOptionPickerDialog.newInstance(
            title = getString(R.string.launcher_youtube_channel_pick_title),
            options = listOf(Option(id = channel.encode(), label = channel.title)),
            selectedId = null,
            includeResetRow = false,
            requestKey = KEY_CHANNEL,
        ).show(parentFragmentManager, SearchableOptionPickerDialog.TAG)
    }

    private fun publish(encodedChannel: String) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()
        setFragmentResult(
            requestKey,
            bundleOf(RESULT_CHANNEL to encodedChannel, RESULT_CELL_ID to cellId),
        )
        dismiss()
    }

    private fun showStatus(messageRes: Int) {
        binding.tvYouTubeChannelStatus.setText(messageRes)
        binding.tvYouTubeChannelStatus.isVisible = true
    }

    companion object {
        const val TAG = "LauncherYouTubeChannelDialog"
        const val RESULT_CHANNEL = "result_channel"
        const val RESULT_CELL_ID = "result_cell_id"
        const val NO_CELL_ID = -1L

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_CELL_ID = "arg_cell_id"

        /** Key this dialog consumes, mirroring the [ARG_REQUEST_KEY] it publishes its own result on. */
        private const val KEY_CHANNEL = "launcher_youtube_channel_pick"

        fun newInstance(requestKey: String, cellId: Long = NO_CELL_ID): LauncherYouTubeChannelDialogFragment =
            LauncherYouTubeChannelDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey, ARG_CELL_ID to cellId)
            }
    }
}
