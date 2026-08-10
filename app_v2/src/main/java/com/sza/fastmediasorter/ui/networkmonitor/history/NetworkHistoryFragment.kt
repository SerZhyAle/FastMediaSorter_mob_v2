package com.sza.fastmediasorter.ui.networkmonitor.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogNetworkMonitorClearHistoryBinding
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorHistoryBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: the History subscreen - the stored measurements, a clear-all behind a confirmation and an export
 * that hands the file to the system share sheet.
 *
 * Both actions hide themselves while the history is empty: strategic §11 criterion 16 asks for a history
 * that can be viewed, cleared and exported, and an export of nothing or a confirmation to delete nothing are
 * buttons that can only disappoint.
 */
@AndroidEntryPoint
class NetworkHistoryFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorHistoryBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: NetworkHistoryViewModel by viewModels()

    private val adapter = NetworkHistoryAdapter()

    private var clearDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        binding.btnHistoryExport.setOnClickListener { viewModel.onExportRequested() }
        binding.btnHistoryClear.setOnClickListener { showClearConfirmation() }
        collectOnLifecycle(viewModel.measurements) { render(it) }
        collectOnLifecycle(viewModel.exportOutcome) { applyExportOutcome(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearDialog?.dismiss()
        clearDialog = null
        binding.historyList.adapter = null
        _binding = null
    }

    private fun render(measurements: List<NetworkMeasurement>) {
        val hasRows = measurements.isNotEmpty()
        adapter.submitList(measurements)
        binding.historyList.isVisible = hasRows
        binding.historyEmpty.isVisible = !hasRows
        binding.btnHistoryExport.isVisible = hasRows
        binding.btnHistoryClear.isVisible = hasRows
    }

    /**
     * A custom layout rather than a builder dialog, because CLAUDE.md §11 fixes the taxonomy for a
     * destructive pair: the confirm button is red `DialogDestructive`, the cancel is the tonal
     * `DialogCancel`, and a builder would give the confirm the ordinary affirmative style instead.
     */
    private fun showClearConfirmation() {
        clearDialog?.dismiss()
        val content = DialogNetworkMonitorClearHistoryBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(content.root)
            .create()
        content.btnCancel.setOnClickListener { dialog.dismiss() }
        content.btnClearHistory.setOnClickListener {
            viewModel.onClearConfirmed()
            dialog.dismiss()
        }
        clearDialog = dialog.showBoundTo(this)
    }

    private fun applyExportOutcome(outcome: HistoryExportOutcome) {
        when (outcome) {
            is HistoryExportOutcome.Ready -> startShare(outcome.uri)
            HistoryExportOutcome.Unavailable -> {
                binding.historyNote.setText(R.string.network_monitor_history_export_failed)
                binding.historyNote.isVisible = true
            }
        }
    }

    /** The chooser is part of the system and always resolves, so a not-found guard could never run. */
    private fun startShare(uri: Uri) {
        binding.historyNote.isVisible = false
        val share = Intent(Intent.ACTION_SEND).apply {
            type = EXPORT_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(share, getString(R.string.network_monitor_history_export))
        )
    }

    private companion object {

        /** The export is one tab-separated text file with a header line. */
        const val EXPORT_MIME_TYPE = "text/plain"
    }
}
