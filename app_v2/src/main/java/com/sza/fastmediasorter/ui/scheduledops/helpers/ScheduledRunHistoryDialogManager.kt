package com.sza.fastmediasorter.ui.scheduledops.helpers

import android.content.Context
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ScheduledOperationsEntryPoint
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.databinding.DialogScheduledRunHistoryBinding
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import com.sza.fastmediasorter.ui.scheduledops.ScheduledRunHistoryAdapter
import com.sza.fastmediasorter.ui.scheduledops.ScheduledRunHistoryRow
import com.sza.fastmediasorter.ui.scheduledops.ScheduledRunReasonText
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * S4211: shows one schedule's run journal - when each run happened, how many files it handled and why
 * it did not finish - newest first, one row per run. S4478: the reason is a sentence in the interface
 * language, and the technical text of the run sits behind a "Details" button instead of in the row.
 * A manager rather than screen code so the journal read stays off the activity (CLAUDE.md Rule 3).
 */
class ScheduledRunHistoryDialogManager(private val host: ComponentActivity) {

    fun show(operationId: Long, scheduleLabel: String) {
        host.lifecycleScope.launch {
            val journal = EntryPointAccessors
                .fromApplication(host.applicationContext, ScheduledOperationsEntryPoint::class.java)
                .getRunJournalUseCase()
            val records = journal(operationId)
            val body = DialogScheduledRunHistoryBinding.inflate(LayoutInflater.from(host))
            body.tvScheduleLabel.text = scheduleLabel
            body.tvRunHistoryEmpty.isVisible = records.isEmpty()
            body.rvRunHistory.isVisible = records.isNotEmpty()
            body.rvRunHistory.layoutManager = LinearLayoutManager(host)
            body.rvRunHistory.adapter = ScheduledRunHistoryAdapter(::showDetails).also { adapter ->
                adapter.submitList(records.map(::toRow))
            }
            MaterialAlertDialogBuilder(host)
                .setTitle(R.string.scheduled_ops_history_title)
                .setView(body.root)
                .setPositiveButton(R.string.close, null)
                .showBoundTo(host)
        }
    }

    /**
     * S4479: the answer to a tap on a card's status badge - the newest run in one sentence, with the full
     * history one button away. [skipped] comes from the stored status and only picks the neutral sentence
     * when the journal has nothing usable (cleared, or newer than the status it belongs to).
     */
    fun showLastRun(operationId: Long, scheduleLabel: String, skipped: Boolean) {
        host.lifecycleScope.launch {
            val journal = EntryPointAccessors
                .fromApplication(host.applicationContext, ScheduledOperationsEntryPoint::class.java)
                .getRunJournalUseCase()
            val newest = journal(operationId).firstOrNull()?.takeIf { it.outcome != ScheduledRunOutcome.OK }
            val outcome = newest?.outcome ?: if (skipped) ScheduledRunOutcome.SKIPPED else ScheduledRunOutcome.ERROR
            val sentence = newest?.let { toRow(it).headline }
                ?: host.getString(ScheduledRunReasonText.of(null, outcome))
            val message = buildString {
                append(scheduleLabel).append("\n\n").append(sentence)
                if (outcome == ScheduledRunOutcome.SKIPPED) {
                    append("\n\n").append(host.getString(R.string.scheduled_op_last_run_retry))
                }
            }
            MaterialAlertDialogBuilder(host)
                .setTitle(R.string.scheduled_op_last_run_title)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.scheduled_op_history) { _, _ -> show(operationId, scheduleLabel) }
                .showBoundTo(host)
        }
    }

    /** The technical text of a run in its own dialog; shared by the per-card journal and the Run log (S4493). */
    fun showDetails(text: String) {
        MaterialAlertDialogBuilder(host)
            .setTitle(R.string.scheduled_op_history_details)
            .setMessage(text)
            .setPositiveButton(R.string.close, null)
            .showBoundTo(host)
    }

    private fun toRow(record: ScheduledRunRecord): ScheduledRunHistoryRow {
        val whenText = formatTime(host, record.startedAt)
        val reason = host.getString(ScheduledRunReasonText.of(record.reason, record.outcome))
        val headline = when (record.outcome) {
            ScheduledRunOutcome.OK ->
                host.getString(R.string.scheduled_op_history_line_ok, whenText, record.filesProcessed)
            ScheduledRunOutcome.SKIPPED ->
                host.getString(R.string.scheduled_op_history_line_skipped, whenText, reason)
            ScheduledRunOutcome.ERROR ->
                host.getString(R.string.scheduled_op_history_line_error, whenText, record.filesProcessed, reason)
        }
        val details = listOfNotNull(record.message, record.note)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifEmpty { null }
        return ScheduledRunHistoryRow(record.startedAt, headline, details)
    }

    private fun formatTime(context: Context, at: Long): String {
        val seam = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UnitSystemEntryPoint::class.java
        )
        return seam.quantityFormatter().format(Quantity.DateTime(at), seam.unitSystemProvider().value)
    }
}
