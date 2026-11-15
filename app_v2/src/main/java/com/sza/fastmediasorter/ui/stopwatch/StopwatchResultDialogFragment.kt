package com.sza.fastmediasorter.ui.stopwatch

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.DialogStopwatchResultBinding
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchLap
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchParticipant
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultFileWriter
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultLabels
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultRenderer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Collects a description and a note for a finished measurement and sends the result on its way
 * (strategic S1411 §2.6, phase 07).
 *
 * A DialogFragment rather than a dialog built inside the Activity, matching
 * [StopwatchSettingsDialogFragment]: §11.5 makes rotation safety an acceptance criterion for this screen,
 * and a bare AlertDialog would take the user's half-typed description down with it on the first rotation.
 *
 * The measurement is frozen the moment the dialog opens. A running stopwatch keeps running behind it, but
 * the preview, the saved file and the shared text then describe one instant instead of three.
 */
@AndroidEntryPoint
class StopwatchResultDialogFragment : DialogFragment() {

    private val viewModel: StopwatchViewModel by activityViewModels()

    @Inject
    lateinit var quantityFormatter: QuantityFormatter

    @Inject
    lateinit var unitSystemProvider: UnitSystemProvider

    private var _binding: DialogStopwatchResultBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding is only valid while the dialog exists" }

    /**
     * The visible participants as they stood when the dialog first opened, every one stopped at that
     * instant. Stopped copies are what [onSaveInstanceState] can carry through a rotation; re-reading the
     * live ViewModel state there would describe the rotation instead of the moment the user pressed Result.
     */
    private lateinit var frozenState: StopwatchScreenState

    /** Kept so a double Save cannot write a second file while the first one is still in flight. */
    private var saveJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStopwatchResultBinding.inflate(layoutInflater)
        frozenState = savedInstanceState?.let { restoreFrozenState(it) }
            ?: freeze(viewModel.state.value, viewModel.nowMillis())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stopwatch_result_title)
            .setView(binding.root)
            .create()

        binding.inputStopwatchResultDescription.doAfterTextChanged { renderPreview() }
        binding.inputStopwatchResultNote.doAfterTextChanged { renderPreview() }
        binding.btnStopwatchResultCancel.setOnClickListener { dialog.dismiss() }
        binding.btnStopwatchResultSave.setOnClickListener { saveToFile(dialog) }
        binding.btnStopwatchResultSend.setOnClickListener { sendToShareSheet(dialog) }
        renderPreview()
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Save is the confirm action; the description and note fields are single-line, so Enter on a
        // focused field falls through to the delegate and confirms rather than inserting a newline.
        DialogKeyboardDelegate.applyToDialogFragment(dialog) {
            binding.btnStopwatchResultSave.performClick()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::frozenState.isInitialized) return
        val participants = frozenState.visibleParticipants
        outState.putLongArray(KEY_ELAPSED, participants.map { it.accumulatedMillis }.toLongArray())
        outState.putLongArray(
            KEY_STARTED_AT,
            participants.map { it.startedAtEpochMillis ?: NO_START }.toLongArray(),
        )
        participants.forEachIndexed { index, participant ->
            outState.putLongArray(KEY_LAPS_PREFIX + index, participant.laps.map { it.atElapsedMillis }.toLongArray())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * The preview is the rendered text itself, not a description of it, so what the user reads before
     * pressing an action is byte for byte what leaves the screen.
     */
    private fun renderPreview() {
        binding.textStopwatchResultPreview.text = renderResult()
    }

    private fun renderResult(): String {
        return StopwatchResultRenderer.render(
            state = frozenState,
            // Every frozen participant is stopped, so the clock reading no longer changes its total.
            nowMillis = 0L,
            description = binding.inputStopwatchResultDescription.text?.toString().orEmpty(),
            note = binding.inputStopwatchResultNote.text?.toString().orEmpty(),
            labels = StopwatchResultLabels(
                participant = { index -> getString(R.string.stopwatch_region_label, index + 1) },
                laps = getString(R.string.stopwatch_result_laps),
                // S2792: the dialog owns the formatting, the renderer owns the line - this seam is what
                // keeps the renderer Context-free while the stamp still reads in the user's calendar.
                // S2795: the field order and the clock length come from the app's measurement system, not
                // from the locale, so a saved result matches the times shown everywhere else.
                measuredAt = { epochMillis ->
                    val stamp = quantityFormatter.format(Quantity.DateTime(epochMillis), unitSystemProvider.value)
                    getString(R.string.stopwatch_result_measured_at, stamp)
                },
            ),
        )
    }

    private fun saveToFile(dialog: Dialog) {
        if (saveJob?.isActive == true) return
        val content = renderResult()
        val appContext = requireContext().applicationContext
        saveJob = lifecycleScope.launch {
            // Downloads is real storage: on the pre-29 branch this is a file write and a media scan, and
            // neither belongs on the frame the user is looking at.
            val outcome = withContext(Dispatchers.IO) {
                StopwatchResultFileWriter.writeToDownloads(appContext, content)
            }
            outcome.fold(
                onSuccess = { fileName ->
                    toast(getString(R.string.stopwatch_result_saved, fileName))
                },
                onFailure = { error ->
                    Timber.w(error, "Stopwatch result could not be written to Downloads")
                    toast(getString(R.string.stopwatch_result_save_failed))
                },
            )
            dialog.dismiss()
        }
    }

    private fun sendToShareSheet(dialog: Dialog) {
        SystemShareInvoker.invoke(
            context = requireContext(),
            payload = SharePayload.Text(renderResult()),
            chooserTitle = getString(R.string.stopwatch_result_share_title),
        )
        dialog.dismiss()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "StopwatchResultDialog"

        private const val KEY_ELAPSED = "stopwatch_result_elapsed"
        private const val KEY_STARTED_AT = "stopwatch_result_started_at"
        private const val KEY_LAPS_PREFIX = "stopwatch_result_laps_"
        private const val NO_START = Long.MIN_VALUE

        private fun freeze(state: StopwatchScreenState, nowMillis: Long): StopwatchScreenState {
            val stopped = state.visibleParticipants.map { participant ->
                participant.copy(running = false, accumulatedMillis = participant.elapsedAt(nowMillis))
            }
            return StopwatchScreenState(participants = stopped, participantCount = stopped.size)
        }

        private fun restoreFrozenState(saved: Bundle): StopwatchScreenState? {
            val elapsed = saved.getLongArray(KEY_ELAPSED) ?: return null
            val startedAt = saved.getLongArray(KEY_STARTED_AT)
            val participants = elapsed.indices.map { index ->
                StopwatchParticipant(
                    id = index,
                    accumulatedMillis = elapsed[index],
                    startedAtEpochMillis = startedAt?.getOrNull(index)?.takeIf { it != NO_START },
                    laps = saved.getLongArray(KEY_LAPS_PREFIX + index)
                        ?.mapIndexed { lapIndex, atMillis -> StopwatchLap(lapIndex + 1, atMillis) }
                        .orEmpty(),
                )
            }
            return StopwatchScreenState(participants = participants, participantCount = participants.size)
        }

        fun newInstance(): StopwatchResultDialogFragment = StopwatchResultDialogFragment()
    }
}
