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
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultFileWriter
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultLabels
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchResultRenderer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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

    private lateinit var frozenState: StopwatchScreenState
    private var frozenNowMillis: Long = 0L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStopwatchResultBinding.inflate(layoutInflater)
        frozenState = viewModel.state.value
        frozenNowMillis = viewModel.nowMillis()

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
            nowMillis = frozenNowMillis,
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
        val content = renderResult()
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
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

        fun newInstance(): StopwatchResultDialogFragment = StopwatchResultDialogFragment()
    }
}
