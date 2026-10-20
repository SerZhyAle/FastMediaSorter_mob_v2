package com.sza.fastmediasorter.ui.settings.cloud

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCrossDevicePacketsBinding
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.transfer.DrivePollState
import com.sza.fastmediasorter.ui.common.dialog.AppDialog
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S3040: the packets other devices left in the user's Drive queue, with the two accept options.
 *
 * A dialog rather than a settings section, like the neighbouring data-transfer surfaces: the list
 * only exists while a transfer is pending and has nothing to persist between visits.
 *
 * S4231: also the home of the Drive channel switches, this device's own sends still waiting, and the
 * delete action for any packet (strategic 3.4).
 */
@AndroidEntryPoint
class CrossDevicePacketListDialogFragment : DialogFragment() {

    private var _binding: DialogCrossDevicePacketsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: CrossDeviceTransferViewModel by viewModels()

    // S3792: one adapter for the dialog lifetime; DiffUtil rebinds only the rows that moved.
    private var packetsAdapter: CrossDevicePacketAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCrossDevicePacketsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCrossDevicePacketsClose.setOnClickListener { dismiss() }
        binding.btnCrossDeviceRemoveExpired.setOnClickListener { viewModel.removeExpiredPackets() }
        binding.btnCrossDeviceSendSettings.setOnClickListener { viewModel.sendSettings() }
        binding.rowDriveChannelEnabled.setOnCheckedChangeListener(viewModel::setChannelEnabled)
        binding.rowDriveChannelBackground.setOnCheckedChangeListener(viewModel::setBackgroundPoll)
        binding.btnDriveChannelPortal.setOnClickListener {
            MediaExchangePortalLinks.open(requireActivity(), MediaExchangePortalLinks.Page.FOLDERS_GOOGLE_DRIVE)
        }
        val adapter = CrossDevicePacketAdapter(requireContext()) { row ->
            if (row.outgoing) askDelete(row) else askReceiveOption(row)
        }
        packetsAdapter = adapter
        binding.listCrossDevicePackets.layoutManager = LinearLayoutManager(requireContext())
        binding.listCrossDevicePackets.adapter = adapter
        collectOnLifecycle(viewModel.state) { state -> render(state) }
        collectOnLifecycle(viewModel.channelEnabled) { enabled ->
            binding.rowDriveChannelEnabled.setCheckedSilently(enabled)
            binding.rowDriveChannelBackground.isEnabled = enabled
        }
        collectOnLifecycle(viewModel.backgroundPoll) { binding.rowDriveChannelBackground.setCheckedSilently(it) }
        collectOnLifecycle(viewModel.pollState, block = ::renderPollState)
        viewModel.refresh()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onVisible()
    }

    override fun onStop() {
        viewModel.onHidden()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // S3792: the list keeps its adapter through an observer registration, so the pair is
        // detached with the view rather than carried into the next dialog instance.
        binding.listCrossDevicePackets.adapter = null
        packetsAdapter = null
        _binding = null
    }

    private fun render(state: CrossDeviceQueueUiState) {
        val rows = state.packets.map { CrossDevicePacketRow(it, outgoing = false) } +
            state.outgoing.map { CrossDevicePacketRow(it, outgoing = true) }
        packetsAdapter?.submitList(rows)
        binding.tvCrossDevicePacketsEmpty.isVisible = rows.isEmpty() && !state.loading
        binding.progressCrossDeviceSend.isVisible = state.loading
        binding.btnCrossDeviceSendSettings.isEnabled = !state.loading
        binding.btnCrossDeviceRemoveExpired.isEnabled = !state.loading
        state.notice?.let { notice ->
            Snackbar.make(binding.root, noticeText(notice), Snackbar.LENGTH_LONG).show()
            viewModel.consumeNotice()
        }
    }

    private fun renderPollState(state: DrivePollState) {
        val text = when (state) {
            DrivePollState.SignInRequired -> getString(R.string.drive_channel_sign_in_required)
            DrivePollState.DailyLimit -> getString(R.string.drive_channel_daily_limit)
            is DrivePollState.BackingOff -> getString(R.string.drive_channel_backing_off)
            DrivePollState.Off, is DrivePollState.Polling -> null
        }
        binding.tvDriveChannelStatus.isVisible = text != null
        binding.tvDriveChannelStatus.text = text
    }

    private fun noticeText(notice: CrossDeviceQueueNotice): String = when (notice) {
        CrossDeviceQueueNotice.Failed -> getString(R.string.cross_device_transfer_failed)
        CrossDeviceQueueNotice.ReceivedFiles -> getString(R.string.cross_device_transfer_received_files)
        CrossDeviceQueueNotice.ReceivedSettings -> getString(R.string.cross_device_transfer_received_settings)
        CrossDeviceQueueNotice.SettingsSent -> getString(R.string.cross_device_transfer_sent)
        is CrossDeviceQueueNotice.ExpiredRemoved ->
            getString(R.string.cross_device_transfer_expired_removed, notice.removed)
        CrossDeviceQueueNotice.ChannelOff -> getString(R.string.drive_channel_off_hint)
        CrossDeviceQueueNotice.SignInRequired -> getString(R.string.drive_channel_sign_in_required)
        CrossDeviceQueueNotice.DailyLimit -> getString(R.string.drive_channel_daily_limit)
        is CrossDeviceQueueNotice.NotEnoughSpace -> getString(
            R.string.drive_channel_not_enough_space,
            Formatter.formatShortFileSize(requireContext(), notice.freeBytes ?: 0L)
        )
        is CrossDeviceQueueNotice.TooLarge -> getString(
            R.string.drive_channel_too_large,
            Formatter.formatShortFileSize(requireContext(), notice.limitBytes)
        )
        CrossDeviceQueueNotice.Deleted -> getString(R.string.drive_channel_deleted)
    }

    /**
     * The accept options are asked here rather than shown as buttons per row.
     *
     * Deleting from the cloud is irreversible and the rows are as narrow as the shortest supported
     * screen, so the choice gets its own prompt where both consequences fit as full sentences.
     */
    private fun askReceiveOption(row: CrossDevicePacketRow) {
        val options = arrayOf(
            getString(R.string.cross_device_transfer_receive_and_keep),
            getString(R.string.cross_device_transfer_receive_and_delete),
            getString(R.string.drive_channel_delete_without_receiving)
        )
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(packetsAdapter?.titleOf(row))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.receive(row.entry, CrossDeviceTransferOption.ACCEPT)
                    1 -> viewModel.receive(row.entry, CrossDeviceTransferOption.ACCEPT_AND_DELETE)
                    else -> askDelete(row)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {}
        dialog.showBoundTo(this)
    }

    private fun askDelete(row: CrossDevicePacketRow) {
        AppDialog.destructive(
            owner = this,
            context = requireContext(),
            title = packetsAdapter?.titleOf(row).orEmpty(),
            message = getString(R.string.drive_channel_delete_message),
            confirmLabel = getString(R.string.drive_channel_delete),
            onConfirm = { viewModel.delete(row.entry) }
        )
    }

    companion object {
        const val TAG = "CrossDevicePacketListDialog"
    }
}
