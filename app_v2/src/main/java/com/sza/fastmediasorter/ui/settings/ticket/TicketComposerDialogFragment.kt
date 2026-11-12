package com.sza.fastmediasorter.ui.settings.ticket

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogTicketComposerBinding
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import com.sza.fastmediasorter.ui.common.dialog.DialogWindowSizer
import com.sza.fastmediasorter.ui.common.dialog.applyDialogInsets
import com.sza.fastmediasorter.ui.settings.ticket.helpers.TicketAttachmentPickerManager
import com.sza.fastmediasorter.ui.settings.ticket.helpers.TicketVoiceRecordManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * S4554: files a ticket into the SZA ticket inbox on the owner's Google Drive (INBOX-PACKET v1).
 *
 * A dialog beside the cross-device packet queue, like the other App data surfaces: the composer on
 * top (or left, in landscape) and this phone's outbox with each packet's state below it. The work
 * lives in the ViewModel and the two helpers; this class only wires views.
 */
@AndroidEntryPoint
class TicketComposerDialogFragment : DialogFragment() {

    private var _binding: DialogTicketComposerBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: TicketComposerViewModel by viewModels()

    private var voice: TicketVoiceRecordManager? = null
    private var attachmentsAdapter: TicketRowAdapter? = null
    private var outboxAdapter: TicketRowAdapter? = null
    private var shownProjects: List<TicketProject>? = null
    private var handledSends = 0

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val picker = TicketAttachmentPickerManager(requireContext())
        lifecycleScope.launch { viewModel.addAttachments(picker.resolve(uris)) }
    }

    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else snack(getString(R.string.ticket_inbox_voice_failed))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTicketComposerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { shown ->
            DialogWindowSizer.applyTo(shown)
            shown.applyDialogInsets()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val form = binding.composer
        val recorder = TicketVoiceRecordManager(requireContext())
        voice = recorder
        form.btnTicketVoice.isVisible = recorder.isAvailable()
        form.btnTicketVoice.setOnClickListener { toggleVoice() }
        form.btnTicketAttach.setOnClickListener { pickFiles.launch(arrayOf("*/*")) }
        binding.btnTicketClose.setOnClickListener { dismiss() }
        binding.btnTicketSend.setOnClickListener {
            val text = form.etTicketText.text?.toString().orEmpty()
            viewModel.send(text, form.spinnerTicketProject.selectedItemPosition)
        }
        binding.outbox.btnTicketRefresh.setOnClickListener { viewModel.refresh() }

        val attachments = TicketRowAdapter { row -> viewModel.removeAttachment(row.id) }
        attachmentsAdapter = attachments
        form.listTicketAttachments.layoutManager = LinearLayoutManager(requireContext())
        form.listTicketAttachments.adapter = attachments
        val outbox = TicketRowAdapter { row -> viewModel.discard(row.id) }
        outboxAdapter = outbox
        binding.outbox.listTicketOutbox.layoutManager = LinearLayoutManager(requireContext())
        binding.outbox.listTicketOutbox.adapter = outbox

        collectOnLifecycle(viewModel.state, block = ::render)
        collectOnLifecycle(viewModel.outbox, block = ::renderOutbox)
    }

    override fun onDestroyView() {
        voice?.release()
        voice = null
        binding.composer.listTicketAttachments.adapter = null
        binding.outbox.listTicketOutbox.adapter = null
        attachmentsAdapter = null
        outboxAdapter = null
        shownProjects = null
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: TicketComposerUiState) {
        val form = binding.composer
        attachmentsAdapter?.submitList(
            state.attachments.map { source ->
                TicketRow(
                    id = source.uri,
                    title = source.displayName,
                    subtitle = null,
                    removeLabel = getString(R.string.ticket_inbox_remove_attachment, source.displayName)
                )
            }
        )
        if (state.projectsLoaded && shownProjects !== state.projects) bindProjects(state.projects)
        form.tvTicketProjectsHint.isVisible = state.projectsLoaded && state.projects == null
        binding.outbox.progressTicket.isVisible = state.busy || !state.projectsLoaded
        binding.btnTicketSend.isEnabled = !state.busy
        if (state.sentCount != handledSends) {
            handledSends = state.sentCount
            form.etTicketText.text = null
        }
        state.notice?.let { notice ->
            snack(
                when (notice) {
                    TicketComposerNotice.Queued -> getString(R.string.ticket_inbox_queued_notice)
                    TicketComposerNotice.EmptyDraft -> getString(R.string.ticket_inbox_empty_draft)
                    is TicketComposerNotice.StageFailed -> getString(R.string.ticket_inbox_stage_failed, notice.reason)
                }
            )
            viewModel.consumeNotice()
        }
    }

    private fun bindProjects(projects: List<TicketProject>?) {
        shownProjects = projects
        val labels = listOf(getString(R.string.ticket_inbox_project_none)) +
            projects.orEmpty().map { "${it.name} (${it.key})" }
        binding.composer.spinnerTicketProject.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        binding.composer.spinnerTicketProject.isEnabled = !projects.isNullOrEmpty()
    }

    private fun renderOutbox(entries: List<TicketOutboxEntry>) {
        binding.outbox.tvTicketOutboxEmpty.isVisible = entries.isEmpty()
        outboxAdapter?.submitList(
            entries.map { entry ->
                val title = listOfNotNull(entry.project, entry.title.ifBlank { null }).joinToString(" - ")
                TicketRow(
                    id = entry.packetId,
                    title = title.ifBlank { entry.packetId },
                    subtitle = stateLine(entry),
                    removeLabel = getString(R.string.ticket_inbox_discard)
                )
            }
        )
    }

    private fun stateLine(entry: TicketOutboxEntry): String = when (entry.state) {
        TicketOutboxState.QUEUED -> getString(R.string.ticket_inbox_state_queued)
        TicketOutboxState.WAITING_ON_DRIVE ->
            getString(R.string.ticket_inbox_state_waiting, entry.failure.orEmpty())
        TicketOutboxState.SENT -> getString(R.string.ticket_inbox_state_sent)
        TicketOutboxState.PROCESSED ->
            getString(R.string.ticket_inbox_state_processed, entry.tickets.joinToString(", "))
        TicketOutboxState.FAILED -> getString(R.string.ticket_inbox_state_failed, entry.failure.orEmpty())
    }

    private fun toggleVoice() {
        val recorder = voice ?: return
        if (recorder.isRecording) {
            val file = recorder.stop()
            renderRecording(false)
            if (file == null) {
                snack(getString(R.string.ticket_inbox_voice_failed))
            } else {
                viewModel.addAttachments(listOf(TicketAttachmentPickerManager(requireContext()).voiceNote(file)))
            }
        } else {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoice() {
        if (voice?.start() == true) renderRecording(true) else snack(getString(R.string.ticket_inbox_voice_failed))
    }

    private fun renderRecording(recording: Boolean) {
        binding.composer.tvTicketRecording.isVisible = recording
        binding.composer.btnTicketVoice.setText(
            if (recording) R.string.ticket_inbox_stop_voice else R.string.ticket_inbox_record_voice
        )
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        const val TAG = "TicketComposerDialog"
    }
}
