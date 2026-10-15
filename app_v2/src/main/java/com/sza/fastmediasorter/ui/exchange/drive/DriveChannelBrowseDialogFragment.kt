package com.sza.fastmediasorter.ui.exchange.drive

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDriveChannelBrowseBinding
import com.sza.fastmediasorter.ui.common.dialog.applyDialogInsets
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S4231: a folder of another own device opened via Google Drive after every live path failed (strategic
 * 3.4, ADR-2). Read-only: entries are listed and picked files are saved to the incoming folder.
 */
@AndroidEntryPoint
class DriveChannelBrowseDialogFragment : DialogFragment() {

    private var _binding: DialogDriveChannelBrowseBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: DriveChannelBrowseViewModel by viewModels()

    private var entriesAdapter: DriveChannelEntryAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDriveChannelBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDriveBrowseTitle.text =
            getString(R.string.drive_channel_browse_title, requireArguments().getString(ARG_TITLE).orEmpty())
        val adapter = DriveChannelEntryAdapter(
            onUp = viewModel::up,
            onFolder = viewModel::enter,
            onFile = viewModel::toggle
        )
        entriesAdapter = adapter
        binding.rvDriveBrowseEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDriveBrowseEntries.adapter = adapter
        binding.btnDriveBrowseClose.setOnClickListener { dismiss() }
        binding.btnDriveBrowseSave.setOnClickListener {
            val timedOut = viewModel.state.value.status is DriveBrowseStatus.TimedOut
            if (timedOut) viewModel.retry() else viewModel.saveSelected()
        }
        collectOnLifecycle(viewModel.state, block = ::render)
        if (savedInstanceState == null) viewModel.open()
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyDialogInsets()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvDriveBrowseEntries.adapter = null
        entriesAdapter = null
        _binding = null
    }

    private fun render(state: DriveBrowseUiState) {
        val up = if (state.canGoUp) listOf(DriveEntryRow(null)) else emptyList()
        entriesAdapter?.submitList(up + state.entries.map { DriveEntryRow(it, it.name in state.selected) })
        binding.progressDriveBrowse.isVisible = state.busy
        val status = state.status?.let(::statusText)
        binding.tvDriveBrowseStatus.isVisible = status != null
        binding.tvDriveBrowseStatus.text = status
        val timedOut = state.status is DriveBrowseStatus.TimedOut
        val action = if (timedOut) R.string.drive_channel_browse_retry else R.string.drive_channel_browse_save
        binding.btnDriveBrowseSave.setText(action)
        binding.btnDriveBrowseSave.isEnabled = timedOut || (!state.busy && state.selected.isNotEmpty())
    }

    private fun statusText(status: DriveBrowseStatus): String = when (status) {
        is DriveBrowseStatus.Waiting -> status.lastSeenMs?.let { seen ->
            getString(
                R.string.drive_channel_browse_waiting,
                status.producerName,
                DateUtils.getRelativeTimeSpanString(seen).toString()
            )
        } ?: getString(R.string.drive_channel_browse_waiting_unknown, status.producerName)
        is DriveBrowseStatus.TimedOut -> getString(R.string.drive_channel_browse_timeout, status.producerName)
        is DriveBrowseStatus.Saved -> getString(R.string.drive_channel_browse_saved, status.count)
        DriveBrowseStatus.Empty -> getString(R.string.drive_channel_browse_empty)
        DriveBrowseStatus.Locked -> getString(R.string.drive_channel_browse_locked)
        DriveBrowseStatus.NotPermitted -> getString(R.string.drive_channel_browse_not_permitted)
        DriveBrowseStatus.Unavailable ->
            getString(R.string.drive_channel_browse_unavailable, producerOrResourceName())
        DriveBrowseStatus.TooLarge -> getString(R.string.drive_channel_browse_too_large)
        DriveBrowseStatus.DriveFull -> getString(R.string.drive_channel_browse_drive_full)
        DriveBrowseStatus.ChannelOff -> getString(R.string.drive_channel_off_hint)
        DriveBrowseStatus.SignInRequired -> getString(R.string.drive_channel_sign_in_required)
        DriveBrowseStatus.DailyLimit -> getString(R.string.drive_channel_daily_limit)
        DriveBrowseStatus.Failed -> getString(R.string.cross_device_transfer_failed)
    }

    // Before the producer is resolved, only the name of the resource is known.
    private fun producerOrResourceName(): String =
        viewModel.producerName.ifEmpty { requireArguments().getString(ARG_TITLE).orEmpty() }

    companion object {
        const val TAG = "DriveChannelBrowseDialog"
        private const val ARG_TITLE = "title"

        fun show(manager: FragmentManager, exchangeResourceId: String, resourceUri: String, resourceName: String) {
            DriveChannelBrowseDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to resourceName,
                    DriveChannelBrowseViewModel.ARG_EXCHANGE_RESOURCE_ID to exchangeResourceId,
                    DriveChannelBrowseViewModel.ARG_RESOURCE_URI to resourceUri
                )
            }.show(manager, TAG)
        }
    }
}
