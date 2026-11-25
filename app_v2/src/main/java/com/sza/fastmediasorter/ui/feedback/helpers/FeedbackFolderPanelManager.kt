package com.sza.fastmediasorter.ui.feedback.helpers

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FeedbackInputsPanelBinding
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolderChoice
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import android.R as AndroidR

/**
 * S4573: the "folder on the computer" selector of the feedback form and the state line of the last packet
 * sent there. The first item is always "None", which leaves Send to.. as the only channel.
 */
class FeedbackFolderPanelManager(
    private val binding: FeedbackInputsPanelBinding,
    private val onChoose: (Long?) -> Unit,
) {

    private val context get() = binding.root.context
    private val row get() = binding.feedbackFolderRow
    private var ids: List<Long?> = emptyList()
    private var labels: List<String> = emptyList()
    private var rendered: Long? = null

    init {
        row.feedbackFolderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = ids.getOrNull(position)
                // Setting the adapter reports a selection of its own; only a move off the rendered one is the owner's.
                if (chosen != rendered) {
                    rendered = chosen
                    onChoose(chosen)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    fun render(choice: FeedbackFolderChoice) {
        row.root.isVisible = choice.available
        if (!choice.available) return
        val nextIds = listOf<Long?>(null) + choice.folders.map { it.resourceId }
        val none = context.getString(R.string.feedback_folder_none)
        val nextLabels = listOf(none) + choice.folders.map { folder ->
            if (folder.viaExchange) {
                context.getString(R.string.feedback_folder_via_exchange, folder.name)
            } else {
                folder.name
            }
        }
        rendered = choice.selectedId
        if (nextIds != ids || nextLabels != labels) {
            ids = nextIds
            labels = nextLabels
            row.feedbackFolderSpinner.adapter =
                ArrayAdapter(context, AndroidR.layout.simple_spinner_item, nextLabels).apply {
                    setDropDownViewResource(AndroidR.layout.simple_spinner_dropdown_item)
                }
        }
        val index = ids.indexOf(choice.selectedId).coerceAtLeast(0)
        if (row.feedbackFolderSpinner.selectedItemPosition != index) {
            row.feedbackFolderSpinner.setSelection(index, false)
        }
        val line = choice.lastPacket?.let(::stateLine)
        row.feedbackFolderState.isVisible = line != null
        row.feedbackFolderState.text = line
    }

    private fun stateLine(entry: TicketOutboxEntry): String = when (entry.state) {
        TicketOutboxState.QUEUED -> context.getString(R.string.feedback_state_queued)
        TicketOutboxState.WAITING ->
            context.getString(R.string.feedback_state_waiting, entry.failure.orEmpty())
        TicketOutboxState.SENT -> context.getString(R.string.feedback_state_sent)
        TicketOutboxState.PROCESSED ->
            context.getString(R.string.feedback_state_processed, entry.tickets.joinToString(", "))
        TicketOutboxState.FAILED -> context.getString(R.string.feedback_state_failed, entry.failure.orEmpty())
    }
}
