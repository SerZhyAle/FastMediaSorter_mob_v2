package com.sza.fastmediasorter.ui.settings.ticket.helpers

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolderChoice
import android.R as AndroidR

/**
 * S4663: the composer's destination selector. Google Drive first and selected by default, then the
 * writable network folders the feedback form also offers, exchange-reached first. A destination whose
 * resource no longer exists falls back to Drive here, visibly; a packet already staged for a folder
 * keeps that folder - staged targeting is the delivery's, not the selector's.
 */
class TicketDestinationPickerManager(
    private val row: View,
    private val spinner: Spinner,
    private val onChoose: (Long?) -> Unit
) {

    private val context get() = row.context
    private var ids: List<Long?> = emptyList()
    private var labels: List<String> = emptyList()
    private var rendered: Long? = null

    init {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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

    fun render(choice: FeedbackFolderChoice, selectedId: Long?) {
        row.isVisible = choice.available
        if (!choice.available) return
        val nextIds = listOf<Long?>(null) + choice.folders.map { it.resourceId }
        val driveLabel = context.getString(R.string.ticket_inbox_destination_drive)
        val nextLabels = listOf(driveLabel) + choice.folders.map { folder ->
            if (folder.viaExchange) {
                context.getString(R.string.feedback_folder_via_exchange, folder.name)
            } else {
                folder.name
            }
        }
        if (nextIds != ids || nextLabels != labels) {
            ids = nextIds
            labels = nextLabels
            spinner.adapter =
                ArrayAdapter(context, AndroidR.layout.simple_spinner_item, nextLabels).apply {
                    setDropDownViewResource(AndroidR.layout.simple_spinner_dropdown_item)
                }
        }
        val effective = selectedId?.takeIf { id -> ids.contains(id) }
        if (effective != selectedId) {
            // The chosen resource is gone, so the selector falls back to Drive instead of targeting a ghost.
            onChoose(effective)
        }
        rendered = effective
        val index = ids.indexOf(effective).coerceAtLeast(0)
        if (spinner.selectedItemPosition != index) {
            spinner.setSelection(index, false)
        }
    }
}
