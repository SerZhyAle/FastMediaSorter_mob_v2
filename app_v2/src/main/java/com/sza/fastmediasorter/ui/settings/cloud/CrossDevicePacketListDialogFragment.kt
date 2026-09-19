package com.sza.fastmediasorter.ui.settings.cloud

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCrossDevicePacketsBinding
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * S3040: the packets other devices left in the user's Drive queue, with the two accept options.
 *
 * A dialog rather than a settings section, like the neighbouring data-transfer surfaces: the list
 * only exists while a transfer is pending and has nothing to persist between visits.
 */
@AndroidEntryPoint
class CrossDevicePacketListDialogFragment : DialogFragment() {

    private var _binding: DialogCrossDevicePacketsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: CrossDeviceTransferViewModel by viewModels()

    private var shownPackets: List<CrossDevicePacketManifest> = emptyList()

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
        Timber.d("S3040: packet queue dialog opened, refreshing pending packets")
        binding.btnCrossDevicePacketsClose.setOnClickListener { dismiss() }
        binding.btnCrossDeviceRemoveExpired.setOnClickListener { viewModel.removeExpiredPackets() }
        binding.btnCrossDeviceSendSettings.setOnClickListener { viewModel.sendSettings() }
        binding.listCrossDevicePackets.setOnItemClickListener { _, _, position, _ ->
            shownPackets.getOrNull(position)?.let(::askReceiveOption)
        }
        collectOnLifecycle(viewModel.state) { state -> render(state) }
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: CrossDeviceQueueUiState) {
        shownPackets = state.packets
        binding.listCrossDevicePackets.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            state.packets.map(::describe)
        )
        binding.tvCrossDevicePacketsEmpty.visibility =
            if (state.packets.isEmpty() && !state.loading) View.VISIBLE else View.GONE
        binding.progressCrossDeviceSend.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.btnCrossDeviceSendSettings.isEnabled = !state.loading
        binding.btnCrossDeviceRemoveExpired.isEnabled = !state.loading
        state.notice?.let { notice ->
            Snackbar.make(binding.root, noticeText(notice), Snackbar.LENGTH_LONG).show()
            viewModel.consumeNotice()
        }
    }

    private fun describe(manifest: CrossDevicePacketManifest): String {
        val age = DateUtils.getRelativeTimeSpanString(manifest.createdAtEpochMs)
        val files = manifest.fileNames.joinToString().ifEmpty { manifest.payloadKind.name }
        return "${manifest.senderDeviceName} - $files ($age)"
    }

    private fun noticeText(notice: CrossDeviceQueueNotice): String = when (notice) {
        CrossDeviceQueueNotice.Failed -> getString(R.string.cross_device_transfer_failed)
        CrossDeviceQueueNotice.ReceivedFiles -> getString(R.string.cross_device_transfer_received_files)
        CrossDeviceQueueNotice.ReceivedSettings -> getString(R.string.cross_device_transfer_received_settings)
        CrossDeviceQueueNotice.SettingsSent -> getString(R.string.cross_device_transfer_sent)
        is CrossDeviceQueueNotice.ExpiredRemoved ->
            getString(R.string.cross_device_transfer_expired_removed, notice.removed)
    }

    /**
     * The two accept options are asked here rather than shown as two buttons per row.
     *
     * Deleting from the cloud is irreversible and the rows are as narrow as the shortest supported
     * screen, so the choice gets its own prompt where both consequences fit as full sentences.
     */
    private fun askReceiveOption(manifest: CrossDevicePacketManifest) {
        val options = arrayOf(
            getString(R.string.cross_device_transfer_receive_and_keep),
            getString(R.string.cross_device_transfer_receive_and_delete)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(describe(manifest))
            .setItems(options) { _, which ->
                val option = if (which == 0) {
                    CrossDeviceTransferOption.ACCEPT
                } else {
                    CrossDeviceTransferOption.ACCEPT_AND_DELETE
                }
                viewModel.receive(manifest, option)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(this)
    }

    companion object {
        const val TAG = "CrossDevicePacketListDialog"
    }
}
