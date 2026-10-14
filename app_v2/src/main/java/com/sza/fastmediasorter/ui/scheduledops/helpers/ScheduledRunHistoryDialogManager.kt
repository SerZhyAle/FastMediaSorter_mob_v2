package com.sza.fastmediasorter.ui.scheduledops.helpers

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ScheduledOperationsEntryPoint
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S4211: shows one schedule's run journal - when each run happened, how many files it handled and why
 * it did not finish - in a plain dialog, newest first. A manager rather than screen code so the
 * journal read stays off the activity (CLAUDE.md Rule 3).
 */
class ScheduledRunHistoryDialogManager(private val host: ComponentActivity) {

    fun show(operationId: Long, scheduleLabel: String) {
        host.lifecycleScope.launch {
            val journal = EntryPointAccessors
                .fromApplication(host.applicationContext, ScheduledOperationsEntryPoint::class.java)
                .getRunJournalUseCase()
            val records = journal(operationId)
            Timber.d("S4211: run history opened op=$operationId records=${records.size}")
            MaterialAlertDialogBuilder(host)
                .setTitle(R.string.scheduled_ops_history_title)
                .setMessage(buildMessage(scheduleLabel, records))
                .setPositiveButton(R.string.close, null)
                .showBoundTo(host)
        }
    }

    private fun buildMessage(scheduleLabel: String, records: List<ScheduledRunRecord>): String {
        val body = if (records.isEmpty()) {
            host.getString(R.string.scheduled_ops_history_empty)
        } else {
            records.joinToString("\n\n") { describe(it) }
        }
        return "$scheduleLabel\n\n$body"
    }

    private fun describe(record: ScheduledRunRecord): String {
        val whenText = formatTime(host, record.startedAt)
        val line = when (record.outcome) {
            ScheduledRunOutcome.OK ->
                host.getString(R.string.scheduled_op_history_line_ok, whenText, record.filesProcessed)
            ScheduledRunOutcome.SKIPPED ->
                host.getString(R.string.scheduled_op_history_line_skipped, whenText, record.message.orEmpty())
            ScheduledRunOutcome.ERROR -> host.getString(
                R.string.scheduled_op_history_line_error,
                whenText,
                record.filesProcessed,
                record.message.orEmpty(),
            )
        }
        return record.note?.let { "$line\n$it" } ?: line
    }

    private fun formatTime(context: Context, at: Long): String {
        val seam = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UnitSystemEntryPoint::class.java
        )
        return seam.quantityFormatter().format(Quantity.DateTime(at), seam.unitSystemProvider().value)
    }
}
